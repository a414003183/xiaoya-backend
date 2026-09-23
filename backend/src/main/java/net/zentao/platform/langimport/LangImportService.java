package net.zentao.platform.langimport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.i18n.LangCatalog;
import net.zentao.platform.i18n.LangOverrideQueryService;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * 语言包 Excel 导入（platform 卡 §3.12；T05 单文件全语言）：一份文件包揽 `key | zh-cn | en`（列头即语言，
 * 不再由表单声明），先整份校验、全通过才落覆盖层（一处不合法即整体拒绝）。
 *
 * <p>**解析不占事务**（T57 / BE-03）：本类只做解析与校验（POI、逐行规则），落库交给
 * {@link ApplyLangImportHandler} 的两个 {@code @Transactional} 入口——否则 5MB 文件的解析全程占着一条连接。
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
  /** 单次上传的字节上限（ADR-005 ②）；体量闸门在解析之前——不拿大文件喂 POI。 */
  public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
  /** 魔数长度（.xlsx = zip `PK\x03\x04`、.xls = OLE2 `D0 CF 11 E0`）：只读这么多，不做全量读。 */
  private static final int EXCEL_MAGIC_BYTES = 4;
  /** 单文件全语言的记录语言码：一个文件包揽所有语言，不再由表单声明（ADR-005 ①⑤）。 */
  public static final String ALL_LANGUAGES = "all";
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
  private static final int MAX_FILE_NAME_LENGTH = 255;

  private final LangCatalog catalog;
  private final ApplyLangImportHandler applyHandler;
  private final LangOverrideQueryService overrides;
  private final MessageResolver messages;

  public LangImportService(LangCatalog catalog, ApplyLangImportHandler applyHandler,
      LangOverrideQueryService overrides, MessageResolver messages) {
    this.catalog = catalog;
    this.applyHandler = applyHandler;
    this.overrides = overrides;
    this.messages = messages;
  }

  /** 导入结果：{@code errors} 非空即整体失败（fields 形状：`row:<n>`/`file` → 原因码）。 */
  public record ImportResult(LangImportView log, Map<String, String> errors) {
    public boolean failed() {
      return !errors.isEmpty();
    }
  }

  /**
   * 校验（无事务）+ 委托落库（{@link ApplyLangImportHandler}）。
   *
   * <p>闸门顺序（ADR-005 §2 + T60，顺序即防线，任一失败整体拒绝）：
   * ① 扩展名 → `invalid-extension`；② 空文件 → `empty-file`；③ **魔数**（只读头 4 字节，改名无关文件挡在解析前）→ `unreadable`；
   * ④ 体量 ≤ {@link #MAX_FILE_BYTES} → `too-large`（**在读取之前判**：超限的文件不进堆，T60/SEC-09）；
   * ⑤ **解压后总量** ≤ {@link LangXlsx#MAX_EXPANDED_BYTES}（zip 炸弹早停）→ `too-large`；
   * ⑥ POI 打不开 → `unreadable`；⑦ 表头必须精确等于 `key` + 目录语言列（顺序一致）→ `invalid-header`；
   * ⑧ 行数 0 → `empty-file`、超 {@link #MAX_ROWS} → `too-many-rows`；
   * ⑨ 行级：空键/非法键/目录外键/重复键/值超长/en 冒中文/**公式单元格**。
   *
   * @param fileName 上传的原始文件名（取扩展名、记日志）
   * @param size 上传文件的声称字节数（容器给的 part 大小；不信任声明值，读取另设上限）
   * @param content 上传文件流（.xlsx/.xls）
   */
  public ImportResult importFile(SessionPrincipal principal, String fileName, long size, InputStream content) {
    String logFileName = truncate(fileName == null || fileName.isBlank() ? "unnamed" : fileName.trim(),
        MAX_FILE_NAME_LENGTH);

    // ── 文件级校验（CONVENTIONS §7 的顺序：类型 → 魔数 → 大小 → 解压比 → 解析 → 表头结构 → 行数）──
    if (!hasExcelExtension(logFileName)) {
      return fail(principal, logFileName, 0, 0, "file", "invalid-extension");
    }
    if (content == null || size <= 0) {
      return fail(principal, logFileName, 0, 0, "file", "empty-file");
    }
    byte[] head;
    try {
      head = content.readNBytes(EXCEL_MAGIC_BYTES);
    } catch (IOException e) {
      return fail(principal, logFileName, 0, 0, "file", "unreadable");
    }
    if (!hasExcelMagic(head)) {
      return fail(principal, logFileName, 0, 0, "file", "unreadable");
    }
    if (size > MAX_FILE_BYTES) {
      // 体量闸门在**读进堆之前**：50MB 的容器上限下，超限文件连一次 readAllBytes 都不做（T60/SEC-09）
      return fail(principal, logFileName, 0, 0, "file", "too-large");
    }
    byte[] bytes;
    try {
      // 声明值不可全信：真读出来的长度再判一次（最多读到 MAX+1 即止）
      bytes = readBounded(head, content, MAX_FILE_BYTES + 1);
    } catch (IOException e) {
      return fail(principal, logFileName, 0, 0, "file", "unreadable");
    }
    if (bytes.length > MAX_FILE_BYTES) {
      return fail(principal, logFileName, 0, 0, "file", "too-large");
    }
    if (!LangXlsx.expansionWithinLimit(bytes)) {
      return fail(principal, logFileName, 0, 0, "file", "too-large");
    }
    LangXlsx.LangSheet sheet;
    try {
      sheet = LangXlsx.read(new ByteArrayInputStream(bytes), catalog.languages());
    } catch (RuntimeException e) {
      return fail(principal, logFileName, 0, 0, "file", "unreadable");
    }
    if (!expectedHeader().equals(sheet.header())) {
      return fail(principal, logFileName, sheet.rows().size(), 0, "file", "invalid-header");
    }
    if (sheet.rows().isEmpty()) {
      return fail(principal, logFileName, 0, 0, "file", "empty-file");
    }
    if (sheet.rows().size() > MAX_ROWS) {
      return fail(principal, logFileName, sheet.rows().size(), 0, "file", "too-many-rows");
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
      // 落库文案随**默认语言**（同审计原因口径，见 MessageResolver#plain）：一个上传的回执不该因谁上传而两样
      String summary = messages.plain("platform.langUpload.summary.invalidRows",
          new Object[] {sheet.rows().size(), failedRows});
      return new ImportResult(applyHandler.recordFailure(principal, logFileName, sheet.rows().size(), failedRows,
          summary), errors);
    }

    // ── 落覆盖层 + 记录（都在 ApplyLangImportHandler 单事务里） ──
    return new ImportResult(applyHandler.recordSuccess(principal, logFileName, accepted), Map.of());
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
    if (row.hasFormula()) {
      // ADR-005 ⑤：公式类型单元格一律不收（缓存值可能看着无害，=HYPERLINK/DDE 那套就是从这个口子进来的）
      return "formula-cell";
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
      if (!hasValidSurrogates(value)) {
        return "invalid-unicode";
      }
      if (LangCatalog.LANG_EN.equals(cell.getKey()) && CJK.matcher(value).find()) {
        return "cjk-in-en";
      }
    }
    // T21：整行留空不再是错误——它表示「清除该键的覆盖，恢复语言包默认」（在线编辑器删掉后，这是唯一的还原入口）
    return null;
  }

  /** 文件级失败：留痕走落库面（独立事务），异常码交给控制器在事务之外抛。 */
  private ImportResult fail(SessionPrincipal principal, String fileName, int totalRows, int failedRows, String field,
      String reason) {
    String summary = messages.plain("platform.langUpload.summary.fileInvalid", new Object[] {reason});
    return new ImportResult(
        applyHandler.recordFailure(principal, fileName, totalRows, failedRows, summary), Map.of(field, reason));
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  /** 模板表头（精确匹配用）：`key` + 目录语言列，顺序一致（ADR-005 ③）。 */
  private List<String> expectedHeader() {
    List<String> expected = new ArrayList<>();
    expected.add(LangXlsx.KEY_COLUMN);
    expected.addAll(catalog.languages());
    return expected;
  }

  private static boolean hasExcelExtension(String fileName) {
    String lower = fileName.toLowerCase(java.util.Locale.ROOT);
    return lower.endsWith(".xlsx") || lower.endsWith(".xls");
  }

  /**
   * 魔数（CONVENTIONS §7 的「魔数/类型」）：.xlsx = zip（`PK\x03\x04`）、.xls = OLE2（`D0 CF 11 E0`）。
   * 扩展名可以随便改，魔数不行——这一步挡掉「改了名的无关文件」，且**在解析与体积闸门之前**。
   */
  private static boolean hasExcelMagic(byte[] head) {
    if (head.length >= 4 && head[0] == 0x50 && head[1] == 0x4B) {
      return true;
    }
    return head.length >= 4 && (head[0] & 0xFF) == 0xD0 && (head[1] & 0xFF) == 0xCF
        && (head[2] & 0xFF) == 0x11 && (head[3] & 0xFF) == 0xE0;
  }

  /**
   * 读入整份内容（含已消耗的头部），**上限 {@code limit} 字节**（T60/SEC-09）：读到就停，
   * 不按声明值无限读——堆占用有上界，超限由调用方按 `too-large` 处理。
   */
  private static byte[] readBounded(byte[] head, InputStream content, int limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(head, 0, head.length);
    out.writeBytes(content.readNBytes(limit - head.length));
    return out.toByteArray();
  }

  /**
   * 值的 UTF-16 合法性（CONVENTIONS §7「拒绝非法 Unicode」）：孤立代理项会让下游 JSON/DB 出替换字符。
   * `.xlsx` 走 XML（XML 1.0 本就不允许孤立代理项，读不进来），这道闸门真正挡的是 **.xls（BIFF8）** 路径。
   * 合法的代理对（emoji 之类）不算非法。
   */
  private static boolean hasValidSurrogates(String value) {
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (Character.isHighSurrogate(ch)) {
        if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
          return false;
        }
        i++;
      } else if (Character.isLowSurrogate(ch)) {
        return false;
      }
    }
    return true;
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
      throw ApiException.keyed(ErrorCode.INTERNAL_ERROR, "platform.langUpload.exportFailed");
    }
  }
}
