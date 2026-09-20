package net.zentao.task.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.project.api.ExecutionApi;
import net.zentao.requirement.api.StoryApi;
import net.zentao.task.api.TaskView;
import net.zentao.task.domain.Effort;
import net.zentao.task.domain.EffortRepository;
import net.zentao.task.domain.Task;
import net.zentao.task.domain.TaskHoursPolicy;
import net.zentao.task.domain.TaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务八动作（task 卡 §4/§5）：迁移、守卫与 activity/notify 副作用由 workflow/task.yml 声明；
 * 请求体驱动字段（assignee/leftHours/closedReason/本次消耗/时间戳）在 fire 前写入对象，
 * 自动落工时（start/finish 的本次消耗）、父子联动与需求 stage 联动同事务执行。
 */
@Component
public class TaskActionHandler {

  private final TaskRepository repository;
  private final EffortRepository effortRepository;
  private final ExecutionApi executionApi;
  private final AccountApi accountApi;
  private final StoryApi storyApi;
  private final WorkflowEngine engine;
  private final TaskParentRollup parentRollup;
  private final TaskQueryService queryService;

  public TaskActionHandler(TaskRepository repository, EffortRepository effortRepository, ExecutionApi executionApi,
      AccountApi accountApi, StoryApi storyApi, WorkflowEngine engine, TaskParentRollup parentRollup,
      TaskQueryService queryService) {
    this.repository = repository;
    this.effortRepository = effortRepository;
    this.executionApi = executionApi;
    this.accountApi = accountApi;
    this.storyApi = storyApi;
    this.engine = engine;
    this.parentRollup = parentRollup;
    this.queryService = queryService;
  }

  public record TaskStartRequest(String assignee, BigDecimal consumedHours, BigDecimal leftHours, String comment) {}

  public record TaskFinishRequest(BigDecimal consumedHours, BigDecimal leftHours, Instant startedAt,
      Instant finishedAt, String work, String comment) {}

  public record TaskCloseRequest(
      @Schema(allowableValues = {"cancel", "done"}) String closedReason, String comment) {}

