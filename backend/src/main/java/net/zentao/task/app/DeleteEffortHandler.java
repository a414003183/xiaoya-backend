package net.zentao.task.app;

import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.DataScope;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.api.ExecutionApi;
import net.zentao.task.domain.Effort;
import net.zentao.task.domain.EffortRepository;
import net.zentao.task.domain.Task;
import net.zentao.task.domain.TaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 删除工时（task 卡 §5/§8）：软删 + 任务三件套回算同事务（回算失败 → 软删回滚）；
 * 删除限本人或超管（40301）；状态不回退。
 */
@Component
public class DeleteEffortHandler {

  private final EffortRepository effortRepository;
  private final TaskRepository taskRepository;
  private final ExecutionApi executionApi;
  private final DataScope dataScope;
  private final EffortRecalculator recalculator;
  private final ActivityRecorder activityRecorder;

  public DeleteEffortHandler(EffortRepository effortRepository, TaskRepository taskRepository,
      ExecutionApi executionApi, DataScope dataScope, EffortRecalculator recalculator,
      ActivityRecorder activityRecorder) {
    this.effortRepository = effortRepository;
    this.taskRepository = taskRepository;
    this.executionApi = executionApi;
    this.dataScope = dataScope;
    this.recalculator = recalculator;
    this.activityRecorder = activityRecorder;
  }

  @Transactional
  public void handle(SessionPrincipal actor, long effortId) {
    Effort effort = effortRepository.findActiveById(effortId).orElseThrow(() -> ApiException.notFound("工时"));
    Task task = TaskGuard.requireReadable(taskRepository, executionApi, actor, effort.taskId());
    if (!dataScope.isSuperAdmin(actor) && !actor.account().equals(effort.account())) {
      throw ApiException.forbidden("只能删除本人登记的工时。");
    }
    effortRepository.softDelete(effortId);
    recalculator.recalculate(task, actor);
    activityRecorder.record(actor.account(), "task", task.id(), "effortDeleted", null, effort.work());
  }
}
