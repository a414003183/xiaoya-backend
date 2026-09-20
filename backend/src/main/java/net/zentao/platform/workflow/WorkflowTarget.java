package net.zentao.platform.workflow;

/**
 * 状态机作用对象（platform 卡 §4.3）：各域聚合根或 PO 实现本接口后交给 {@link WorkflowEngine#fire}。
 *
 * <p>约定：{@link #objectType()} = workflow YAML 的 domain = 动态流/通知的资源单数名（story/plan/release…）；
 * 请求体驱动的字段（closedReason/assignee/reviewers…）由命令处理器在 fire 之前写入对象，
 * 声明式守卫（{@link StateMachine.Guard}）因此在同一处读得到；动作备注经 fire 的 comment 参数注入，
 * 以字段名 {@code comment} 参与守卫与动态流。
 */
public interface WorkflowTarget {

  /** 资源单数名，如 story/plan/release/account。 */
  String objectType();

  long objectId();

  /** 操作人账号（写入动态流 actor）。 */
  String actor();

  String status();

  void applyStatus(String status);

  /** 守卫/分派表达式与 notify 接收人表达式的取值入口；未知字段返回 null。 */
  Object field(String name);

  /** fieldSet 副作用入口（@now/@actor/@null 已由引擎解析）。 */
  void setField(String name, Object value);
}
