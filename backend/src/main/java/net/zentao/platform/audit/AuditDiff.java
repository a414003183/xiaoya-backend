package net.zentao.platform.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级 diff 采集（ADR-004 决策 2 的注解轨，T04 建框架、T10 逐类挂到 Handler）。
 *
 * <p>执行前按 {@code objectType} 向 {@link AuditSnapshotRegistry} 取一次旧值，执行后再取一次，比对
 * {@code keyFields} 生成 {@code changes}（敏感字段掩码 {@code ***}）。旧值来源是各域自己注册的
 * 快照函数——框架不猜 Repository，域之间不互相依赖。
 *
 * <p>取不到旧值/新值（未注册 provider、对象不存在、函数抛错）时**只记审计行、不记 diff**——
 * 审计缺失比审计报错便宜，但绝不因为 diff 失败而打断业务写。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditDiff {

  /** 快照注册表里的对象类型（如 account、product）。 */
  String objectType();

  /** 要比对的字段；留空 = provider 给出的全部字段。 */
  String[] keyFields() default {};

  /** 对象 id 所在的路由变量名；留空 = 用横切推导出的「以 Id 结尾」的那个路由变量。 */
  String idParam() default "";
}
