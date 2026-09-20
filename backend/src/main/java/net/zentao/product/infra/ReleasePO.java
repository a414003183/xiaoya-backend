package net.zentao.product.infra;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.time.Instant;
import java.time.LocalDate;

/** product_release 表 PO（product 卡 §3.5 全列；story_ids/bug_ids/notify_accounts 存 JSON 文本）。 */
@Table("product_release")
public class ReleasePO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private Long productId;
  private Long branchId;
  private Long buildId;
  private Long projectId;
  private String name;
  private String status;
  private LocalDate releaseDate;
  private Instant publishedAt;
  private Integer isMilestone;
  private String storyIds;
  private String bugIds;
  private String notifyAccounts;
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

  public Long getBuildId() {
    return buildId;
  }

  public void setBuildId(Long buildId) {
    this.buildId = buildId;
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDate getReleaseDate() {
    return releaseDate;
  }

  public void setReleaseDate(LocalDate releaseDate) {
    this.releaseDate = releaseDate;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(Instant publishedAt) {
    this.publishedAt = publishedAt;
  }

  public Integer getIsMilestone() {
    return isMilestone;
  }

  public void setIsMilestone(Integer isMilestone) {
    this.isMilestone = isMilestone;
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

  public String getNotifyAccounts() {
    return notifyAccounts;
  }

  public void setNotifyAccounts(String notifyAccounts) {
    this.notifyAccounts = notifyAccounts;
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
