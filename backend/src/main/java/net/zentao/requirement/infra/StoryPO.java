package net.zentao.requirement.infra;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** story 表 PO（requirement 卡 §3 全列；reviewers/notify_accounts/linked_story_ids/custom_fields 存 JSON 文本）。 */
@Table("story")
public class StoryPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private Long productId;
  private Long branchId;
  private Long categoryId;
  private Long planId;
  private Long parentId;
  private String title;
  private String keywords;
  private String type;
  private String status;
  private Integer priority;
  private BigDecimal estimateHours;
  private String source;
  private String description;
  private String stage;
  private String assignee;
  private Instant assignedAt;
  private String reviewers;
  private Integer needNotReview;
  private String notifyAccounts;
  private String linkedStoryIds;
  private Long duplicateOfId;
  private Integer version;
  private String customFields;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private String closedBy;
  private Instant closedAt;
  private String closedReason;
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

  public Long getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(Long categoryId) {
    this.categoryId = categoryId;
  }

  public Long getPlanId() {
    return planId;
  }

  public void setPlanId(Long planId) {
    this.planId = planId;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getKeywords() {
    return keywords;
  }

  public void setKeywords(String keywords) {
    this.keywords = keywords;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getPriority() {
    return priority;
  }

  public void setPriority(Integer priority) {
    this.priority = priority;
  }

  public BigDecimal getEstimateHours() {
    return estimateHours;
  }

  public void setEstimateHours(BigDecimal estimateHours) {
    this.estimateHours = estimateHours;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getStage() {
    return stage;
  }

  public void setStage(String stage) {
    this.stage = stage;
  }

  public String getAssignee() {
    return assignee;
  }

  public void setAssignee(String assignee) {
    this.assignee = assignee;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }

  public void setAssignedAt(Instant assignedAt) {
    this.assignedAt = assignedAt;
  }

  public String getReviewers() {
    return reviewers;
  }

  public void setReviewers(String reviewers) {
    this.reviewers = reviewers;
  }

  public Integer getNeedNotReview() {
    return needNotReview;
  }

  public void setNeedNotReview(Integer needNotReview) {
    this.needNotReview = needNotReview;
  }

  public String getNotifyAccounts() {
    return notifyAccounts;
  }

  public void setNotifyAccounts(String notifyAccounts) {
    this.notifyAccounts = notifyAccounts;
  }

  public String getLinkedStoryIds() {
    return linkedStoryIds;
  }

  public void setLinkedStoryIds(String linkedStoryIds) {
    this.linkedStoryIds = linkedStoryIds;
  }

  public Long getDuplicateOfId() {
    return duplicateOfId;
  }

  public void setDuplicateOfId(Long duplicateOfId) {
    this.duplicateOfId = duplicateOfId;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public String getCustomFields() {
    return customFields;
  }

  public void setCustomFields(String customFields) {
    this.customFields = customFields;
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

  public String getClosedBy() {
    return closedBy;
  }

  public void setClosedBy(String closedBy) {
    this.closedBy = closedBy;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public void setClosedAt(Instant closedAt) {
    this.closedAt = closedAt;
  }

  public String getClosedReason() {
    return closedReason;
  }

  public void setClosedReason(String closedReason) {
    this.closedReason = closedReason;
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
