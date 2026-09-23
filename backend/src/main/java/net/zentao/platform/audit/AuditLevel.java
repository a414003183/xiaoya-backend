package net.zentao.platform.audit;

/**
 * 采集粒度（ADR-004 决策 2 的声明式分级）。分级只影响「记什么」，不影响「记不记」——
 * 审计行的存在性由动作是否发生决定。
 */
public enum AuditLevel {
  /** 逐条落主表，带字段级 diff（必要时带 snapshot/extra）。权限/配置/审批类。 */
  FULL("full"),
  /** 逐条落主表，不带 diff（登录、导出、敏感查看这类「发生即结论」的动作）。 */
  SUMMARY("summary"),
  /** 不进主表，只进 {@code audit_query_stat} 聚合（普通查询/列表/翻页）。 */
  SAMPLED("sampled");

  private final String value;

  AuditLevel(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
