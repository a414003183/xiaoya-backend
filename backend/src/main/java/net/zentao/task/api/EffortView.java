package net.zentao.task.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import net.zentao.task.domain.Effort;

/** 工时视图（contract：EffortView；task 卡 §3b 读侧字段）。 */
public record EffortView(
    long id,
    long taskId,
    long executionId,
    long projectId,
    String account,
    LocalDate workDate,
    BigDecimal consumedHours,
    BigDecimal leftHours,
    String work,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt) {

  public static EffortView of(Effort effort) {
    return new EffortView(effort.id(), effort.taskId(), effort.executionId(), effort.projectId(), effort.account(),
        effort.workDate(), effort.consumedHours(), effort.leftHours(), effort.work(), effort.createdBy(),
        effort.createdAt(), effort.updatedBy(), effort.updatedAt());
  }
}
