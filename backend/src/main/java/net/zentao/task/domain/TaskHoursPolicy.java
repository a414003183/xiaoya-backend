package net.zentao.task.domain;

/**
 * 任务工时三件套联动规则（task 卡 §2/§4，纯函数）：
 * consumedHours 只增（动作/工时回写）；leftHours 由 start 初始化、由 effort/activate 覆写；
 * 父任务三件套恒等于子任务合计——cancel 子排除，leftHours 另排除 closed 子。
 */
public final class TaskHoursPolicy {

  private TaskHoursPolicy() {}

  /** start 的 leftHours 缺省值 = estimateHours − consumedHours（§4）。 */
  public static java.math.BigDecimal initialLeftHours(java.math.BigDecimal estimateHours,
      java.math.BigDecimal consumedHours) {
    java.math.BigDecimal estimate = estimateHours == null ? java.math.BigDecimal.ZERO : estimateHours;
    java.math.BigDecimal consumed = consumedHours == null ? java.math.BigDecimal.ZERO : consumedHours;
    java.math.BigDecimal left = estimate.subtract(consumed);
    return left.signum() < 0 ? java.math.BigDecimal.ZERO : left;
  }

  /** 编辑/删除工时后的 consumedHours 回算 = 未删流水合计（§4；状态不回退）。 */
  public static java.math.BigDecimal recalculatedConsumed(java.util.List<Effort> efforts) {
    java.math.BigDecimal total = java.math.BigDecimal.ZERO;
    for (Effort effort : efforts) {
      total = total.add(effort.consumedHours() == null ? java.math.BigDecimal.ZERO : effort.consumedHours());
    }
    return total;
  }

  /** 编辑/删除工时后的 leftHours 回算 = 最近一条带覆写的流水值；无则保持原值（返回空表示不改）。 */
  public static java.util.Optional<java.math.BigDecimal> recalculatedLeft(java.util.List<Effort> efforts) {
    return efforts.stream()
        .filter(effort -> effort.leftHours() != null)
        .reduce((first, second) -> second)
        .map(Effort::leftHours);
  }

  /** 父任务三件套 = 子任务合计（cancel 子排除；left 另排除 closed 子）。 */
  public static Totals rollUp(java.util.List<Task> children) {
    java.math.BigDecimal estimate = java.math.BigDecimal.ZERO;
    java.math.BigDecimal consumed = java.math.BigDecimal.ZERO;
    java.math.BigDecimal left = java.math.BigDecimal.ZERO;
    for (Task child : children) {
      if ("cancel".equals(child.status())) {
        continue;
      }
      estimate = estimate.add(child.estimateHours() == null ? java.math.BigDecimal.ZERO : child.estimateHours());
      consumed = consumed.add(child.consumedHours());
      if (!"closed".equals(child.status())) {
        left = left.add(child.leftHours() == null ? java.math.BigDecimal.ZERO : child.leftHours());
      }
    }
    return new Totals(estimate, consumed, left);
  }

  public record Totals(java.math.BigDecimal estimateHours, java.math.BigDecimal consumedHours,
      java.math.BigDecimal leftHours) {}
}
