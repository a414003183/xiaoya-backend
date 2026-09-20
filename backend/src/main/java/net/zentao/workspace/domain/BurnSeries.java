package net.zentao.workspace.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 燃尽曲线纯函数（workspace 卡 §3.3/§5 BurnReport 节）：
 * ideal = 首日总 leftHours 到 endDate 归零的直线；remaining = 执行级日行 leftHours，缺失日沿用上值。
 */
public final class BurnSeries {

  private BurnSeries() {}

  /** 日期轴：beginDate..endDate 逐日（含两端）。 */
  public static List<LocalDate> dates(LocalDate beginDate, LocalDate endDate) {
    List<LocalDate> dates = new ArrayList<>();
    if (beginDate == null || endDate == null || endDate.isBefore(beginDate)) {
      return dates;
    }
    for (LocalDate date = beginDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      dates.add(date);
    }
    return dates;
  }

  /** ideal 直线：首日 total，末日 0（单日轴退化为一点）。 */
  public static List<BigDecimal> ideal(BigDecimal startLeft, int size) {
    BigDecimal total = startLeft == null ? BigDecimal.ZERO : startLeft;
    if (size <= 1) {
      return List.of(scale(total));
    }
    List<BigDecimal> series = new ArrayList<>(size);
    BigDecimal span = BigDecimal.valueOf(size - 1);
    for (int index = 0; index < size; index++) {
      BigDecimal remainingRatio = BigDecimal.valueOf(size - 1 - index).divide(span, 6, RoundingMode.HALF_UP);
      series.add(scale(total.multiply(remainingRatio)));
    }
    return series;
  }

  /**
   * remaining 序列：按日期取执行级日行 leftHours，缺失日沿用上值；首个快照之前沿用 fallback（首日基线）。
   */
  public static List<BigDecimal> remaining(List<LocalDate> dates, Map<LocalDate, BigDecimal> dailyLeft,
      BigDecimal fallback) {
    List<BigDecimal> series = new ArrayList<>(dates.size());
    BigDecimal last = fallback == null ? BigDecimal.ZERO : fallback;
    for (LocalDate date : dates) {
      BigDecimal value = dailyLeft.get(date);
      if (value != null) {
        last = value;
      }
      series.add(scale(last));
    }
    return series;
  }

  private static BigDecimal scale(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
