package net.zentao.task.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.activity.ActivityQueryService;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditDiff;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.BatchActionRequest;
import net.zentao.platform.web.BatchActionResult;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.task.api.EffortView;
import net.zentao.task.api.TaskView;
import net.zentao.task.app.BatchTaskActionHandler;
import net.zentao.task.app.DeleteTaskHandler;
import net.zentao.task.app.EditEffortHandler;
import net.zentao.task.app.EffortQueryService;
import net.zentao.task.app.RecordEffortHandler;
import net.zentao.task.app.TaskActionHandler;
import net.zentao.task.app.TaskActionHandler.TaskActivateRequest;
import net.zentao.task.app.TaskActionHandler.TaskAssignRequest;
import net.zentao.task.app.TaskActionHandler.TaskCloseRequest;
import net.zentao.task.app.TaskActionHandler.TaskFinishRequest;
import net.zentao.task.app.TaskActionHandler.TaskStartRequest;
import net.zentao.task.app.TaskQueryService;
import net.zentao.task.app.UpdateTaskHandler;
import net.zentao.task.app.UpdateTaskHandler.TaskUpdateRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 任务端点（task 卡 §5：详情/PATCH/八动作/批量/工时明细与登记/动态流）。 */
@RestController
@RequestMapping("/api/v1")
public class TaskController {

  private final TaskQueryService queryService;
  private final UpdateTaskHandler updateHandler;
  private final TaskActionHandler actionHandler;
  private final BatchTaskActionHandler batchHandler;
  private final EffortQueryService effortQueryService;
  private final RecordEffortHandler recordEffortHandler;
  private final DeleteTaskHandler deleteHandler;
  private final ActivityQueryService activityQueryService;
  private final SessionResolver resolver;

  public TaskController(TaskQueryService queryService, UpdateTaskHandler updateHandler,
      TaskActionHandler actionHandler, BatchTaskActionHandler batchHandler, EffortQueryService effortQueryService,
      RecordEffortHandler recordEffortHandler, DeleteTaskHandler deleteHandler,
      ActivityQueryService activityQueryService, SessionResolver resolver) {
    this.queryService = queryService;
    this.updateHandler = updateHandler;
    this.actionHandler = actionHandler;
    this.batchHandler = batchHandler;
    this.effortQueryService = effortQueryService;
    this.recordEffortHandler = recordEffortHandler;
    this.deleteHandler = deleteHandler;
    this.activityQueryService = activityQueryService;
    this.resolver = resolver;
  }

