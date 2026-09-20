package net.zentao.quality.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import net.zentao.quality.domain.Report;

/** 测试报告视图（quality 卡 §3.6 全字段；字段集合与 contract ReportView 一一对应）。 */
public record ReportView(
    long id,
    long executionId,
    long projectId,
    long productId,
    String title,
    List<Long> testRunIds,
    LocalDate beginDate,
    LocalDate endDate,
    String owner,
    String content,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    int lockVersion) {

  public static ReportView of(Report report) {
    return new ReportView(
        report.id(),
        report.executionId(),
        report.projectId(),
        report.productId(),
        report.title(),
        report.testRunIds(),
        report.beginDate(),
        report.endDate(),
        report.owner(),
        report.content(),
        report.createdBy(),
        report.createdAt(),
        report.updatedBy(),
        report.updatedAt(),
        report.lockVersion());
  }
}
