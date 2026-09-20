package net.zentao.project.app;

import net.zentao.platform.workflow.WorkflowTarget;
import net.zentao.project.domain.Project;

/**
 * 项目域状态机作用对象适配（platform 卡 §4.3）：域聚合不实现 platform 接口，
 * 由 app 层在 fire 前套上操作人上下文；执行型三别（sprint/stage/kanban）共用一台 execution 机。
 */
final class ProjectWorkflowTargets {

  private ProjectWorkflowTargets() {}

  record ProjectTarget(Project project, String actor) implements WorkflowTarget {

    @Override
    public String objectType() {
      if (project.isProgram()) {
        return "program";
      }
      return project.isExecution() ? "execution" : "project";
    }

    @Override
    public long objectId() {
      return project.id();
    }

    @Override
    public String status() {
      return project.status();
    }

    @Override
    public void applyStatus(String status) {
      project.applyStatus(status);
    }

    @Override
    public Object field(String name) {
      return switch (name) {
        case "title" -> project.name();
        case "createdBy" -> project.createdBy();
        case "pm" -> project.pm();
        default -> null;
      };
    }

    @Override
    public void setField(String name, Object value) {
      project.setField(name, value);
    }
  }
}
