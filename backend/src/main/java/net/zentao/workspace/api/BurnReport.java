package net.zentao.workspace.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 燃尽报表（contract：BurnReport；workspace 卡 §5：ideal 直线 + remaining 执行级日行）。 */
public record BurnReport(
    LocalDate beginDate,
    LocalDate endDate,
    List<LocalDate> dates,
    List<BigDecimal> ideal,
    List<BigDecimal> remaining) {}
