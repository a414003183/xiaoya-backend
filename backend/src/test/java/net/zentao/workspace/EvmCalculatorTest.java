package net.zentao.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import net.zentao.task.api.TaskApi;
import net.zentao.workspace.domain.EvmCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** EVM 纯函数口径（workspace 卡 §3.2）：pv 折算边界、ev 进度、sv/cv 公式与两位小数。 */
class EvmCalculatorTest {

  private static final LocalDate WEEK_START = LocalDate.of(2026, 9, 14);
  private static final LocalDate WEEK_END = LocalDate.of(2026, 9, 20);

  @Test
  @DisplayName("pv：endDate 不晚于周日计全部；跨周末按本周工作日占比折算；周日之后不计")
  void pvBoundaries() {
    TaskApi.EvmTask withinWeek = task("doing", 10, 0, 0, "2026-09-01", "2026-09-18");
    TaskApi.EvmTask crossing = task("doing", 10, 0, 0, "2026-09-14", "2026-09-25");
    TaskApi.EvmTask afterWeek = task("doing", 10, 0, 0, "2026-09-21", "2026-09-30");

    assertEquals(new BigDecimal("10.00"), EvmCalculator.pv(List.of(withinWeek), WEEK_START, WEEK_END),
        "本周内完成的任务计全部预估");
    // 本周工作日 5（9/14–9/18），全程 9/14–9/25 工作日 10 → 10 × 5/10 = 5
    assertEquals(new BigDecimal("5.00"), EvmCalculator.pv(List.of(crossing), WEEK_START, WEEK_END),
        "跨周末按工作日占比折算");
    assertEquals(new BigDecimal("0.00"), EvmCalculator.pv(List.of(afterWeek), WEEK_START, WEEK_END),
        "下周才开始的排期不计入本周 pv");
    assertEquals(new BigDecimal("15.00"), EvmCalculator.pv(List.of(withinWeek, crossing), WEEK_START, WEEK_END),
        "多任务求和");
  }

  @Test
  @DisplayName("pv：status=cancel 的任务整体排除")
  void pvExcludesCanceled() {
    assertEquals(new BigDecimal("0.00"),
        EvmCalculator.pv(List.of(task("cancel", 8, 0, 0, "2026-09-01", "2026-09-18")), WEEK_START, WEEK_END));
  }

  @Test
  @DisplayName("ev：done 计全部；进行中按 consumed/(consumed+left) 进度折算；分母 0 进度为 0")
  void evProgress() {
    TaskApi.EvmTask done = task("done", 10, 3, 0, "2026-09-01", "2026-09-18");
    TaskApi.EvmTask half = task("doing", 10, 2, 2, "2026-09-01", "2026-09-30");
    TaskApi.EvmTask untouched = task("wait", 10, 0, 0, "2026-09-01", "2026-09-30");

    assertEquals(new BigDecimal("10.00"), EvmCalculator.ev(List.of(done)), "done 计全部预估");
    assertEquals(new BigDecimal("5.00"), EvmCalculator.ev(List.of(half)), "2/(2+2)=50% → 5");
    assertEquals(new BigDecimal("0.00"), EvmCalculator.ev(List.of(untouched)), "未开始进度 0");
    assertEquals(new BigDecimal("15.00"), EvmCalculator.ev(List.of(done, half)), "多任务求和");
  }

  @Test
  @DisplayName("sv/cv：−(1−ev/base)×100 两位小数；base=0 时 0")
  void variance() {
    assertEquals(new BigDecimal("-20.00"), EvmCalculator.variance(new BigDecimal("8"), new BigDecimal("10")),
        "ev/pv=0.8 → −(1−0.8)×100 = −20%");
    assertEquals(new BigDecimal("0.00"), EvmCalculator.variance(new BigDecimal("8"), BigDecimal.ZERO),
        "分母 0 → 0");
    assertEquals(new BigDecimal("-100.00"), EvmCalculator.variance(BigDecimal.ZERO, new BigDecimal("10")),
        "ev=0 → −(1−0)×100 = −100%（无进展）");
  }

  @Test
  @DisplayName("工作日口径：周一至周五，跨周末区间计数正确")
  void workdays() {
    assertEquals(5, EvmCalculator.workdays(WEEK_START, WEEK_END), "整周工作日 5 天");
    assertEquals(2, EvmCalculator.workdays(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 22)),
        "跨周末 9/19–9/22 只计 9/21、9/22");
    assertEquals(0, EvmCalculator.workdays(WEEK_END, WEEK_START), "倒序区间 0");
  }

  private static TaskApi.EvmTask task(String status, long estimate, long consumed, long left, String begin,
      String end) {
    return new TaskApi.EvmTask(1, "devel", status, BigDecimal.valueOf(estimate), BigDecimal.valueOf(consumed),
        BigDecimal.valueOf(left), LocalDate.parse(begin), LocalDate.parse(end));
  }
}
