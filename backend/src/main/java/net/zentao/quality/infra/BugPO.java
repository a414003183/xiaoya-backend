package net.zentao.quality.infra;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.time.Instant;
import java.time.LocalDate;

/** bug 表 PO（quality 卡 §3.1 全列；related_bug_ids/notify_accounts/custom_fields 存 JSON 文本）。 */
@Table("bug")
public class BugPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private Long productId;
  private Long branchId;
  private Long categoryId;
  private Long projectId;
  private Long executionId;
  private Long planId;
  private Long storyId;
  private Long taskId;
  private Long testCaseId;
  private Long testRunId;
  private String title;
  private String keywords;
  private Integer severity;
  private Integer priority;
  private String type;
  private String os;
  private String browser;
  private String steps;
  private String openedBuilds;
  private String status;
  private Integer confirmed;
  private Integer activatedCount;
  private LocalDate deadline;
  private String assignee;
  private Instant assignedAt;
  private String resolution;
  private String resolvedBy;
  private Instant resolvedAt;
  private String resolvedBuild;
  private Long duplicateOfId;
  private String relatedBugIds;
  private String notifyAccounts;
  private String closedBy;
  private Instant closedAt;
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

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long projectId) {
    this.projectId = projectId;
  }

  public Long getExecutionId() {
    return executionId;
  }

  public void setExecutionId(Long executionId) {
    this.executionId = executionId;
  }

  public Long getPlanId() {
    return planId;
  }

  public void setPlanId(Long planId) {
    this.planId = planId;
  }

  public Long getStoryId() {
    return storyId;
  }

  public void setStoryId(Long storyId) {
    this.storyId = storyId;
  }

  public Long getTaskId() {
    return taskId;
  }

  public void setTaskId(Long taskId) {
    this.taskId = taskId;
  }

  public Long getTestCaseId() {
    return testCaseId;
  }

  public void setTestCaseId(Long testCaseId) {
    this.testCaseId = testCaseId;
  }

  public Long getTestRunId() {
    return testRunId;
  }

  public void setTestRunId(Long testRunId) {
    this.testRunId = testRunId;
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

  public Integer getSeverity() {
    return severity;
  }

  public void setSeverity(Integer severity) {
    this.severity = severity;
  }

  public Integer getPriority() {
    return priority;
  }

  public void setPriority(Integer priority) {
    this.priority = priority;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getOs() {
    return os;
  }

  public void setOs(String os) {
    this.os = os;
  }

  public String getBrowser() {
    return browser;
  }

  public void setBrowser(String browser) {
    this.browser = browser;
  }

  public String getSteps() {
    return steps;
  }

  public void setSteps(String steps) {
    this.steps = steps;
  }

  public String getOpenedBuilds() {
    return openedBuilds;
  }

  public void setOpenedBuilds(String openedBuilds) {
    this.openedBuilds = openedBuilds;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getConfirmed() {
    return confirmed;
  }

  public void setConfirmed(Integer confirmed) {
    this.confirmed = confirmed;
  }

  public Integer getActivatedCount() {
    return activatedCount;
  }

  public void setActivatedCount(Integer activatedCount) {
    this.activatedCount = activatedCount;
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

  public String getResolution() {
    return resolution;
  }

  public void setResolution(String resolution) {
    this.resolution = resolution;
  }

  public String getResolvedBy() {
    return resolvedBy;
  }

  public void setResolvedBy(String resolvedBy) {
    this.resolvedBy = resolvedBy;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public String getResolvedBuild() {
    return resolvedBuild;
  }

  public void setResolvedBuild(String resolvedBuild) {
    this.resolvedBuild = resolvedBuild;
  }

  public Long getDuplicateOfId() {
    return duplicateOfId;
  }

  public void setDuplicateOfId(Long duplicateOfId) {
    this.duplicateOfId = duplicateOfId;
  }

  public String getRelatedBugIds() {
    return relatedBugIds;
  }

  public void setRelatedBugIds(String relatedBugIds) {
    this.relatedBugIds = relatedBugIds;
  }

  public String getNotifyAccounts() {
    return notifyAccounts;
  }

  public void setNotifyAccounts(String notifyAccounts) {
    this.notifyAccounts = notifyAccounts;
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
