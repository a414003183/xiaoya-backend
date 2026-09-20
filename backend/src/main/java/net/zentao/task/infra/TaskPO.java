package net.zentao.task.infra;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** task 表 PO（task 卡 §3 全列；notify_accounts/custom_fields 存 JSON 文本）。 */
@Table("task")
public class TaskPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private Long executionId;
  private Long projectId;
  private Long storyId;
  private Long parentId;
  private Long categoryId;
  private String title;
  private String type;
  private String status;
  private Integer priority;
  private BigDecimal estimateHours;
  private BigDecimal consumedHours;
  private BigDecimal leftHours;
  private LocalDate estStartedDate;
  private LocalDate deadline;
  private String assignee;
  private Instant assignedAt;
  private Instant startedAt;
  private Instant activatedAt;
  private String finishedBy;
  private Instant finishedAt;
  private String canceledBy;
  private Instant canceledAt;
  private String closedBy;
  private Instant closedAt;
  private String closedReason;
  private String keywords;
  private String description;
  private Integer isParent;
  private String notifyAccounts;
  private String customFields;
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

  public Long getStoryId() {
    return storyId;
  }

  public void setStoryId(Long storyId) {
    this.storyId = storyId;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public Long getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(Long categoryId) {
    this.categoryId = categoryId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
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

  public BigDecimal getConsumedHours() {
    return consumedHours;
  }

  public void setConsumedHours(BigDecimal consumedHours) {
    this.consumedHours = consumedHours;
  }

  public BigDecimal getLeftHours() {
    return leftHours;
  }

  public void setLeftHours(BigDecimal leftHours) {
    this.leftHours = leftHours;
  }

  public LocalDate getEstStartedDate() {
    return estStartedDate;
  }

  public void setEstStartedDate(LocalDate estStartedDate) {
    this.estStartedDate = estStartedDate;
  }

  public LocalDate getDeadline() {
    return deadline;
  }

  public void setDeadline(LocalDate deadline) {
    this.deadline = deadline;
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

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getActivatedAt() {
    return activatedAt;
  }

  public void setActivatedAt(Instant activatedAt) {
    this.activatedAt = activatedAt;
  }

  public String getFinishedBy() {
    return finishedBy;
  }

  public void setFinishedBy(String finishedBy) {
    this.finishedBy = finishedBy;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
  }

  public String getCanceledBy() {
    return canceledBy;
  }

  public void setCanceledBy(String canceledBy) {
    this.canceledBy = canceledBy;
  }

  public Instant getCanceledAt() {
    return canceledAt;
  }

  public void setCanceledAt(Instant canceledAt) {
    this.canceledAt = canceledAt;
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

  public String getKeywords() {
    return keywords;
  }

  public void setKeywords(String keywords) {
    this.keywords = keywords;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Integer getIsParent() {
    return isParent;
  }

  public void setIsParent(Integer isParent) {
    this.isParent = isParent;
  }

  public String getNotifyAccounts() {
    return notifyAccounts;
  }

  public void setNotifyAccounts(String notifyAccounts) {
    this.notifyAccounts = notifyAccounts;
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
