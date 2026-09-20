package net.zentao.workspace.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
      List<TaskSummaryView> finished, List<TaskSummaryView> postponed, List<TaskSummaryView> nextWeek) {
    return new WeeklyReportView(report.id(), report.projectId(), report.weekStart(), weekSN, weekEnd, report.pv(),
        report.ev(), report.ac(), report.sv(), report.cv(), report.staff(), report.workload(),
        analysis(report.sv(), report.cv()), finished, postponed, nextWeek, report.updatedAt());
  }

  /** analysis：按 sv/cv 阈值现算纯文本（\n 分隔，前端禁 HTML 注入）。 */
  static String analysis(BigDecimal sv, BigDecimal cv) {
    StringBuilder text = new StringBuilder();
    text.append(conclusion("进度", sv));
    text.append("\n").append(conclusion("成本", cv));
    return text.toString();
  }

  private static String conclusion(String label, BigDecimal variance) {
    BigDecimal value = variance == null ? BigDecimal.ZERO : variance;
    int compare = value.abs().compareTo(THRESHOLD);
    if (compare <= 0) {
      return label + "偏差在可接受范围内（" + value.setScale(2, RoundingMode.HALF_UP) + "%）。";
    }
    return value.signum() < 0
        ? label + "落后计划 " + value.abs().setScale(2, RoundingMode.HALF_UP) + "%，建议关注。"
        : label + "超前计划 " + value.setScale(2, RoundingMode.HALF_UP) + "%。";
  }
}
