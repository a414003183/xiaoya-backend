package net.zentao.project.infra;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** project 表 PO（project 卡 §3.1 全列；白名单在 acl_entry 表，无镜像列）。 */
@Table("project")
public class ProjectPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private String type;
  private Long parentId;
  private String path;
  private Integer grade;
  private String name;
  private String code;
  private String model;
  private String status;
  private Integer priority;
  private LocalDate beginDate;
  private LocalDate endDate;
  private LocalDate firstEndDate;
  private LocalDate realBeganDate;
  private LocalDate realEndDate;
  private Integer days;
  private BigDecimal budget;
  private String budgetUnit;
  private String description;
  private String pm;
  private String po;
  private String qd;
  private String rd;
  private Integer progress;
  private BigDecimal estimateHours;
  private BigDecimal consumedHours;
  private BigDecimal leftHours;
  private Integer isMilestone;
  private String acl;
  private Integer sort;
  private String customFields;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private String closedBy;
  private Instant closedAt;
  private Instant deletedAt;

  @Column(version = true)
  private Integer lockVersion;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public Integer getGrade() {
    return grade;
  }

  public void setGrade(Integer grade) {
    this.grade = grade;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
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

  public LocalDate getBeginDate() {
    return beginDate;
  }

  public void setBeginDate(LocalDate beginDate) {
    this.beginDate = beginDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public LocalDate getFirstEndDate() {
    return firstEndDate;
  }

  public void setFirstEndDate(LocalDate firstEndDate) {
    this.firstEndDate = firstEndDate;
  }

  public LocalDate getRealBeganDate() {
    return realBeganDate;
  }

  public void setRealBeganDate(LocalDate realBeganDate) {
    this.realBeganDate = realBeganDate;
  }

  public LocalDate getRealEndDate() {
    return realEndDate;
  }

  public void setRealEndDate(LocalDate realEndDate) {
    this.realEndDate = realEndDate;
  }

  public Integer getDays() {
    return days;
  }

  public void setDays(Integer days) {
    this.days = days;
  }

  public BigDecimal getBudget() {
    return budget;
  }

  public void setBudget(BigDecimal budget) {
    this.budget = budget;
  }

  public String getBudgetUnit() {
    return budgetUnit;
  }

  public void setBudgetUnit(String budgetUnit) {
    this.budgetUnit = budgetUnit;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getPm() {
    return pm;
  }

  public void setPm(String pm) {
    this.pm = pm;
  }

  public String getPo() {
    return po;
  }

  public void setPo(String po) {
    this.po = po;
  }

  public String getQd() {
    return qd;
  }

  public void setQd(String qd) {
    this.qd = qd;
  }

  public String getRd() {
    return rd;
  }

  public void setRd(String rd) {
    this.rd = rd;
  }

  public Integer getProgress() {
    return progress;
  }

  public void setProgress(Integer progress) {
    this.progress = progress;
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

  public Integer getIsMilestone() {
    return isMilestone;
  }

  public void setIsMilestone(Integer isMilestone) {
    this.isMilestone = isMilestone;
  }

  public String getAcl() {
    return acl;
  }

  public void setAcl(String acl) {
    this.acl = acl;
  }

  public Integer getSort() {
    return sort;
  }

  public void setSort(Integer sort) {
    this.sort = sort;
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
