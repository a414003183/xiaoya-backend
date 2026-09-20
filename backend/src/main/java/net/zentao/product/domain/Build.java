package net.zentao.product.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 构建聚合根（product 卡 §3.6；无状态机，生命周期 = 创建/编辑/软删）。 */
public class Build {

  private final long id;
  private long productId;
  private long branchId;
  private long executionId;
  private long projectId;
  private String name;
  private String scmPath;
  private String filePath;
  private LocalDate buildDate;
  private String builder;
  private List<Long> storyIds;
  private List<Long> bugIds;
  private String description;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private int lockVersion;

  /** link/unlink 请求体字段（不落库）。 */
  private String linkObjectType;
  private List<Long> linkIds = List.of();

  public Build(long id, long productId, long branchId, long executionId, long projectId, String name, String scmPath,
      String filePath, LocalDate buildDate, String builder, List<Long> storyIds, List<Long> bugIds, String description,
      String createdBy, Instant createdAt, String updatedBy, Instant updatedAt, int lockVersion) {
    this.id = id;
    this.productId = productId;
    this.branchId = branchId;
    this.executionId = executionId;
    this.projectId = projectId;
    this.name = name;
    this.scmPath = scmPath;
    this.filePath = filePath;
    this.buildDate = buildDate;
    this.builder = builder;
    this.storyIds = storyIds == null ? List.of() : List.copyOf(storyIds);
    this.bugIds = bugIds == null ? List.of() : List.copyOf(bugIds);
    this.description = description;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（product 卡 §5）；null 不改。 */
  public void update(String name, Long branchId, String scmPath, String filePath, LocalDate buildDate, String builder,
      Long projectId, String description) {
    if (name != null) {
      this.name = name;
    }
    if (branchId != null) {
      this.branchId = branchId;
    }
    if (scmPath != null) {
      this.scmPath = scmPath;
    }
    if (filePath != null) {
      this.filePath = filePath;
    }
    if (buildDate != null) {
      this.buildDate = buildDate;
    }
    if (builder != null) {
      this.builder = builder;
    }
    if (projectId != null) {
      this.projectId = projectId;
    }
    if (description != null) {
      this.description = description;
    }
  }

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

  public long executionId() {
    return executionId;
  }

  public long projectId() {
    return projectId;
  }

  public String name() {
    return name;
  }

  public String scmPath() {
    return scmPath;
  }

  public String filePath() {
    return filePath;
  }

  public LocalDate buildDate() {
    return buildDate;
  }

  public String builder() {
    return builder;
  }

  public List<Long> storyIds() {
    return storyIds;
  }

  public List<Long> bugIds() {
    return bugIds;
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
