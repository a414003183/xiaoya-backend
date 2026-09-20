package net.zentao.product.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 产品计划聚合根（product 卡 §3.4；父子仅两级）。 */
public class Plan {

  private final long id;
  private long productId;
  private long branchId;
  private long parentId;
  private String title;
  private String status;
  private String description;
  private LocalDate beginDate;
  private LocalDate endDate;
  private Instant finishedAt;
  private Instant closedAt;
  private String closedReason;
  private Map<String, Object> customFields;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private int lockVersion;

  /** link/unlink 请求体字段（fire 前写入，供 YAML 守卫判定；不落库）。 */
  private String linkObjectType;
  private List<Long> linkIds = List.of();

  public Plan(long id, long productId, long branchId, long parentId, String title, String status, String description,
      LocalDate beginDate, LocalDate endDate, Instant finishedAt, Instant closedAt, String closedReason,
      Map<String, Object> customFields, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt,
      int lockVersion) {
    this.id = id;
    this.productId = productId;
    this.branchId = branchId;
    this.parentId = parentId;
    this.title = title;
    this.status = status;
    this.description = description;
    this.beginDate = beginDate;
    this.endDate = endDate;
    this.finishedAt = finishedAt;
    this.closedAt = closedAt;
    this.closedReason = closedReason;
    this.customFields = customFields == null ? Map.of() : Map.copyOf(customFields);
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（product 卡 §5：title/branchId/parentId/beginDate/endDate/description/customFields）；null 不改。 */
  public void update(String title, Long branchId, Long parentId, LocalDate beginDate, LocalDate endDate,
      String description, Map<String, Object> customFields) {
    if (title != null) {
      this.title = title;
    }
    if (branchId != null) {
      this.branchId = branchId;
    }
    if (parentId != null) {
      this.parentId = parentId;
    }
    if (beginDate != null) {
      this.beginDate = beginDate;
    }
    if (endDate != null) {
      this.endDate = endDate;
    }
    if (description != null) {
      this.description = description;
    }
    if (customFields != null) {
      this.customFields = Map.copyOf(customFields);
    }
  }

  public void markClosedReason(String closedReason) {
    this.closedReason = closedReason;
  }

  public void linkRequest(String objectType, List<Long> ids) {
    this.linkObjectType = objectType;
    this.linkIds = ids == null ? List.of() : List.copyOf(ids);
  }

  public void applyStatus(String status) {
    this.status = status;
  }

  /** fieldSet 副作用入口（product.yml plan：finishedAt/closedAt/closedReason）。 */
  public void setField(String field, Object value) {
    switch (field) {
      case "finishedAt" -> this.finishedAt = toInstant(value);
      case "closedAt" -> this.closedAt = toInstant(value);
      case "closedReason" -> this.closedReason = value == null ? null : String.valueOf(value);
      default -> throw new IllegalArgumentException("计划未声明的 fieldSet 字段：" + field);
    }
  }

  private static Instant toInstant(Object value) {
    if (value == null) {
      return null;
    }
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

  public long branchId() {
    return branchId;
  }

  public long parentId() {
    return parentId;
  }

  public String title() {
    return title;
  }

  public String status() {
    return status;
  }

  public String description() {
    return description;
  }

  public LocalDate beginDate() {
    return beginDate;
  }

  public LocalDate endDate() {
    return endDate;
  }

  public Instant finishedAt() {
    return finishedAt;
  }

  public Instant closedAt() {
    return closedAt;
  }

  public String closedReason() {
    return closedReason;
  }

  public Map<String, Object> customFields() {
    return customFields;
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

  public String linkObjectType() {
    return linkObjectType;
  }

  public List<Long> linkIds() {
    return linkIds;
  }
}
