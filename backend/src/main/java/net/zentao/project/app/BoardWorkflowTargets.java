package net.zentao.project.app;

import net.zentao.platform.workflow.WorkflowTarget;
import net.zentao.project.domain.Board;
import net.zentao.project.domain.BoardSpace;

/**
 * 看板域状态机作用对象适配（platform 卡 §4.3）：域聚合不实现 platform 接口，
 * 由 app 层在 fire 前套上操作人上下文。
 */
final class BoardWorkflowTargets {

  private BoardWorkflowTargets() {}

  record BoardSpaceTarget(BoardSpace space, String actor) implements WorkflowTarget {

    @Override
    public String objectType() {
      return "board_space";
    }

    @Override
    public long objectId() {
      return space.id();
    }

    @Override
    public String status() {
      return space.status();
    }

    @Override
    public void applyStatus(String status) {
      space.applyStatus(status);
    }

    /** board.yml 无守卫/通知表达式，取值入口无需供给字段。 */
    @Override
    public Object field(String name) {
      return null;
    }

    @Override
    public void setField(String name, Object value) {
      space.setField(name, value);
    }
  }

  record BoardTarget(Board board, String actor) implements WorkflowTarget {

    @Override
    public String objectType() {
      return "board";
    }

    @Override
    public long objectId() {
      return board.id();
    }

    @Override
    public String status() {
      return board.status();
    }

    @Override
    public void applyStatus(String status) {
      board.applyStatus(status);
    }

    /** board.yml 无守卫/通知表达式，取值入口无需供给字段。 */
    @Override
    public Object field(String name) {
      return null;
    }

    @Override
    public void setField(String name, Object value) {
      board.setField(name, value);
    }
  }
}