  public record TaskActivateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal leftHours,
      String assignee, String comment) {}

  public record TaskAssignRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String assignee,
      BigDecimal leftHours, String comment) {}

  @Transactional
  public TaskView start(SessionPrincipal actor, long taskId, TaskStartRequest body) {
    Task task = writable(actor, taskId);
    requireNotParent(task, "start");
    BigDecimal spent = hours(body == null ? null : body.consumedHours());
    if (body != null && body.consumedHours() != null) {
      TaskFields.hours("consumedHours", spent, true);
    }
    if (body != null && body.assignee() != null) {
      requireAccount(body.assignee());
      task.setField("assignee", body.assignee());
    }
    task.setField("startedAt", Instant.now());
    if (spent.signum() > 0) {
      task.setField("consumedHours", task.consumedHours().add(spent));
    }
    if (body != null && body.leftHours() != null) {
      TaskFields.hours("leftHours", body.leftHours(), false);
      task.setField("leftHours", body.leftHours());
    } else if (task.leftHours() == null) {
      task.setField("leftHours", TaskHoursPolicy.initialLeftHours(task.estimateHours(), task.consumedHours()));
    }
    fire(actor, task, "start", body == null ? null : body.comment());
    if (spent.signum() > 0) {
      recordEffort(actor, task, spent, null, null);
    }
    return finishAction(actor, task, "start");
  }

  @Transactional
  public TaskView finish(SessionPrincipal actor, long taskId, TaskFinishRequest body) {
    Task task = writable(actor, taskId);
    requireNotParent(task, "finish");
    BigDecimal spent = hours(body == null ? null : body.consumedHours());
    if (body != null && body.consumedHours() != null) {
      TaskFields.hours("consumedHours", spent, true);
    }
    Instant finishedAt = body == null || body.finishedAt() == null ? Instant.now() : body.finishedAt();
    Instant startedAt = body == null || body.startedAt() == null ? task.startedAt() : body.startedAt();
    if (startedAt != null && startedAt.isAfter(finishedAt)) {
      throw ApiException.validation(Map.of("finishedAt", "beforeStartedAt"));
    }
    task.setField("startedAt", startedAt == null ? Instant.now() : startedAt);
    task.setField("consumedHours", task.consumedHours().add(spent));
    if (body != null && body.leftHours() != null) {
      TaskFields.hours("leftHours", body.leftHours(), false);
      task.setField("leftHours", body.leftHours());
    } else {
      task.setField("leftHours", BigDecimal.ZERO);
    }
    task.setField("finishedBy", actor.account());
    task.setField("finishedAt", finishedAt);
    fire(actor, task, "finish", body == null ? null : body.comment());
    if (spent.signum() > 0) {
      String work = body != null && body.work() != null && !body.work().isBlank()
          ? body.work() : (body == null ? null : body.comment());
      recordEffort(actor, task, spent, BigDecimal.ZERO, work);
    }
    return finishAction(actor, task, "finish");
  }

  @Transactional
  public TaskView pause(SessionPrincipal actor, long taskId, String comment) {
    return simple(actor, taskId, "pause", comment);
  }

  @Transactional
  public TaskView resume(SessionPrincipal actor, long taskId, String comment) {
    return simple(actor, taskId, "resume", comment);
  }

  @Transactional
  public TaskView cancel(SessionPrincipal actor, long taskId, String comment) {
    Task task = writable(actor, taskId);
    task.setField("canceledBy", actor.account());
    task.setField("canceledAt", Instant.now());
    task.setField("assignee", task.createdBy());
    fire(actor, task, "cancel", comment);
    return finishAction(actor, task, "cancel");
  }

  @Transactional
  public TaskView close(SessionPrincipal actor, long taskId, TaskCloseRequest body) {
    Task task = writable(actor, taskId);
    String reason = body == null ? null : body.closedReason();
    if (reason == null || reason.isBlank()) {
      if ("done".equals(task.status())) {
        reason = "done";
      } else if ("cancel".equals(task.status())) {
        reason = "cancel";
      } else {
        throw ApiException.validation(Map.of("closedReason", "required"));
      }
    }
    if (!List.of("done", "cancel").contains(reason)) {
      throw ApiException.validation(Map.of("closedReason", "invalid"));
    }
    task.setField("closedReason", reason);
    task.setField("assignee", null);
    fire(actor, task, "close", body == null ? null : body.comment());
    return finishAction(actor, task, "close");
  }

  @Transactional
  public TaskView activate(SessionPrincipal actor, long taskId, TaskActivateRequest body) {
    if (body == null || body.leftHours() == null) {
      throw ApiException.validation(Map.of("leftHours", "required"));
    }
    TaskFields.hours("leftHours", body.leftHours(), true);
    Task task = writable(actor, taskId);
    if (body.assignee() != null) {
      requireAccount(body.assignee());
      task.setField("assignee", body.assignee());
    }
    task.setField("leftHours", body.leftHours());
    task.setField("finishedBy", null);
    task.setField("finishedAt", null);
    task.setField("canceledBy", null);
    task.setField("canceledAt", null);
    task.setField("closedBy", null);
    task.setField("closedAt", null);
    task.setField("closedReason", null);
    task.setField("activatedAt", Instant.now());
    fire(actor, task, "activate", body.comment());
    return finishAction(actor, task, "activate");
  }

  @Transactional
  public TaskView assign(SessionPrincipal actor, long taskId, TaskAssignRequest body) {
    if (body == null || body.assignee() == null || body.assignee().isBlank()) {
      throw ApiException.validation(Map.of("assignee", "required"));
    }
    requireAccount(body.assignee());
    Task task = writable(actor, taskId);
    task.setField("assignee", body.assignee());
    if (body.leftHours() != null) {
      TaskFields.hours("leftHours", body.leftHours(), false);
      task.setField("leftHours", body.leftHours());
    }
    fire(actor, task, "assign", body.comment());
    return finishAction(actor, task, "assign");
  }

  private TaskView simple(SessionPrincipal actor, long taskId, String action, String comment) {
    Task task = writable(actor, taskId);
    fire(actor, task, action, comment);
    return finishAction(actor, task, action);
  }

  /** 迁移 + 落库；副作用（activity/notify/fieldSet）由引擎按 YAML 顺序在同事务内执行。 */
  private void fire(SessionPrincipal actor, Task task, String action, String comment) {
    engine.fire(new TaskWorkflowTargets.TaskTarget(task, actor.account()), action, comment);
    task.markUpdatedBy(actor.account());
    repository.update(task).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
  }

  /** 动作收尾：父子联动 + 需求 stage 联动（start/finish/activate）+ 返回最新视图。 */
  private TaskView finishAction(SessionPrincipal actor, Task task, String action) {
    if (task.parentId() != 0) {
      parentRollup.rollUp(task.parentId(), actor.account());
    }
    if (task.storyId() != 0 && List.of("start", "finish", "activate").contains(action)) {
      List<Task> storyTasks = repository.findActiveByStory(task.storyId());
      storyApi.advanceStage(task.storyId(), TaskParentRollup.progressOf(storyTasks));
    }
    Task saved = repository.findActiveById(task.id()).orElseThrow();
    return queryService.viewOf(actor, saved);
  }

  /** 本次消耗自动落工时（§4：finish 落 leftHours=0、workDate=finishedAt 当天、work 缺省取 comment）。 */
  private void recordEffort(SessionPrincipal actor, Task task, BigDecimal spent, BigDecimal leftHours, String work) {
    LocalDate workDate = task.finishedAt() == null ? LocalDate.now()
        : task.finishedAt().atZone(ZoneId.systemDefault()).toLocalDate();
    effortRepository.insert(new Effort(0, task.id(), task.executionId(), task.projectId(), actor.account(),
        workDate, spent, leftHours, work, actor.account(), Instant.now(), null, null));
  }

  /** 父任务只允许 edit/assign/pause/resume/cancel/close/activate 与建子任务（task 卡 §4/§8 → 42202）。 */
  private static void requireNotParent(Task task, String action) {
    if (task.isParent()) {
      throw ApiException.stateActionNotAllowed("父任务不支持该动作：" + action);
    }
  }

  private Task writable(SessionPrincipal actor, long taskId) {
    return TaskGuard.requireWritable(repository, executionApi, actor, taskId);
  }

  private void requireAccount(String account) {
    if (!accountApi.missingAccounts(List.of(account)).isEmpty()) {
      throw ApiException.validation(Map.of("assignee", "notFound"));
    }
  }

  private static BigDecimal hours(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
