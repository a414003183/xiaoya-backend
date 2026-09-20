package net.zentao.workspace.infra;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** weekly_report 表 PO（workspace 卡 §3.2；workload 为 JSON 文本列，无软删无乐观锁）。 */
@Table("weekly_report")
public class WeeklyReportPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private Long projectId;
  private LocalDate weekStart;
  private BigDecimal pv;
  private BigDecimal ev;
  private BigDecimal ac;
  private BigDecimal sv;
  private BigDecimal cv;
  private Integer staff;
  private String workload;
  private Instant updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long projectId) {
    this.projectId = projectId;
  }

  public LocalDate getWeekStart() {
    return weekStart;
  }

  public void setWeekStart(LocalDate weekStart) {
    this.weekStart = weekStart;
  }

  public BigDecimal getPv() {
    return pv;
  }

  public void setPv(BigDecimal pv) {
    this.pv = pv;
  }

  public BigDecimal getEv() {
    return ev;
  }

  public void setEv(BigDecimal ev) {
    this.ev = ev;
  }

  public BigDecimal getAc() {
    return ac;
  }

  public void setAc(BigDecimal ac) {
    this.ac = ac;
  }

  public BigDecimal getSv() {
    return sv;
  }

  public void setSv(BigDecimal sv) {
    this.sv = sv;
  }

  public BigDecimal getCv() {
    return cv;
  }

  public void setCv(BigDecimal cv) {
    this.cv = cv;
  }

  public Integer getStaff() {
    return staff;
  }

  public void setStaff(Integer staff) {
    this.staff = staff;
  }

  public String getWorkload() {
    return workload;
  }

  public void setWorkload(String workload) {
    this.workload = workload;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
