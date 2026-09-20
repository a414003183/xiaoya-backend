package net.zentao.product.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 发布聚合根（product 卡 §3.5/§4.4；状态集 normal|terminated）。 */
public class Release {

  private final long id;
  private long productId;
  private long branchId;
  private Long buildId;
  private long projectId;
  private String name;
  private String status;
  private LocalDate releaseDate;
  private Instant publishedAt;
  private boolean isMilestone;
  private List<Long> storyIds;
  private List<Long> bugIds;
  private List<String> notifyAccounts;
  private String description;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private int lockVersion;

  /** link/unlink 请求体字段（fire 前写入，供 YAML 守卫判定；不落库）。 */
  private String linkObjectType;
  private List<Long> linkIds = List.of();

  public Release(long id, long productId, long branchId, Long buildId, long projectId, String name, String status,
      LocalDate releaseDate, Instant publishedAt, boolean isMilestone, List<Long> storyIds, List<Long> bugIds,
      List<String> notifyAccounts, String description, String createdBy, Instant createdAt, String updatedBy,
      Instant updatedAt, int lockVersion) {
    this.id = id;
    this.productId = productId;
    this.branchId = branchId;
    this.buildId = buildId;
    this.projectId = projectId;
    this.name = name;
    this.status = status;
    this.releaseDate = releaseDate;
    this.publishedAt = publishedAt;
    this.isMilestone = isMilestone;
    this.storyIds = storyIds == null ? List.of() : List.copyOf(storyIds);
    this.bugIds = bugIds == null ? List.of() : List.copyOf(bugIds);
    this.notifyAccounts = notifyAccounts == null ? List.of() : List.copyOf(notifyAccounts);
    this.description = description;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（product 卡 §5）；null 不改。 */
  public void update(String name, Long branchId, Long buildId, Long projectId, LocalDate releaseDate,
      Instant publishedAt, Boolean isMilestone, List<String> notifyAccounts, String description) {
    if (name != null) {
      this.name = name;
    }
    if (branchId != null) {
      this.branchId = branchId;
    }
    if (buildId != null) {
      this.buildId = buildId == 0 ? null : buildId;
    }
    if (projectId != null) {
      this.projectId = projectId;
    }
    if (releaseDate != null) {
      this.releaseDate = releaseDate;
    }
    if (publishedAt != null) {
      this.publishedAt = publishedAt;
    }
    if (isMilestone != null) {
      this.isMilestone = isMilestone;
    }
    if (notifyAccounts != null) {
      this.notifyAccounts = List.copyOf(notifyAccounts);
    }
    if (description != null) {
      this.description = description;
    }
  }

  /** 关联维护（link/unlink 幂等）。 */
  public void replaceStoryIds(List<Long> ids) {
    this.storyIds = ids == null ? List.of() : List.copyOf(ids);
  }

  public void replaceBugIds(List<Long> ids) {
    this.bugIds = ids == null ? List.of() : List.copyOf(ids);
  }

  public void linkRequest(String objectType, List<Long> ids) {
    this.linkObjectType = objectType;
    this.linkIds = ids == null ? List.of() : List.copyOf(ids);
  }

  public void applyStatus(String status) {
    this.status = status;
  }

  public void setField(String field, Object value) {
    switch (field) {
      case "publishedAt" -> this.publishedAt = value == null ? null : Instant.parse(String.valueOf(value));
      default -> throw new IllegalArgumentException("发布未声明的 fieldSet 字段：" + field);
    }
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

  public Long buildId() {
    return buildId;
  }

  public long projectId() {
    return projectId;
  }

  public String name() {
    return name;
  }

  public String status() {
    return status;
  }

  public LocalDate releaseDate() {
    return releaseDate;
  }

  public Instant publishedAt() {
    return publishedAt;
  }

  public boolean isMilestone() {
    return isMilestone;
  }

  public List<Long> storyIds() {
    return storyIds;
  }

  public List<Long> bugIds() {
    return bugIds;
  }

  public List<String> notifyAccounts() {
    return notifyAccounts;
  }

  public String description() {
    return description;
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