  @GetMapping("/tasks/{taskId}")
  @Operation(operationId = "getTask")
  @RequirePrivilege("task-view")
  public DataEnvelope<TaskView> detail(@PathVariable long taskId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.detail(resolver.resolve(request), taskId));
  }

  @PatchMapping("/tasks/{taskId}")
  @Operation(operationId = "updateTask")
  @RequirePrivilege("task-edit")
  @Audit(action = "task-update", objectType = "task")
  @AuditDiff(objectType = "task")
  public DataEnvelope<TaskView> update(@PathVariable long taskId, @RequestBody TaskUpdateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(updateHandler.handle(resolver.resolve(request), taskId, body));
  }

  @PostMapping("/tasks/{taskId}/start")
  @Operation(operationId = "startTask")
  @RequirePrivilege("task-start")
  @Audit(action = "task-start", objectType = "task")
  @AuditDiff(objectType = "task")
  public DataEnvelope<TaskView> start(@PathVariable long taskId,
      @RequestBody(required = false) TaskStartRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.start(resolver.resolve(request), taskId, body));
  }

  @PostMapping("/tasks/{taskId}/finish")
  @Operation(operationId = "finishTask")
  @RequirePrivilege("task-finish")
  @Audit(action = "task-finish", objectType = "task")
  @AuditDiff(objectType = "task")
  public DataEnvelope<TaskView> finish(@PathVariable long taskId,
      @RequestBody(required = false) TaskFinishRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.finish(resolver.resolve(request), taskId, body));
  }

  @PostMapping("/tasks/{taskId}/pause")
  @Operation(operationId = "pauseTask")
  @RequirePrivilege("task-pause")
  @Audit(action = "task-pause", objectType = "task")
  @AuditDiff(objectType = "task")
  public DataEnvelope<TaskView> pause(@PathVariable long taskId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.pause(resolver.resolve(request), taskId, comment(body)));
  }

  @PostMapping("/tasks/{taskId}/resume")
  @Operation(operationId = "resumeTask")
  @RequirePrivilege("task-resume")
  @Audit(action = "task-resume", objectType = "task")
  @AuditDiff(objectType = "task")
  public DataEnvelope<TaskView> resume(@PathVariable long taskId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.resume(resolver.resolve(request), taskId, comment(body)));
  }

  @PostMapping("/tasks/{taskId}/cancel")
  @Operation(operationId = "cancelTask")
  @RequirePrivilege("task-cancel")
  @Audit(action = "task-cancel", objectType = "task")
  @AuditDiff(objectType = "task")
  public DataEnvelope<TaskView> cancel(@PathVariable long taskId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.cancel(resolver.resolve(request), taskId, comment(body)));
  }

  @PostMapping("/tasks/{taskId}/close")
  @Operation(operationId = "closeTask")
  @RequirePrivilege("task-close")
  @Audit(action = "task-close", objectType = "task")
  @AuditDiff(objectType = "task")
  public DataEnvelope<TaskView> close(@PathVariable long taskId,
      @RequestBody(required = false) TaskCloseRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.close(resolver.resolve(request), taskId, body));
  }

  @PostMapping("/tasks/{taskId}/activate")
  @Operation(operationId = "activateTask")
  @RequirePrivilege("task-activate")
  @Audit(action = "task-activate", objectType = "task")
  @AuditDiff(objectType = "task")
  public DataEnvelope<TaskView> activate(@PathVariable long taskId,
      @RequestBody(required = false) TaskActivateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.activate(resolver.resolve(request), taskId, body));
  }

  @PostMapping("/tasks/{taskId}/assign")
  @Operation(operationId = "assignTask")
  @RequirePrivilege("task-assign")
  @Audit(action = "task-assign", objectType = "task")
  @AuditDiff(objectType = "task")
  public DataEnvelope<TaskView> assign(@PathVariable long taskId,
      @RequestBody(required = false) TaskAssignRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.assign(resolver.resolve(request), taskId, body));
  }

  @DeleteMapping("/tasks/{taskId}")
  @Operation(operationId = "deleteTask")
  @RequirePrivilege("task-delete")
  @Audit(action = "task-delete", objectType = "task")
  @AuditDiff(objectType = "task")
  public DataEnvelope<Void> delete(@PathVariable long taskId, HttpServletRequest request) {
    deleteHandler.handle(resolver.resolve(request), taskId);
    return DataEnvelope.empty();
  }

  @PostMapping("/tasks/batch")
  @Operation(operationId = "batchTasks")
  @Audit(action = "batch-operation", objectType = "task")
  public DataEnvelope<BatchActionResult> batch(@RequestBody BatchActionRequest body, HttpServletRequest request) {
    return DataEnvelope.of(batchHandler.handle(resolver.resolve(request), body));
  }

  @GetMapping("/tasks/{taskId}/efforts")
  @Operation(operationId = "listTaskEfforts")
  @RequirePrivilege("task-view")
  public DataEnvelope<EffortQueryService.EffortList> efforts(@PathVariable long taskId,
      HttpServletRequest request) {
    return DataEnvelope.of(effortQueryService.page(resolver.resolve(request), taskId, request.getParameterMap()));
  }

  @PostMapping("/tasks/{taskId}/efforts")
  @Operation(operationId = "createEffort")
  @RequirePrivilege("task-effort")
  @Audit(action = "effort-create", objectType = "effort")
  public DataEnvelope<EffortView> createEffort(@PathVariable long taskId,
      @RequestBody RecordEffortHandler.EffortCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(recordEffortHandler.handle(resolver.resolve(request), taskId, body));
  }

  @GetMapping("/tasks/{taskId}/activities")
  @Operation(operationId = "listTaskActivities")
  @RequirePrivilege("task-view")
  public DataEnvelope<ActivityQueryService.ActivityList> activities(@PathVariable long taskId,
      @RequestParam(required = false) Integer limit, @RequestParam(required = false) Long beforeId,
      HttpServletRequest request) {
    queryService.requireReadable(resolver.resolve(request), taskId);
    return DataEnvelope.of(activityQueryService.list("task", taskId, null, limit, beforeId));
  }

  private static String comment(CommentRequest body) {
    return body == null ? null : body.comment();
  }
}
