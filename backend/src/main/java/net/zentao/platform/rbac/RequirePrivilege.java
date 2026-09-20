package net.zentao.platform.rbac;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 端点功能权限码声明（platform 卡 §7.1）：无码 → 40301。Controller 方法或类级标注。 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequirePrivilege {

  /** 权限码，形如 &lt;资源单数&gt;-&lt;动作&gt;。 */
  String value();
}
