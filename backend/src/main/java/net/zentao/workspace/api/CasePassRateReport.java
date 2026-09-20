package net.zentao.workspace.api;

import java.math.BigDecimal;

/** 用例通过率（contract：CasePassRateReport；passRate 分母 total−na 为 0 时 null）。 */
public record CasePassRateReport(
    long total,
    long passed,
    long failed,
    long blocked,
    long na,
    BigDecimal passRate) {}
