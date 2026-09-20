package net.zentao.platform.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 写端点的审计动作名（B1 §H3）。不标也能审——动作由「HTTP 方法 + 路由」自动推导，
 * 标注只为把动作名写成业务语义（如 product-update），便于按 action 检索。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Audit {

  /** 动作名，形如 &lt;资源&gt;-&lt;动作&gt;。 */
  String action();

  /** 对象类型，形如 product；留空则由路由变量名推导（{@code {productId}} → product）。 */
  String objectType() default "";
}
