package net.zentao.task.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 工时流水聚合（task 卡 §3b；登记粒度=天，account 恒等于登记人）。 */
public class Effort {

  private final long id;
  private long taskId;
  private long executionId;
  private long projectId;
  private String account;
  private LocalDate workDate;
  private BigDecimal consumedHours;
  private BigDecimal leftHours;
  private String work;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;

  public Effort(long id, long taskId, long executionId, long projectId, String account, LocalDate workDate,
      BigDecimal consumedHours, BigDecimal leftHours, String work, String createdBy, Instant createdAt,
      String updatedBy, Instant updatedAt) {
    this.id = id;
    this.taskId = taskId;
    this.executionId = executionId;
    this.projectId = projectId;
    this.account = account;
    this.workDate = workDate;
    this.consumedHours = consumedHours;
    this.leftHours = leftHours;
    this.work = work;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
  }

  /** PATCH 白名单（§5：仅 workDate/consumedHours/leftHours/work；taskId/account 不可改）。 */
  public void update(LocalDate workDate, BigDecimal consumedHours, BigDecimal leftHours, String work) {
    if (workDate != null) {
      this.workDate = workDate;
    }
    if (consumedHours != null) {
      this.consumedHours = consumedHours;
    }
    if (leftHours != null) {
      this.leftHours = leftHours;
    }
    if (work != null) {
      this.work = work;
    }
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  public long id() {
    return id;
  }

  public long taskId() {
    return taskId;
  }

  public long executionId() {
    return executionId;
  }

  public long projectId() {
    return projectId;
  }

  public String account() {
    return account;
  }

  public LocalDate workDate() {
    return workDate;
  }

  public BigDecimal consumedHours() {
    return consumedHours;
  }

  public BigDecimal leftHours() {
    return leftHours;
  }

  public String work() {
    return work;
  }

  public String createdBy() {
    return createdBy;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public String updatedBy() {
    return updatedBy;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
