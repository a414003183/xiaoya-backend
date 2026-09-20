package net.zentao.workspace.domain;

import java.time.Instant;
import java.time.LocalDate;

/** 待办聚合根（workspace 卡 §3.1；纯 Java，A1）。beginTime/endTime 在领域内恒为 HH:mm 形态。 */
public class Todo {

  private final long id;
  private String title;
  private String type;
  private long objectId;
  private LocalDate date;
  private String beginTime;
  private String endTime;
  private int priority;
  private String description;
  private String status;
  private boolean isPrivate;
  private String assignee;
  private String assignedBy;
  private Instant assignedAt;
  private String finishedBy;
  private Instant finishedAt;
  private String closedBy;
  private Instant closedAt;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private int lockVersion;

  public Todo(long id, String title, String type, long objectId, LocalDate date, String beginTime, String endTime,
      int priority, String description, String status, boolean isPrivate, String assignee, String assignedBy,
      Instant assignedAt, String finishedBy, Instant finishedAt, String closedBy, Instant closedAt, String createdBy,
      Instant createdAt, String updatedBy, Instant updatedAt, int lockVersion) {
    this.id = id;
    this.title = title;
    this.type = type;
    this.objectId = objectId;
    this.date = date;
    this.beginTime = beginTime;
    this.endTime = endTime;
    this.priority = priority;
    this.description = description;
    this.status = status;
    this.isPrivate = isPrivate;
    this.assignee = assignee;
    this.assignedBy = assignedBy;
    this.assignedAt = assignedAt;
    this.finishedBy = finishedBy;
    this.finishedAt = finishedAt;
    this.closedBy = closedBy;
    this.closedAt = closedAt;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（workspace 卡 §5：title/type/objectId/date/beginTime/endTime/priority/description/isPrivate）；null 不改。 */
  public void update(String title, String type, Long objectId, LocalDate date, String beginTime, String endTime,
      Integer priority, String description, Boolean isPrivate) {
    if (title != null) {
      this.title = title;
    }
    if (type != null) {
      this.type = type;
    }
    if (objectId != null) {
      this.objectId = objectId;
    }
    if (date != null) {
      this.date = date;
    }
    if (beginTime != null) {
      this.beginTime = beginTime;
    }
    if (endTime != null) {
      this.endTime = endTime;
    }
    if (priority != null) {
      this.priority = priority;
    }
    if (description != null) {
      this.description = description;
    }
    if (isPrivate != null) {
      this.isPrivate = isPrivate;
    }
  }

  /** 状态机落状态（todo.yml start/finish/activate/close → WorkflowEngine.applyStatus）。 */
  public void applyStatus(String status) {
    this.status = status;
  }

  /** fieldSet 副作用入口（todo.yml：finish 落 finished*、activate 清四列、close 落 closed*、assign 落 assigned*）。 */
  public void setField(String field, Object value) {
    switch (field) {
      case "assignedBy" -> this.assignedBy = value == null ? null : String.valueOf(value);
      case "assignedAt" -> this.assignedAt = value == null ? null : toInstant(value);
      case "finishedBy" -> this.finishedBy = value == null ? null : String.valueOf(value);
      case "finishedAt" -> this.finishedAt = value == null ? null : toInstant(value);
      case "closedBy" -> this.closedBy = value == null ? null : String.valueOf(value);
      case "closedAt" -> this.closedAt = value == null ? null : toInstant(value);
      case "assignee" -> this.assignee = value == null ? this.assignee : String.valueOf(value);
      default -> throw new IllegalArgumentException("待办未声明的 fieldSet 字段：" + field);
    }
  }

  /** 指派联动（assign 动作的请求体字段由处理器在 fire 前写入）。 */
  public void assignTo(String assignee) {
    this.assignee = assignee;
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  private static Instant toInstant(Object value) {
    if (value instanceof Instant instant) {
      return instant;
    }
    return Instant.parse(String.valueOf(value));
  }

  public long id() {
    return id;
  }

  public String title() {
    return title;
  }

  public String type() {
    return type;
  }

  public long objectId() {
    return objectId;
  }

  public LocalDate date() {
    return date;
  }

  public String beginTime() {
    return beginTime;
  }

  public String endTime() {
    return endTime;
  }

  public int priority() {
    return priority;
  }

  public String description() {
    return description;
  }

  public String status() {
    return status;
  }

  public boolean isPrivate() {
    return isPrivate;
  }

  public String assignee() {
    return assignee;
  }

  public String assignedBy() {
    return assignedBy;
  }

  public Instant assignedAt() {
    return assignedAt;
  }

  public String finishedBy() {
    return finishedBy;
  }

  public Instant finishedAt() {
    return finishedAt;
  }

  public String closedBy() {
    return closedBy;
  }

  public Instant closedAt() {
    return closedAt;
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

  public int lockVersion() {
    return lockVersion;
  }
}
