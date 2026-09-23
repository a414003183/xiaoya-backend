package net.zentao.platform.audit;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.time.LocalDate;

/**
 * audit_query_stat 表 PO（T04/ADR-004 决策 3）：query 类操作的聚合行，唯一键 account+resource+stat_day（resource = API 路径首段，如 products）。
 *
 * <p>{@code query_count}/{@code total_ms} 是累加列，只许「单条 SQL 自增」（CONVENTIONS §2.4）——
 * 写入唯一入口是 {@link AuditQueryStatRepository}。
 */
@Table("audit_query_stat")
public class AuditQueryStatPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private String account;
  private String resource;
  /** 聚合日（UTC）；库列名 stat_day，避免与 SQL 的 DAY() 家族关键字纠缠。 */
  private LocalDate statDay;
  private Long queryCount;
  private Long totalMs;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getAccount() {
    return account;
  }

  public void setAccount(String account) {
    this.account = account;
  }

  public String getModule() {
    return resource;
  }

  public void setModule(String resource) {
    this.resource = resource;
  }

  public LocalDate getStatDay() {
    return statDay;
  }

  public void setStatDay(LocalDate statDay) {
    this.statDay = statDay;
  }

  public Long getQueryCount() {
    return queryCount;
  }

  public void setQueryCount(Long queryCount) {
    this.queryCount = queryCount;
  }

  public Long getTotalMs() {
    return totalMs;
  }

  public void setTotalMs(Long totalMs) {
    this.totalMs = totalMs;
  }
}
