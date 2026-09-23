package net.zentao.platform.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感读采集（VISION 事项 4 第 7 行「敏感数据查看」，T10 落地）：标在**读端点**（GET）上，
 * 每次查看落一行审计——查看人/时间/IP/UA 由横切采集，对象由路由变量推导，本注解声明
 * 「这次响应交付了哪些敏感字段」（进 {@code extra.fields}，审计才能回答「他看了什么」）。
 *
 * <p>分类仍由 {@link AuditCatalog} 决定：与 {@link Audit} 同挂（动作名与对象类型取 {@code @Audit}），
 * 域在目录里把该动作登记为 {@link AuditCategory#SENSITIVE}。只想声明字段、不想管分类的端点，
 * 动作名回落「GET + 路由模板」——那时必须显式登记该动作，否则按未登记回落 business。
 *
 * <p>原因（VISION 要求「查看人、对象ID、字段、原因、IP」）：取可选的 {@code ?reason=} 查询参数，
 * 未填则 extra 里不出现该键——UI 暂不强制填原因（T25/P5 再评）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditSensitive {

  /** 本次响应里被交付的敏感字段（如 mobile/email/idCard）；进 {@code extra.fields}。 */
  String[] fields();
}
