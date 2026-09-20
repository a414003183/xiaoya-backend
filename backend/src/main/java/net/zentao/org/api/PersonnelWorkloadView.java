package net.zentao.org.api;

import java.math.BigDecimal;

/** 工作量行（contract：PersonnelWorkloadView；org 卡 §5 Personnel 节）。 */
public record PersonnelWorkloadView(
    String account,
    String realName,
    Long departmentId,
    BigDecimal consumedHours,
    long finishedTaskCount) {}
