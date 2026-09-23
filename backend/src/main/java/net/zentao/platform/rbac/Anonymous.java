package net.zentao.platform.rbac;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 匿名端点显式标记（T50）：拦截器的**唯一放行口**。
 *
 * <p>{@link PrivilegeInterceptor} 三态判定——带 {@link RequirePrivilege} 查码；带本注解=真匿名（不解析会话）；
 * 两者都没有则**默认要求已认证会话**（无会话 40101）。此前"没标注"= 匿名放行，忘标一个就是裸奔端点
 * （SEC-02：`/menus/routes`、`/dicts/{name}`、`/meta/{domain}`、`/departments/tree`）。
 *
 * <p>理由必填：它同时是"这个端点为什么可以匿名"的书面登记，`tools/contract-check/check-privilege-coverage.mjs`
 * 强制非空（防止 {@code @Anonymous("")} 变成新的"忘了标注"）。同时带码与匿名时**码优先**，门禁静态报冲突。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Anonymous {

  /** 允许匿名的理由（门禁要求非空，写清"匿名为什么安全"）。 */
  String value();
}
