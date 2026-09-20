package net.zentao.task.app;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.DataScope;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.api.ExecutionApi;
import net.zentao.task.api.EffortView;
import net.zentao.task.domain.Effort;
import net.zentao.task.domain.EffortRepository;
import net.zentao.task.domain.Task;
import net.zentao.task.domain.TaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 编辑工时（task 卡 §5/§8）：仅 workDate/consumedHours/leftHours/work 可改；编辑限本人或超管（40301）；回算同事务。 */
@Component
public class EditEffortHandler {

  private final EffortRepository effortRepository;
  private final TaskRepository taskRepository;
  private final ExecutionApi executionApi;
  private final DataScope dataScope;
  private final EffortRecalculator recalculator;
  private final ActivityRecorder activityRecorder;

  public EditEffortHandler(EffortRepository effortRepository, TaskRepository taskRepository,
      ExecutionApi executionApi, DataScope dataScope, EffortRecalculator recalculator,
      ActivityRecorder activityRecorder) {
    this.effortRepository = effortRepository;
    this.taskRepository = taskRepository;
    this.executionApi = executionApi;
    this.dataScope = dataScope;
    this.recalculator = recalculator;
    this.activityRecorder = activityRecorder;
  }

  /** 编辑请求体（contract：EffortUpdateRequest）；工时不设 lockVersion（§3b）。 */
  public record EffortUpdateRequest(LocalDate workDate, BigDecimal consumedHours, BigDecimal leftHours, String work) {}

  @Transactional
  public EffortView handle(SessionPrincipal actor, long effortId, EffortUpdateRequest command) {
    Effort effort = effortRepository.findActiveById(effortId).orElseThrow(() -> ApiException.notFound("工时"));
    Task task = TaskGuard.requireReadable(taskRepository, executionApi, actor, effort.taskId());
    if (!dataScope.isSuperAdmin(actor) && !actor.account().equals(effort.account())) {
      throw ApiException.forbidden("只能编辑本人登记的工时。");
    }
    TaskFields.hours("consumedHours", command == null ? null : command.consumedHours(), true);
    TaskFields.hours("leftHours", command == null ? null : command.leftHours(), false);
    TaskFields.workDate(command == null ? null : command.workDate());
    effort.update(command == null ? null : command.workDate(), command == null ? null : command.consumedHours(),
        command == null ? null : command.leftHours(), command == null ? null : command.work());
    effort.markUpdatedBy(actor.account());
    Effort saved = effortRepository.update(effort).orElseThrow();
    recalculator.recalculate(task, actor);
    activityRecorder.record(actor.account(), "task", task.id(), "effortEdited",
        List.of(new net.zentao.platform.activity.ActivityRepository.DetailField("consumedHours",
            null, String.valueOf(saved.consumedHours()))), saved.work());
    return EffortView.of(saved);
  }
}
