package net.zentao.workspace.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import net.zentao.task.api.TaskApi;

/**
 * EVM 纯函数（workspace 卡 §3.2 EVM 节为唯一真源）：
 * pv 按任务 endDate 与周的相对位置折算、ev = Σ estimateHours × 进度、sv/cv 偏差百分数。
 * 工作日口径暂定周一至周五（前置-3：org 域节假日未定义前不变）。
 */
public final class EvmCalculator {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private EvmCalculator() {}

  /** PV：任务 endDate ≤ weekEnd → 计全部；跨周末 → 按工作日占比折算；其余不计。 */
  public static BigDecimal pv(List<TaskApi.EvmTask> tasks, LocalDate weekStart, LocalDate weekEnd) {
    BigDecimal total = BigDecimal.ZERO;
    for (TaskApi.EvmTask task : tasks) {
      if (isCanceled(task) || task.estimateHours() == null) {
        continue;
      }
      if (task.endDate() != null && !task.endDate().isAfter(weekEnd)) {
        total = total.add(task.estimateHours());
        continue;
      }
      if (task.beginDate() == null || task.endDate() == null || task.beginDate().isAfter(weekEnd)) {
        continue;
      }
      BigDecimal span = BigDecimal.valueOf(workdays(task.beginDate(), task.endDate()));
      if (span.signum() == 0) {
        continue;
      }
      LocalDate from = task.beginDate().isBefore(weekStart) ? weekStart : task.beginDate();
      BigDecimal share = BigDecimal.valueOf(workdays(from, weekEnd)).divide(span, 6, RoundingMode.HALF_UP);
      total = total.add(task.estimateHours().multiply(share));
    }
    return scale(total);
  }

  /** EV：status=done 计全部 estimateHours；否则按 consumed/(consumed+left) 进度折算（分母 0 → 进度 0）。 */
  public static BigDecimal ev(List<TaskApi.EvmTask> tasks) {
    BigDecimal total = BigDecimal.ZERO;
    for (TaskApi.EvmTask task : tasks) {
      if (isCanceled(task) || task.estimateHours() == null) {
        continue;
      }
      if ("done".equals(task.status())) {
        total = total.add(task.estimateHours());
        continue;
      }
      BigDecimal progress = progress(task.consumedHours(), task.leftHours());
      total = total.add(task.estimateHours().multiply(progress).divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }
    return scale(total);
  }

  /** 任务进度百分数：consumed/(consumed+left)×100，分母 0 → 0。 */
  public static BigDecimal progress(BigDecimal consumedHours, BigDecimal leftHours) {
    BigDecimal consumed = consumedHours == null ? BigDecimal.ZERO : consumedHours;
    BigDecimal left = leftHours == null ? BigDecimal.ZERO : leftHours;
    BigDecimal denominator = consumed.add(left);
    if (denominator.signum() == 0) {
      return BigDecimal.ZERO;
    }
    return consumed.multiply(HUNDRED).divide(denominator, 2, RoundingMode.HALF_UP);
  }

  /** 偏差百分数 = −(1 − value/base)×100；base=0 → 0，两位小数。 */
  public static BigDecimal variance(BigDecimal value, BigDecimal base) {
    BigDecimal safeValue = value == null ? BigDecimal.ZERO : value;
    BigDecimal safeBase = base == null ? BigDecimal.ZERO : base;
    if (safeBase.signum() == 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    return BigDecimal.ONE.subtract(safeValue.divide(safeBase, 6, RoundingMode.HALF_UP))
        .multiply(HUNDRED).negate().setScale(2, RoundingMode.HALF_UP);
  }

  /** 闭区间工作日数（周一至周五，含两端）。 */
  public static int workdays(LocalDate from, LocalDate to) {
    if (from == null || to == null || to.isBefore(from)) {
      return 0;
    }
    int count = 0;
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      if (isWorkday(date)) {
        count++;
      }
    }
    return count;
  }

  public static boolean isWorkday(LocalDate date) {
    DayOfWeek day = date.getDayOfWeek();
    return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
  }

  /** 任务是否落在统计范围外（status=cancel）。 */
  private static boolean isCanceled(TaskApi.EvmTask task) {
    return "cancel".equals(task.status());
  }

  private static BigDecimal scale(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
