package net.zentao.platform.langimport;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.meta.LangItemRepository;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 语言包 Excel 导入（platform 卡 §3.12）：先整份校验、全通过才落覆盖层（一处不合法即整体拒绝）。
 *
 * <p>失败也留痕：校验不通过时写一条 {@code status=failed} 的 lang_import 记录（message = 失败摘要），
 * 且**不抛异常**地返回结果——抛异常会连这条记录一起回滚；42201 由控制器在事务之外抛出。
 *
 * <p>行级失败原因用 ASCII 原因码（{@code fields} 的 {@code row:<n>} → 码），界面按码映射 i18n
 * （同 03 §2「fields 存码、界面按 code 映射文案」的口径，避免 en 界面冒中文）。
 */
@Component
public class LangImportService {

  /** 单次上传的数据行上限（超出即整体拒绝；导出全量 1674 行 × 2 语言仍在其内）。 */
  public static final int MAX_ROWS = 5000;
  /** 单元格文案上限（与 notification.content 同量级；语言包正常值远小于此）。 */
  public static final int MAX_VALUE_LENGTH = 2000;
  /** 响应里回报的行级错误条数上限（其余计入 failedRows 计数）。 */
  public static final int MAX_REPORTED_ROWS = 20;

  /**
   * 键字符集：段字符 + `.` 分隔 + `-`/`_`；`/` 也在内——目录里真实存在一个带斜杠的键
   * （{@code testCase.result.n/a}），不许它等于砍掉导出→导入的往返（platform 卡 §3.12）。
   */
  private static final Pattern KEY_CHARSET = Pattern.compile("[A-Za-z0-9_./-]+");
  private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");
  private static final int MAX_LANG_LENGTH = 30;
  private static final int MAX_FILE_NAME_LENGTH = 255;
  private static final int MAX_MESSAGE_LENGTH = 2000;

  private final LangCatalog catalog;
  private final LangImportRepository logRepository;
  private final LangItemRepository langItems;
  private final LangOverrideQueryService overrides;

  public LangImportService(
      LangCatalog catalog,
      LangImportRepository logRepository,
      LangItemRepository langItems,
      LangOverrideQueryService overrides) {
    this.catalog = catalog;
    this.logRepository = logRepository;
    this.langItems = langItems;
    this.overrides = overrides;
  }

  /** 导入结果：{@code errors} 非空即整体失败（fields 形状：`row:<n>`/`file` → 原因码）。 */
  public record ImportResult(LangImportView log, Map<String, String> errors) {
    public boolean failed() {
      return !errors.isEmpty();
    }
  }

  /**
   * 校验 + 落库（单事务；校验期间不写覆盖层）。
   *
   * @param lang 表单声明的上传语言码（必须 ∈ 目录语言；记录进日志）
   * @param content 上传文件字节（.xlsx/.xls）
   */
  @Transactional
  public ImportResult importFile(SessionPrincipal principal, String lang, String fileName, byte[] content) {
    String normalizedLang = catalog.normalizeLang(lang);
    String logLang = truncate(normalizedLang, MAX_LANG_LENGTH);
    String logFileName = truncate(fileName == null || fileName.isBlank() ? "unnamed" : fileName.trim(),
        MAX_FILE_NAME_LENGTH);

    // ── 文件级校验 ──
    if (!catalog.isKnownLang(normalizedLang)) {
      return fail(principal, logLang, logFileName, 0, "file", "invalid-lang");
    }
    LangXlsx.LangSheet sheet;
    try {
      sheet = LangXlsx.read(new ByteArrayInputStream(content), catalog.languages());
    } catch (RuntimeException e) {
      return fail(principal, logLang, logFileName, 0, "file", "unreadable");
    }
    if (!sheet.hasKeyColumn()) {
      return fail(principal, logLang, logFileName, sheet.rows().size(), "file", "missing-key-column");
    }
    if (sheet.languages().isEmpty()) {
      return fail(principal, logLang, logFileName, sheet.rows().size(), "file", "missing-lang-column");
    }
    if (sheet.rows().isEmpty()) {
      return fail(principal, logLang, logFileName, 0, "file", "empty-file");
    }
    if (sheet.rows().size() > MAX_ROWS) {
      return fail(principal, logLang, logFileName, sheet.rows().size(), "file", "too-many-rows");
    }

    // ── 行级校验（全部收集，报告上限 MAX_REPORTED_ROWS 条） ──
    List<LangXlsx.LangRow> accepted = new ArrayList<>();
    Map<String, String> errors = new LinkedHashMap<>();
    Set<String> seen = new HashSet<>();
    int failedRows = 0;
    for (LangXlsx.LangRow row : sheet.rows()) {
      String reason = reject(row, seen);
      if (reason != null) {
        failedRows++;
        if (errors.size() < MAX_REPORTED_ROWS) {
          errors.put("row:" + row.rowNumber(), reason);
        }
        continue;
      }
      accepted.add(row);
    }
    if (!errors.isEmpty()) {
      String summary = "格式校验失败：共 " + sheet.rows().size() + " 行，" + failedRows + " 行未通过（原因码见 fields）。";
      LangImportView log = LangImportView.of(logRepository.append(po(principal, logLang, logFileName,
          sheet.rows().size(), 0, failedRows, LangImportStatus.failed, summary)));
      return new ImportResult(log, errors);
    }

    // ── 落覆盖层（同 (lang, domain, section) 分组批量 upsert，复用 lang_item 的既有写路径） ──
    apply(accepted);
    LangImportView log = LangImportView.of(logRepository.append(po(principal, logLang, logFileName, accepted.size(),
        accepted.size(), 0, LangImportStatus.success, null)));
    return new ImportResult(log, Map.of());
  }

