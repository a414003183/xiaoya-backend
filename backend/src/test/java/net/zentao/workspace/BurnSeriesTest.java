package net.zentao.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.workspace.domain.BurnSeries;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 燃尽曲线口径（workspace 卡 §3.3/§5）：ideal 直线、缺失日沿用上值、末值=当前剩余合计。 */
class BurnSeriesTest {

  @Test
  @DisplayName("日期轴：beginDate..endDate 逐日；倒序/空 → 空轴")
  void dates() {
    assertEquals(List.of(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16)),
        BurnSeries.dates(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16)));
    assertEquals(List.of(), BurnSeries.dates(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 14)));
  }

  @Test
  @DisplayName("ideal：首日总 leftHours 直线归零到末日；单日轴退化为一点")
  void ideal() {
    assertEquals(List.of(new BigDecimal("10.00"), new BigDecimal("7.50"), new BigDecimal("5.00"),
        new BigDecimal("2.50"), new BigDecimal("0.00")), BurnSeries.ideal(new BigDecimal("10"), 5));
    assertEquals(List.of(new BigDecimal("10.00")), BurnSeries.ideal(new BigDecimal("10"), 1));
    assertEquals(List.of(new BigDecimal("0.00"), new BigDecimal("0.00")),
        BurnSeries.ideal(BigDecimal.ZERO, 2), "起点为 0 时全 0");
  }

  @Test
  @DisplayName("remaining：按日取执行级日行，缺失日沿用上值，首行之前用基线兜底")
  void remainingCarriesForward() {
    List<LocalDate> dates = List.of(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16),
        LocalDate.of(2026, 9, 17));
    Map<LocalDate, BigDecimal> rows = new LinkedHashMap<>();
    rows.put(LocalDate.of(2026, 9, 14), new BigDecimal("10"));
    rows.put(LocalDate.of(2026, 9, 16), new BigDecimal("6"));

    assertEquals(List.of(new BigDecimal("10.00"), new BigDecimal("10.00"), new BigDecimal("6.00"),
        new BigDecimal("6.00")),
        BurnSeries.remaining(dates, rows, new BigDecimal("12")), "9/15、9/17 沿用上值");
    assertEquals(List.of(new BigDecimal("12.00"), new BigDecimal("7.00"), new BigDecimal("7.00"),
        new BigDecimal("7.00")),
        BurnSeries.remaining(dates, Map.of(LocalDate.of(2026, 9, 15), new BigDecimal("7")), new BigDecimal("12")),
        "首行之前用基线，之后沿用");
  }

  @Test
  @DisplayName("remaining 末值 = 最新执行级剩余合计（接口契约）")
  void lastValueIsLatestLeft() {
    List<LocalDate> dates = BurnSeries.dates(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18));
    List<BigDecimal> remaining = BurnSeries.remaining(dates,
        Map.of(LocalDate.of(2026, 9, 16), new BigDecimal("4.5")), new BigDecimal("9"));
    assertEquals(new BigDecimal("4.50"), remaining.getLast());
    assertEquals(dates.size(), remaining.size());
  }
}
