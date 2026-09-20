package net.zentao.task.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.activity.ActivityRepository;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.project.api.ExecutionApi;
import net.zentao.requirement.api.StoryApi;
import net.zentao.task.api.EffortView;
import net.zentao.task.domain.Effort;
import net.zentao.task.domain.EffortRepository;
import net.zentao.task.domain.Task;
import net.zentao.task.domain.TaskHoursPolicy;
import net.zentao.task.domain.TaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登记工时（task 卡 §4/§5/§8）：consumedHours 累计、leftHours 覆写语义（null 不动 / 传 0 触发自动完成）；
 * wait 任务登记后自动 doing；closed/cancel 拒绝 42202；workDate 晚于今天 → 42201；
 * 动态流顺序：effortRecorded →（自动 started/finished）；父子联动与需求 stage 联动同事务。
 */
@Component
public class RecordEffortHandler {

  private final EffortRepository effortRepository;
  private final TaskRepository taskRepository;
  private final ExecutionApi executionApi;
  private final WorkflowEngine engine;
  private final ActivityRecorder activityRecorder;
  private final TaskParentRollup parentRollup;
  private final StoryApi storyApi;

  public RecordEffortHandler(EffortRepository effortRepository, TaskRepository taskRepository,
      ExecutionApi executionApi, WorkflowEngine engine, ActivityRecorder activityRecorder,
      TaskParentRollup parentRollup, StoryApi storyApi) {
    this.effortRepository = effortRepository;
    this.taskRepository = taskRepository;
    this.executionApi = executionApi;
    this.engine = engine;
    this.activityRecorder = activityRecorder;
    this.parentRollup = parentRollup;
    this.storyApi = storyApi;
  }

  /** 登记请求体（contract：EffortCreateRequest）；account 恒当前账号，不接受代登记（§7）。 */
  public record EffortCreateRequest(LocalDate workDate,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal consumedHours,
      BigDecimal leftHours, String work) {}

  @Transactional
  public EffortView handle(SessionPrincipal actor, long taskId, EffortCreateRequest command) {
    Task task = TaskGuard.requireWritable(taskRepository, executionApi, actor, taskId);
    if (task.isParent()) {
      throw ApiException.stateActionNotAllowed("父任务不支持登记工时（工时由子任务合计承载）。");
    }
    if ("closed".equals(task.status()) || "cancel".equals(task.status())) {
      throw ApiException.stateActionNotAllowed("任务已关闭或取消，不可登记工时。");
    }
    if (command == null || command.consumedHours() == null) {
      throw ApiException.validation(Map.of("consumedHours", "required"));
    }
    TaskFields.hours("consumedHours", command.consumedHours(), true);
    TaskFields.hours("leftHours", command.leftHours(), false);
    LocalDate workDate = command.workDate() == null ? LocalDate.now() : command.workDate();
    TaskFields.workDate(workDate);

    Effort effort = effortRepository.insert(new Effort(0, task.id(), task.executionId(), task.projectId(),
        actor.account(), workDate, command.consumedHours(), command.leftHours(), command.work(), actor.account(),
        Instant.now(), null, null));

    task.setField("consumedHours", task.consumedHours().add(command.consumedHours()));
    if (command.leftHours() != null) {
      task.setField("leftHours", command.leftHours());
    }
    activityRecorder.record(actor.account(), "task", task.id(), "effortRecorded",
        List.of(new ActivityRepository.DetailField("consumedHours", null, command.consumedHours().toPlainString())),
        command.work());

    autoTransition(actor, task, command.leftHours());
    task.markUpdatedBy(actor.account());
    taskRepository.update(task).orElseThrow();
    if (task.parentId() != 0) {
      parentRollup.rollUp(task.parentId(), actor.account());
    }
    return EffortView.of(effort);
  }

  /** 登记引发的状态联动：wait 且传了正的剩余 → 自动 doing；传 leftHours=0 → 自动完成（动态流顺序 effortRecorded 先）。 */
  private void autoTransition(SessionPrincipal actor, Task task, BigDecimal leftHours) {
    boolean transitioned = false;
    if ("wait".equals(task.status()) && leftHours != null && leftHours.signum() > 0) {
      task.setField("startedAt", Instant.now());
      if (task.leftHours() == null) {
        task.setField("leftHours", TaskHoursPolicy.initialLeftHours(task.estimateHours(), task.consumedHours()));
      }
      engine.fire(new TaskWorkflowTargets.TaskTarget(task, actor.account()), "start", null);
      transitioned = true;
    }
    if (leftHours != null && leftHours.signum() == 0
        && !List.of("done", "closed", "cancel").contains(task.status())) {
      if (task.startedAt() == null) {
        task.setField("startedAt", Instant.now());
      }
      task.setField("finishedBy", actor.account());
      task.setField("finishedAt", Instant.now());
      engine.fire(new TaskWorkflowTargets.TaskTarget(task, actor.account()), "finish", null);
      transitioned = true;
    }
    if (transitioned && task.storyId() != 0) {
      storyApi.advanceStage(task.storyId(),
          TaskParentRollup.progressOf(taskRepository.findActiveByStory(task.storyId())));
    }
  }
}
