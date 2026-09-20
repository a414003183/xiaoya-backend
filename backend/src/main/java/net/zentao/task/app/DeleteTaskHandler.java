package net.zentao.task.app;

import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.api.ExecutionApi;
import net.zentao.task.domain.Task;
import net.zentao.task.domain.TaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 删除任务（task 卡 §5 DELETE，A-07 落地）：软删 + 动态流 deleted。
 * 守卫：父任务存在未删子任务 → 42203；删的是子任务且删后父已无未删子任务 → 复位父 isParent=false
 * （§4「全部子任务删除 → 复位」，不产生动态流）。
 */
@Component
public class DeleteTaskHandler {

  private final TaskRepository repository;
  private final ExecutionApi executionApi;
  private final TaskParentRollup parentRollup;
  private final ActivityRecorder activityRecorder;

  public DeleteTaskHandler(TaskRepository repository, ExecutionApi executionApi, TaskParentRollup parentRollup,
      ActivityRecorder activityRecorder) {
    this.repository = repository;
    this.executionApi = executionApi;
    this.parentRollup = parentRollup;
    this.activityRecorder = activityRecorder;
  }

  @Transactional
  public void handle(SessionPrincipal actor, long taskId) {
    Task task = TaskGuard.requireWritable(repository, executionApi, actor, taskId);
    if (repository.countActiveChildren(taskId) > 0) {
      throw ApiException.guardNotSatisfied("父任务存在未删除的子任务，不能删除。");
    }
    repository.softDelete(taskId);
    activityRecorder.record(actor.account(), "task", taskId, "deleted", null, null);
    if (task.parentId() != 0) {
      parentRollup.releaseIfChildless(task.parentId());
    }
  }
}
