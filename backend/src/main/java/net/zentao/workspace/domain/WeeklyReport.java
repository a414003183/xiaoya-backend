package net.zentao.workspace.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** 项目周报快照（workspace 卡 §3.2；无软删无乐观锁，重算即整行覆写）。 */
public class WeeklyReport {

  private final long id;
  private final long projectId;
  private final LocalDate weekStart;
  private BigDecimal pv;
  private BigDecimal ev;
  private BigDecimal ac;
  private BigDecimal sv;
  private BigDecimal cv;
  private int staff;
  private Map<String, BigDecimal> workload;
  private Instant updatedAt;

  public WeeklyReport(long id, long projectId, LocalDate weekStart, BigDecimal pv, BigDecimal ev, BigDecimal ac,
      BigDecimal sv, BigDecimal cv, int staff, Map<String, BigDecimal> workload, Instant updatedAt) {
    this.id = id;
    this.projectId = projectId;
    this.weekStart = weekStart;
    this.pv = pv;
    this.ev = ev;
    this.ac = ac;
    this.sv = sv;
    this.cv = cv;
    this.staff = staff;
    this.workload = workload == null ? Map.of() : Map.copyOf(workload);
    this.updatedAt = updatedAt;
  }

  /** 重算覆写（读取时幂等重算）。 */
  public void recompute(BigDecimal pv, BigDecimal ev, BigDecimal ac, BigDecimal sv, BigDecimal cv, int staff,
      Map<String, BigDecimal> workload, Instant updatedAt) {
    this.pv = pv;
    this.ev = ev;
    this.ac = ac;
    this.sv = sv;
    this.cv = cv;
    this.staff = staff;
    this.workload = workload == null ? Map.of() : Map.copyOf(workload);
    this.updatedAt = updatedAt;
  }

  public long id() {
    return id;
  }

  public long projectId() {
    return projectId;
  }

  public LocalDate weekStart() {
    return weekStart;
  }

  public BigDecimal pv() {
    return pv;
  }

  public BigDecimal ev() {
    return ev;
  }

  public BigDecimal ac() {
    return ac;
  }

  public BigDecimal sv() {
    return sv;
  }

  public BigDecimal cv() {
    return cv;
  }

  public int staff() {
    return staff;
  }

  public Map<String, BigDecimal> workload() {
    return workload;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
