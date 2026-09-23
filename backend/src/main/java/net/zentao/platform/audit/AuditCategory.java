package net.zentao.platform.audit;

import java.util.Optional;

/**
 * 审计分类（ADR-004 决策 3 的 9 类）。{@code value()} 是契约枚举、库列取值与
 * {@code ck_audit_log_category} CHECK 的共同真源。
 */
public enum AuditCategory {
  AUTH("auth"),
  PERM("perm"),
  CONFIG("config"),
  BUSINESS("business"),
  BATCH("batch"),
  EXPORT("export"),
  SENSITIVE("sensitive"),
  QUERY("query"),
  APPROVE("approve");

  private final String value;

  AuditCategory(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  /** 存储值 → 枚举；未知值回落空（读取历史数据用，不抛）。 */
  public static Optional<AuditCategory> of(String value) {
    for (AuditCategory category : values()) {
      if (category.value.equals(value)) {
        return Optional.of(category);
      }
    }
    return Optional.empty();
  }
}
