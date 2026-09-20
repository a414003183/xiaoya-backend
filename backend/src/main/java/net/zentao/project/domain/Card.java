package net.zentao.project.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 看板卡片聚合根（project 卡 §3.6；纯 Java，A1）。
 * 归属单源 laneId + sort；status 只有 doing/done 且由 PATCH 直改（§5 唯一状态直改例外），无状态机。
 */
public class Card {

  private final long id;
  private final long boardId;
  private long laneId;
  private String name;
  private String description;
  private String status;
  private int priority;
  private String assignee;
  private LocalDate beginDate;
  private LocalDate endDate;
  private BigDecimal estimateHours;
  private int progress;
  private String color;
  private boolean archived;
  private int sort;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private int lockVersion;

  public Card(long id, long boardId, long laneId, String name, String description, String status, int priority,
      String assignee, LocalDate beginDate, LocalDate endDate, BigDecimal estimateHours, int progress, String color,
      boolean archived, int sort, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt,
      int lockVersion) {
    this.id = id;
    this.boardId = boardId;
    this.laneId = laneId;
    this.name = name;
    this.description = description;
    this.status = status;
    this.priority = priority;
    this.assignee = assignee;
    this.beginDate = beginDate;
    this.endDate = endDate;
    this.estimateHours = estimateHours;
    this.progress = progress;
    this.color = color;
    this.archived = archived;
    this.sort = sort;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（§5：name/description/priority/assignee/beginDate/endDate/estimateHours/progress/color/status）。 */
  public void update(String name, String description, Integer priority, String assignee, LocalDate beginDate,
      LocalDate endDate, BigDecimal estimateHours, Integer progress, String color, String status) {
    if (name != null) {
      this.name = name;
    }
    if (description != null) {
      this.description = description;
    }
    if (priority != null) {
      this.priority = priority;
    }
    if (assignee != null) {
      this.assignee = assignee;
    }
    if (beginDate != null) {
      this.beginDate = beginDate;
    }
    if (endDate != null) {
      this.endDate = endDate;
    }
    if (estimateHours != null) {
      this.estimateHours = estimateHours;
    }
    if (progress != null) {
      this.progress = progress;
    }
    if (color != null) {
      this.color = color;
    }
    if (status != null) {
      this.status = status;
    }
  }

  /** 拖拽：改写归属单源（laneId + 列内 sort）；wipLimit/lane 归属守卫在 CardHandlers。 */
  public void move(long laneId, int sort) {
    this.laneId = laneId;
    this.sort = sort;
  }

  /** 归档：只置 archived，不动 status（§5）。 */
  public void archive() {
    this.archived = true;
  }

  /** 取消归档（B-PRJ-13）：archived=false，同样不动 status。 */
  public void unarchive() {
    this.archived = false;
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  public long id() {
    return id;
  }

  public long boardId() {
    return boardId;
  }

  public long laneId() {
    return laneId;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public String status() {
    return status;
  }

  public int priority() {
    return priority;
  }

  public String assignee() {
    return assignee;
  }

  public LocalDate beginDate() {
    return beginDate;
  }

  public LocalDate endDate() {
    return endDate;
  }

  public BigDecimal estimateHours() {
    return estimateHours;
  }

  public int progress() {
    return progress;
  }

  public String color() {
    return color;
  }

  public boolean archived() {
    return archived;
  }

  public int sort() {
    return sort;
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
