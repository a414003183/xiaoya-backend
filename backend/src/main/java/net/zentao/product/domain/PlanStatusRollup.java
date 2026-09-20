package net.zentao.product.domain;

import java.util.List;
import java.util.Optional;

/**
 * 父计划状态聚合（product 卡 §4.3，纯函数）：
 * 子集全 closed → 父 closed（closedbychild）；子无 wait/doing 且非全 closed → 父 done（finishedbychild）；
 * 子含 doing → 父 doing（activatedbychild）；子计划全删（无子）→ 父回 wait 并脱离父子。
 */
public final class PlanStatusRollup {

  private PlanStatusRollup() {}

  /** 聚合结论：status=目标状态；action=动态流动作名（子驱动），无子时为空。 */
  public record Rollup(String status, String action) {}

  /** @param children 未删除的直接子计划 */
  public static Optional<Rollup> compute(List<Plan> children) {
    if (children.isEmpty()) {
      return Optional.of(new Rollup("wait", null));
    }
    boolean allClosed = children.stream().allMatch(child -> "closed".equals(child.status()));
    if (allClosed) {
      return Optional.of(new Rollup("closed", "closedbychild"));
    }
    boolean anyActive = children.stream()
        .anyMatch(child -> "wait".equals(child.status()) || "doing".equals(child.status()));
    if (!anyActive) {
      return Optional.of(new Rollup("done", "finishedbychild"));
    }
    boolean anyDoing = children.stream().anyMatch(child -> "doing".equals(child.status()));
    if (anyDoing) {
      return Optional.of(new Rollup("doing", "activatedbychild"));
    }
    return Optional.empty();
  }

  /** 目标状态是否真的需要落库（避免重复写与重复动态流）。 */
  public static boolean changes(String currentStatus, Rollup rollup) {
    return !currentStatus.equals(rollup.status());
  }
}
