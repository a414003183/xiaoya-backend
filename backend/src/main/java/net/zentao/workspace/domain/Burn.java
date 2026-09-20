package net.zentao.workspace.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 燃尽日行快照（workspace 卡 §3.3；task_id=0 为执行级日行）。 */
public class Burn {

  private final long id;
  private final long executionId;
  private final LocalDate burnDate;
  private final long taskId;
  private final BigDecimal estimateHours;
  private final BigDecimal consumedHours;
  private final BigDecimal leftHours;
  private final BigDecimal storyPoint;

  public Burn(long id, long executionId, LocalDate burnDate, long taskId, BigDecimal estimateHours,
      BigDecimal consumedHours, BigDecimal leftHours, BigDecimal storyPoint) {
    this.id = id;
    this.executionId = executionId;
    this.burnDate = burnDate;
    this.taskId = taskId;
    this.estimateHours = estimateHours;
    this.consumedHours = consumedHours;
    this.leftHours = leftHours;
    this.storyPoint = storyPoint;
  }

  public long id() {
    return id;
  }

  public long executionId() {
    return executionId;
  }

  public LocalDate burnDate() {
    return burnDate;
  }

  public long taskId() {
    return taskId;
  }

  public BigDecimal estimateHours() {
    return estimateHours;
  }

  public BigDecimal consumedHours() {
    return consumedHours;
  }

  public BigDecimal leftHours() {
    return leftHours;
  }

  public BigDecimal storyPoint() {
    return storyPoint;
  }

  /** 执行级汇总日行（task_id=0，逐任务行之和）。 */
  public static Burn summary(long executionId, LocalDate date, BigDecimal estimate, BigDecimal consumed,
      BigDecimal left, BigDecimal storyPoint) {
    return new Burn(0, executionId, date, 0, estimate, consumed, left, storyPoint);
  }
}
