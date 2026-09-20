package net.zentao.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import net.zentao.product.domain.Plan;
import net.zentao.product.domain.PlanStatusRollup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T-8 父计划聚合纯函数（product 卡 §4.3 四规则）。 */
class PlanStatusRollupTest {

  private static Plan plan(String status) {
    return new Plan(1, 1, 0, 0, "计划", status, null, null, null, null, null, null, null, "admin",
        Instant.now(), null, null, 0);
  }

  @Test
  @DisplayName("子计划全删（无子）→ 父回 wait")
  void emptyChildren() {
    PlanStatusRollup.Rollup rollup = PlanStatusRollup.compute(List.of()).orElseThrow();
    assertEquals("wait", rollup.status());
    assertTrue(PlanStatusRollup.changes("done", rollup));
    assertFalse(PlanStatusRollup.changes("wait", rollup));
  }

  @Test
  @DisplayName("子集全 closed → 父 closed（closedbychild）")
  void allClosed() {
    PlanStatusRollup.Rollup rollup = PlanStatusRollup.compute(List.of(plan("closed"), plan("closed"))).orElseThrow();
    assertEquals("closed", rollup.status());
    assertEquals("closedbychild", rollup.action());
  }

  @Test
  @DisplayName("子无 wait/doing 且非全 closed → 父 done（finishedbychild）")
  void finishedByChild() {
    PlanStatusRollup.Rollup rollup = PlanStatusRollup.compute(List.of(plan("done"), plan("closed"))).orElseThrow();
    assertEquals("done", rollup.status());
    assertEquals("finishedbychild", rollup.action());
  }

  @Test
  @DisplayName("子含 doing → 父 doing（activatedbychild）；doing 优先于 wait 兜底")
  void activatedByChild() {
    PlanStatusRollup.Rollup rollup = PlanStatusRollup.compute(List.of(plan("wait"), plan("doing"))).orElseThrow();
    assertEquals("doing", rollup.status());
    assertEquals("activatedbychild", rollup.action());
  }

  @Test
  @DisplayName("子仅 wait → 不变更（无匹配规则）")
  void waitingOnly() {
    assertTrue(PlanStatusRollup.compute(List.of(plan("wait"))).isEmpty());
  }
}
