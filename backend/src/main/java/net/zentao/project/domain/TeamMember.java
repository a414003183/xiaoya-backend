package net.zentao.project.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 团队成员聚合根（project 卡 §3.7；纯 Java，A1）：(objectType, objectId, account) 唯一，全量提交走 diff。
 * joinDate 缺省当天，hours 为每日可用工时（0–24，≤1 位小数）。
 */
public class TeamMember {

  private final long id;
  private final String objectType;
  private final long objectId;
  private final String account;
  private String role;
  private LocalDate joinDate;
  private int days;
  private BigDecimal hours;
  private int sort;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;

  public TeamMember(long id, String objectType, long objectId, String account, String role, LocalDate joinDate,
      int days, BigDecimal hours, int sort, String createdBy, Instant createdAt, String updatedBy,
      Instant updatedAt) {
    this.id = id;
    this.objectType = objectType;
    this.objectId = objectId;
    this.account = account;
    this.role = role;
    this.joinDate = joinDate;
    this.days = days;
    this.hours = hours;
    this.sort = sort;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
  }

  /**
   * 全量提交行覆盖（提交表即目标态）：role 可清空（null 即无角色），其余数值/日期由 app 层取缺省值后传入。
   */
  public void apply(String role, LocalDate joinDate, int days, BigDecimal hours, int sort) {
    this.role = role;
    this.joinDate = joinDate;
    this.days = days;
    this.hours = hours;
    this.sort = sort;
  }

  /** 幂等判定：提交行与库内行同内容 → 不产生写入（§8 幂等）。 */
  public boolean sameAs(String role, LocalDate joinDate, int days, BigDecimal hours, int sort) {
    return java.util.Objects.equals(this.role, role) && java.util.Objects.equals(this.joinDate, joinDate)
        && this.days == days && (this.hours == null ? BigDecimal.ZERO : this.hours).compareTo(hours) == 0
        && this.sort == sort;
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  public long id() {
    return id;
  }

  public String objectType() {
    return objectType;
  }

  public long objectId() {
    return objectId;
  }

  public String account() {
    return account;
  }

  public String role() {
    return role;
  }

  public LocalDate joinDate() {
    return joinDate;
  }

  public int days() {
    return days;
  }

  public BigDecimal hours() {
    return hours;
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
}
