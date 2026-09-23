package net.zentao.task.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.task.api.TaskList;
import net.zentao.task.api.TaskView;
import net.zentao.task.app.BatchCreateTasksHandler;
import net.zentao.task.app.CreateTaskHandler;
import net.zentao.task.app.TaskQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 执行下的任务端点（task 卡 §5：列表/创建/批量创建）。 */
@RestController
@RequestMapping("/api/v1")
public class ExecutionTaskController {

  private final TaskQueryService queryService;
  private final CreateTaskHandler createHandler;
  private final BatchCreateTasksHandler batchCreateHandler;
  private final SessionResolver resolver;

  public ExecutionTaskController(TaskQueryService queryService, CreateTaskHandler createHandler,
      BatchCreateTasksHandler batchCreateHandler, SessionResolver resolver) {
    this.queryService = queryService;
    this.createHandler = createHandler;
    this.batchCreateHandler = batchCreateHandler;
    this.resolver = resolver;
  }

  @GetMapping("/executions/{executionId}/tasks")
  @Operation(operationId = "listExecutionTasks")
  @RequirePrivilege("task-view")
  public DataEnvelope<TaskList> list(@PathVariable long executionId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(resolver.resolve(request), executionId, request.getParameterMap()));
  }

  @PostMapping("/executions/{executionId}/tasks")
  @Operation(operationId = "createTask")
  @RequirePrivilege("task-create")
  @Audit(action = "task-create", objectType = "task")
  public DataEnvelope<TaskView> create(@PathVariable long executionId,
      @RequestBody CreateTaskHandler.TaskCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(createHandler.handle(resolver.resolve(request), executionId, body));
  }

  @PostMapping("/executions/{executionId}/tasks/batch")
  @Operation(operationId = "batchCreateTasks")
  @RequirePrivilege("task-create")
  @Audit(action = "batch-operation", objectType = "task")
  public DataEnvelope<BatchCreateTasksHandler.TaskBatchCreateResult> batchCreate(@PathVariable long executionId,
      @RequestBody BatchCreateTasksHandler.TaskBatchCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(batchCreateHandler.handle(resolver.resolve(request), executionId, body));
  }
}
