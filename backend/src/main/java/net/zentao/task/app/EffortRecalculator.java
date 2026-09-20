package net.zentao.task.app;

import net.zentao.platform.session.SessionPrincipal;
import net.zentao.task.domain.EffortRepository;
import net.zentao.task.domain.Task;
import net.zentao.task.domain.TaskHoursPolicy;
import net.zentao.task.domain.TaskRepository;
import org.springframework.stereotype.Component;

/**
 * 工时三件套回算（task 卡 §4/§9）：编辑/删除工时后按未删流水重算任务
 * consumedHours（合计）与 leftHours（最近一条覆写值，无覆写保持原值）；状态不自动回退。
 */
@Component
public class EffortRecalculator {

  private final EffortRepository effortRepository;
  private final TaskRepository taskRepository;
  private final TaskParentRollup parentRollup;

  public EffortRecalculator(EffortRepository effortRepository, TaskRepository taskRepository,
      TaskParentRollup parentRollup) {
    this.effortRepository = effortRepository;
    this.taskRepository = taskRepository;
    this.parentRollup = parentRollup;
  }

  public void recalculate(Task task, SessionPrincipal actor) {
    TaskHoursPolicy.recalculatedLeft(effortRepository.findActiveByTask(task.id()))
        .ifPresent(left -> task.setField("leftHours", left));
    task.setField("consumedHours", TaskHoursPolicy.recalculatedConsumed(effortRepository.findActiveByTask(task.id())));
    task.markUpdatedBy(actor.account());
    taskRepository.update(task).orElseThrow();
    if (task.parentId() != 0) {
      parentRollup.rollUp(task.parentId(), actor.account());
    }
  }
}
