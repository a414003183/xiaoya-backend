package net.zentao.workspace.app;

import net.zentao.platform.workflow.WorkflowTarget;
import net.zentao.workspace.domain.Todo;

/** 待办状态机作用对象适配（platform 卡 §4.3）：聚合不实现 platform 接口，app 层套操作人上下文。 */
final class TodoWorkflowTargets {

  private TodoWorkflowTargets() {}

  /** assignToActor：assign 动作的「非本人」约束由 todo.yml 的 not-self 守卫裁决（与状态守卫同源）。 */
  record TodoTarget(Todo todo, String actor, boolean assignToActor) implements WorkflowTarget {

    TodoTarget(Todo todo, String actor) {
      this(todo, actor, false);
    }

    @Override
    public String objectType() {
      return "todo";
    }

    @Override
    public long objectId() {
      return todo.id();
    }

    @Override
    public String status() {
      return todo.status();
    }

    @Override
    public void applyStatus(String status) {
      todo.applyStatus(status);
    }

    @Override
    public Object field(String name) {
      return switch (name) {
        case "title" -> todo.title();
        case "createdBy" -> todo.createdBy();
        case "assignee" -> todo.assignee();
        case "assignToSelf" -> assignToActor;
        default -> null;
      };
    }

    @Override
    public void setField(String name, Object value) {
      todo.setField(name, value);
    }
  }
}
