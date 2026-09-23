package net.zentao.platform.langimport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.platform.i18n.LangCatalog;
import net.zentao.platform.meta.LangItemRepository;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 语言包导入的**落库面**（T57 / BE-03）：解析在校验层（{@link LangImportService}）完成，事务只包这一层。
 *
 * <p>为什么拆：POI 解析 5MB 上限的文件要几百毫秒到秒级，包在同一事务里等于解析期间一直占着一条连接
 * （Hikari 默认 10 条，并发上传即打满池）。拆开之后解析不碰数据库，落库是纯写 + 单事务。
 *
 * <p>两条入口都要事务：失败也要留痕（{@code lang_import} 记一条 {@code status=failed}）——
 * 与覆盖层写入一样是"要么都成"的一次写。成功路径的覆盖层 upsert 与记录 append 在同事务内原子提交。
 */
@Component
public class ApplyLangImportHandler {

  private static final int MAX_MESSAGE_LENGTH = 2000;

  private final LangImportRepository logRepository;
  private final LangItemRepository langItems;
  private final LangCatalog catalog;

  public ApplyLangImportHandler(LangImportRepository logRepository, LangItemRepository langItems,
      LangCatalog catalog) {
    this.logRepository = logRepository;
    this.langItems = langItems;
    this.catalog = catalog;
  }

  /** 校验失败留痕（独立事务）：{@code failedRows} > 0 记行级失败数，文件级失败传 0。 */
  @Transactional
  public LangImportView recordFailure(SessionPrincipal actor, String fileName, int totalRows, int failedRows,
      String summary) {
    return LangImportView.of(logRepository.append(po(actor, fileName, totalRows, 0, failedRows,
        LangImportStatus.failed, summary)));
  }

  /** 成功落库（独立事务）：覆盖层 upsert + 记录 append 一起提交。 */
  @Transactional
  public LangImportView recordSuccess(SessionPrincipal actor, String fileName, List<LangXlsx.LangRow> applied) {
    apply(applied);
    return LangImportView.of(logRepository.append(po(actor, fileName, applied.size(), applied.size(), 0,
        LangImportStatus.success, null)));
  }

  /** 每条被接受的行按其非空单元格写入覆盖层；空单元格 = 不改该语言；整行留空 = 清该键的覆盖。 */
  private void apply(List<LangXlsx.LangRow> rows) {
    Map<String, Map<LangCatalog.KeyParts, Map<String, String>>> grouped = new LinkedHashMap<>();
    List<LangCatalog.KeyParts> cleared = new ArrayList<>();
    for (LangXlsx.LangRow row : rows) {
      if (row.values().values().stream().allMatch(value -> value == null || value.isEmpty())) {
        cleared.add(LangCatalog.parts(row.key()));
        continue;
      }
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
    // 清理行对**所有语言**生效：整行留空表达的是「这个键不要自定义文案」，与语言无关
    for (LangCatalog.KeyParts parts : cleared) {
      for (String lang : catalog.languages()) {
        langItems.deleteItem(lang, parts.domain(), parts.section(), parts.itemKey());
      }
    }
  }

  private LangImportPO po(SessionPrincipal principal, String fileName, int totalRows, int appliedRows,
      int failedRows, LangImportStatus status, String message) {
    LangImportPO po = new LangImportPO();
    po.setLang(LangImportService.ALL_LANGUAGES);
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
}
