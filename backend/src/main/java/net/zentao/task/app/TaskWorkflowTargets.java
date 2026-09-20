package net.zentao.task.app;

import net.zentao.platform.workflow.WorkflowTarget;
import net.zentao.task.domain.Task;

/**
 * 任务状态机作用对象适配（platform 卡 §4.3）：域聚合不实现 platform 接口，
 * 由 app 层在 fire 前套上操作人上下文（与 product/project 同构）。
 */
final class TaskWorkflowTargets {

  private TaskWorkflowTargets() {}

  record TaskTarget(Task task, String actor) implements WorkflowTarget {

    @Override
    public String objectType() {
      return "task";
    }

    @Override
    public long objectId() {
      return task.id();
    }

    @Override
    public String status() {
      return task.status();
    }

    @Override
    public void applyStatus(String status) {
      task.applyStatus(status);
    }

    @Override
    public Object field(String name) {
      return switch (name) {
        case "title" -> task.title();
        case "createdBy" -> task.createdBy();
        case "assignee" -> task.assignee();
        case "notifyAccounts" -> task.notifyAccounts();
        case "isParent" -> task.isParent();
        case "consumedHours" -> task.consumedHours();
        case "leftHours" -> task.leftHours();
        case "closedReason" -> task.closedReason();
        default -> null;
      };
    }

    @Override
    public void setField(String name, Object value) {
      task.setField(name, value);
    }
  }
}
