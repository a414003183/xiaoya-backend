package net.zentao.platform.audit;

/**
 * 审计结果（ADR-004 决策 1）。{@code value()} 是契约枚举与 {@code ck_audit_log_result} CHECK 的共同真源。
 *
 * <p>success = 动作完成；fail = 动作抛错（reason 记原因）；denied = 权限/数据权限拒绝——
 * T04 只建语义位，denied 的写入面随 T10 的授权埋点接入（拦截器当前不落审计行）。
 */
public enum AuditResult {
  SUCCESS("success"),
  FAIL("fail"),
  DENIED("denied");

  private final String value;

  AuditResult(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