  /** 每条被接受的行按其非空单元格写入覆盖层；空单元格 = 不改该语言。 */
  private void apply(List<LangXlsx.LangRow> rows) {
    Map<String, Map<LangCatalog.KeyParts, Map<String, String>>> grouped = new LinkedHashMap<>();
    for (LangXlsx.LangRow row : rows) {
      LangCatalog.KeyParts parts = LangCatalog.parts(row.key());
      for (Map.Entry<String, String> cell : row.values().entrySet()) {
        if (cell.getValue() == null || cell.getValue().isEmpty()) {
          continue;
        }
        grouped.computeIfAbsent(cell.getKey(), ignored -> new LinkedHashMap<>())
            .computeIfAbsent(parts, ignored -> new LinkedHashMap<>())
            .put(parts.itemKey(), cell.getValue());
      }
    }
    for (Map.Entry<String, Map<LangCatalog.KeyParts, Map<String, String>>> byLang : grouped.entrySet()) {
      for (Map.Entry<LangCatalog.KeyParts, Map<String, String>> group : byLang.getValue().entrySet()) {
        langItems.upsert(byLang.getKey(), group.getKey().domain(), group.getKey().section(), group.getValue());
      }
    }
  }

  /** 单行校验：返回 null = 通过；否则返回 ASCII 原因码（首个命中即止，报错不刷屏）。 */
  private String reject(LangXlsx.LangRow row, Set<String> seen) {
    String key = row.key().trim();
    if (key.isEmpty()) {
      return "empty-key";
    }
    if (!KEY_CHARSET.matcher(key).matches()) {
      return "invalid-key";
    }
    if (!catalog.keys().contains(key)) {
      return "unknown-key";
    }
    if (!seen.add(key)) {
      return "duplicate-key";
    }
    boolean allEmpty = true;
    for (Map.Entry<String, String> cell : row.values().entrySet()) {
      String value = cell.getValue() == null ? "" : cell.getValue();
      if (value.isEmpty()) {
        continue;
      }
      allEmpty = false;
      if (value.length() > MAX_VALUE_LENGTH) {
        return "value-too-long";
      }
      if (LangCatalog.LANG_EN.equals(cell.getKey()) && CJK.matcher(value).find()) {
        return "cjk-in-en";
      }
    }
    return allEmpty ? "empty-row" : null;
  }

  private ImportResult fail(SessionPrincipal principal, String lang, String fileName, int totalRows, String field,
      String reason) {
    String summary = "文件校验失败：" + reason + "。";
    LangImportView log = LangImportView.of(logRepository.append(po(principal, lang, fileName, totalRows, 0, 0,
        LangImportStatus.failed, summary)));
    return new ImportResult(log, Map.of(field, reason));
  }

  private LangImportPO po(SessionPrincipal principal, String lang, String fileName, int totalRows, int appliedRows,
      int failedRows, LangImportStatus status, String message) {
    LangImportPO po = new LangImportPO();
    po.setLang(lang);
    po.setFileName(fileName);
    po.setTotalRows(totalRows);
    po.setAppliedRows(appliedRows);
    po.setFailedRows(failedRows);
    po.setStatus(status.name());
    po.setMessage(truncate(message, MAX_MESSAGE_LENGTH));
    po.setCreatedBy(principal.account());
    po.setUpdatedBy(principal.account());
    return po;
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  /** 导出（platform 卡 §3.12）：全量目录键 × 语言，值 = 覆盖层优先、内建默认为底。 */
  public byte[] export() {
    Map<String, Map<String, String>> overridesByLang = new LinkedHashMap<>();
    for (String lang : catalog.languages()) {
      overridesByLang.put(lang, overrides.byKey(lang));
    }
    List<LangXlsx.ExportRow> rows = new ArrayList<>();
    for (String key : catalog.sortedKeys()) {
      Map<String, String> values = new LinkedHashMap<>();
      for (String lang : catalog.languages()) {
        values.put(lang, overridesByLang.get(lang).getOrDefault(key, catalog.defaults(lang).getOrDefault(key, "")));
      }
      rows.add(new LangXlsx.ExportRow(key, values));
    }
    try {
      return LangXlsx.write(catalog.languages(), rows);
    } catch (java.io.IOException e) {
      throw ApiException.internal("语言包导出失败。");
    }
  }
}
