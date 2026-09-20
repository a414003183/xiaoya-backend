package net.zentao.product.infra;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.time.Instant;
import java.time.LocalDate;

/** build 表 PO（product 卡 §3.6 全列）。 */
@Table("build")
public class BuildPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private Long productId;
  private Long branchId;
  private Long executionId;
  private Long projectId;
  private String name;
  private String scmPath;
  private String filePath;
  private LocalDate buildDate;
  private String builder;
  private String storyIds;
  private String bugIds;
  private String description;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private Instant deletedAt;

  @Column(version = true)
  private Integer lockVersion;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getProductId() {
    return productId;
  }

  public void setProductId(Long productId) {
    this.productId = productId;
  }

  public Long getBranchId() {
    return branchId;
  }

  public void setBranchId(Long branchId) {
    this.branchId = branchId;
  }

  public Long getExecutionId() {
    return executionId;
  }

  public void setExecutionId(Long executionId) {
    this.executionId = executionId;
  }

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long projectId) {
    this.projectId = projectId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getScmPath() {
    return scmPath;
  }

  public void setScmPath(String scmPath) {
    this.scmPath = scmPath;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public LocalDate getBuildDate() {
    return buildDate;
  }

  public void setBuildDate(LocalDate buildDate) {
    this.buildDate = buildDate;
  }

  public String getBuilder() {
    return builder;
  }

  public void setBuilder(String builder) {
    this.builder = builder;
  }

  public String getStoryIds() {
    return storyIds;
  }

  public void setStoryIds(String storyIds) {
    this.storyIds = storyIds;
  }

  public String getBugIds() {
    return bugIds;
  }

  public void setBugIds(String bugIds) {
    this.bugIds = bugIds;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }

  public Integer getLockVersion() {
    return lockVersion;
  }

  public void setLockVersion(Integer lockVersion) {
    this.lockVersion = lockVersion;
  }
}
