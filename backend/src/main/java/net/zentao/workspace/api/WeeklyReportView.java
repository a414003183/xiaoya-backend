package net.zentao.workspace.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.task.api.TaskSummaryView;
import net.zentao.workspace.domain.WeeklyReport;

/**
 * 周报视图（contract：WeeklyReportView；workspace 卡 §3.2）：
 * weekSN/weekEnd/analysis 与三张任务表为读取时现算，不进库。
 */
public record WeeklyReportView(
    long id,
    long projectId,
    LocalDate weekStart,
    int weekSN,
    LocalDate weekEnd,
    BigDecimal pv,
    BigDecimal ev,
    BigDecimal ac,
    BigDecimal sv,
    BigDecimal cv,
    int staff,
    Map<String, BigDecimal> workload,
    String analysis,
    List<TaskSummaryView> finished,
    List<TaskSummaryView> postponed,
    List<TaskSummaryView> nextWeek,
    Instant updatedAt) {

  /** 偏差阈值（卡未定配置键，内建常量 ±10，登记 STATE 待裁决）。 */
  private static final BigDecimal THRESHOLD = BigDecimal.valueOf(10);

  public static WeeklyReportView of(WeeklyReport report, int weekSN, LocalDate weekEnd,
      List<TaskSummaryView> finished, List<TaskSummaryView> postponed, List<TaskSummaryView> nextWeek,
      MessageResolver messages) {
    return new WeeklyReportView(report.id(), report.projectId(), report.weekStart(), weekSN, weekEnd, report.pv(),
        report.ev(), report.ac(), report.sv(), report.cv(), report.staff(), report.workload(),
        analysis(report.sv(), report.cv(), messages), finished, postponed, nextWeek, report.updatedAt());
  }

  /**
   * analysis：按 sv/cv 阈值现算纯文本（\n 分隔，前端禁 HTML 注入）。
   * 文案走语言包键（T23）：落进响应正文的句子也随请求语言，同错误信封口径。
   */
  static String analysis(BigDecimal sv, BigDecimal cv, MessageResolver messages) {
    StringBuilder text = new StringBuilder();
    text.append(conclusion("weeklyReport.conclusion.schedule", sv, messages));
    text.append("\n").append(conclusion("weeklyReport.conclusion.cost", cv, messages));
    return text.toString();
  }

  private static String conclusion(String labelKey, BigDecimal variance, MessageResolver messages) {
    BigDecimal value = variance == null ? BigDecimal.ZERO : variance;
    String label = messages.forRequest(labelKey, null);
    String percent = value.abs().setScale(2, RoundingMode.HALF_UP).toString();
    if (value.abs().compareTo(THRESHOLD) <= 0) {
      return messages.forRequest("weeklyReport.conclusion.acceptable", new Object[] {label, percent});
    }
    return messages.forRequest(
        value.signum() < 0 ? "weeklyReport.conclusion.behind" : "weeklyReport.conclusion.ahead",
        new Object[] {label, percent});
  }
}
