package net.zentao.platform.langimport;

import java.time.Instant;

/**
 * LangImportView（contract：id/lang/fileName/totalRows/appliedRows/failedRows/status/createdBy/createdAt/message/lockVersion）。
 * {@code message} = 校验失败摘要（成功为 null）：失败也留痕，故它是记录的一部分而非错误响应的替代。
 */
public record LangImportView(
    long id,
    String lang,
    String fileName,
    int totalRows,
    int appliedRows,
    int failedRows,
    LangImportStatus status,
    String createdBy,
    Instant createdAt,
    String message,
    Integer lockVersion) {

  public static LangImportView of(LangImportPO po) {
    return new LangImportView(
        po.getId() == null ? 0 : po.getId(),
        po.getLang(),
        po.getFileName(),
        po.getTotalRows() == null ? 0 : po.getTotalRows(),
        po.getAppliedRows() == null ? 0 : po.getAppliedRows(),
        po.getFailedRows() == null ? 0 : po.getFailedRows(),
        LangImportStatus.valueOf(po.getStatus()),
        po.getCreatedBy(),
        po.getCreatedAt(),
        po.getMessage(),
        po.getLockVersion());
  }
}
