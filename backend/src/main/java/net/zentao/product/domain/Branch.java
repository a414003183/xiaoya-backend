package net.zentao.product.domain;

import java.time.Instant;

/** 产品分支聚合根（product 卡 §3.2；纯 Java，A1）。 */
public class Branch {

  private final long id;
  private long productId;
  private String name;
  private boolean isDefault;
  private String status;
  private String description;
  private int sort;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private Instant closedAt;
  private int lockVersion;

  public Branch(long id, long productId, String name, boolean isDefault, String status, String description, int sort,
      String createdBy, Instant createdAt, String updatedBy, Instant updatedAt, Instant closedAt, int lockVersion) {
    this.id = id;
    this.productId = productId;
    this.name = name;
    this.isDefault = isDefault;
    this.status = status;
    this.description = description;
    this.sort = sort;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.closedAt = closedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（product 卡 §5：name/description/sort）；null 不改。 */
  public void update(String name, String description, Integer sort) {
    if (name != null) {
      this.name = name;
    }
    if (description != null) {
      this.description = description;
    }
    if (sort != null) {
      this.sort = sort;
    }
  }

  /** set-default 排他（§4.2）：由处理器对同产品其余分支置 false，本对象置 true。 */
  public void markDefault(boolean value) {
    this.isDefault = value;
  }

  public void applyStatus(String status) {
    this.status = status;
  }

  /** fieldSet 副作用入口（product.yml branch：closedAt / isDefault）。 */
  public void setField(String field, Object value) {
    switch (field) {
      case "closedAt" -> this.closedAt = value == null ? null : toInstant(value);
      case "isDefault" -> this.isDefault = Boolean.TRUE.equals(value) || "true".equals(String.valueOf(value));
      default -> throw new IllegalArgumentException("分支未声明的 fieldSet 字段：" + field);
    }
  }

  private static Instant toInstant(Object value) {
    if (value instanceof Instant instant) {
      return instant;
    }
    return Instant.parse(String.valueOf(value));
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  public long id() {
    return id;
  }

  public long productId() {
    return productId;
  }

  public String name() {
    return name;
  }

  public boolean isDefault() {
    return isDefault;
  }

  public String status() {
    return status;
  }

  public String description() {
    return description;
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

  public Instant closedAt() {
    return closedAt;
  }

  public int lockVersion() {
    return lockVersion;
  }
}
