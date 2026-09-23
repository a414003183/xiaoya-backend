package net.zentao.workspace.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.activity.ActivityQueryService;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditDiff;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.workspace.api.TodoBatchResult;
import net.zentao.workspace.api.TodoList;
import net.zentao.workspace.api.TodoView;
import net.zentao.workspace.app.BatchTodoHandler;
import net.zentao.workspace.app.CreateTodoHandler;
import net.zentao.workspace.app.TodoActionHandler;
import net.zentao.workspace.app.TodoQueryService;
import net.zentao.workspace.app.UpdateTodoHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 待办端点（workspace 卡 §5 /todos 族 10 行）。 */
@RestController
@RequestMapping("/api/v1")
public class TodoController {

  private final TodoQueryService queryService;
  private final CreateTodoHandler createHandler;
  private final UpdateTodoHandler updateHandler;
  private final TodoActionHandler actionHandler;
  private final BatchTodoHandler batchHandler;
  private final ActivityQueryService activityQueryService;
  private final SessionResolver resolver;

  public TodoController(TodoQueryService queryService, CreateTodoHandler createHandler,
      UpdateTodoHandler updateHandler, TodoActionHandler actionHandler, BatchTodoHandler batchHandler,
      ActivityQueryService activityQueryService, SessionResolver resolver) {
    this.queryService = queryService;
    this.createHandler = createHandler;
    this.updateHandler = updateHandler;
    this.actionHandler = actionHandler;
    this.batchHandler = batchHandler;
    this.activityQueryService = activityQueryService;
    this.resolver = resolver;
  }

  @GetMapping("/todos")
  @Operation(operationId = "listTodos")
  @RequirePrivilege("todo-view")
  public DataEnvelope<TodoList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/todos")
  @Operation(operationId = "createTodo")
  @RequirePrivilege("todo-create")
  @Audit(action = "todo-create", objectType = "todo")
  public DataEnvelope<TodoView> create(@RequestBody CreateTodoHandler.TodoCreateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(createHandler.handle(resolver.resolve(request), body));
  }

  @PostMapping("/todos/batch")
  @Operation(operationId = "batchTodos")
  @Audit(action = "batch-operation", objectType = "todo")
  public DataEnvelope<TodoBatchResult> batch(@RequestBody BatchTodoHandler.TodoBatchRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(batchHandler.handle(resolver.resolve(request), body));
  }

  @GetMapping("/todos/{todoId}")
  @Operation(operationId = "getTodo")
  @RequirePrivilege("todo-view")
  public DataEnvelope<TodoView> detail(@PathVariable long todoId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.detail(resolver.resolve(request), todoId));
  }

  @PatchMapping("/todos/{todoId}")
  @Operation(operationId = "updateTodo")
  @RequirePrivilege("todo-edit")
  @Audit(action = "todo-update", objectType = "todo")
  @AuditDiff(objectType = "todo")
  public DataEnvelope<TodoView> update(@PathVariable long todoId,
      @RequestBody UpdateTodoHandler.TodoUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(updateHandler.handle(resolver.resolve(request), todoId, body));
  }

  @DeleteMapping("/todos/{todoId}")
  @Operation(operationId = "deleteTodo")
  @RequirePrivilege("todo-delete")
  @Audit(action = "todo-delete", objectType = "todo")
  @AuditDiff(objectType = "todo")
  public DataEnvelope<Void> delete(@PathVariable long todoId, HttpServletRequest request) {
    actionHandler.delete(resolver.resolve(request), todoId);
    return DataEnvelope.empty();
  }

  @GetMapping("/todos/{todoId}/activities")
  @Operation(operationId = "listTodoActivities")
  @RequirePrivilege("todo-view")
  public DataEnvelope<ActivityQueryService.ActivityList> activities(@PathVariable long todoId,
      @RequestParam(required = false) Integer limit, @RequestParam(required = false) Long beforeId,
      HttpServletRequest request) {
    // 详情闸门复用（40401/私有待办 40302），动态流 object_type=todo 与 workflow/todo.yml 写入口径一致
    queryService.detail(resolver.resolve(request), todoId);
    return DataEnvelope.of(activityQueryService.list("todo", todoId, null, limit, beforeId));
  }

  @PostMapping("/todos/{todoId}/start")
  @Operation(operationId = "startTodo")
  @RequirePrivilege("todo-start")
  @Audit(action = "todo-start", objectType = "todo")
  @AuditDiff(objectType = "todo")
  public DataEnvelope<TodoView> start(@PathVariable long todoId, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.start(resolver.resolve(request), todoId));
  }

  @PostMapping("/todos/{todoId}/finish")
  @Operation(operationId = "finishTodo")
  @RequirePrivilege("todo-finish")
  @Audit(action = "todo-finish", objectType = "todo")
  @AuditDiff(objectType = "todo")
  public DataEnvelope<TodoView> finish(@PathVariable long todoId, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.finish(resolver.resolve(request), todoId));
  }

  @PostMapping("/todos/{todoId}/activate")
  @Operation(operationId = "activateTodo")
  @RequirePrivilege("todo-activate")
  @Audit(action = "todo-activate", objectType = "todo")
  @AuditDiff(objectType = "todo")
  public DataEnvelope<TodoView> activate(@PathVariable long todoId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.activate(resolver.resolve(request), todoId, comment(body)));
  }

  @PostMapping("/todos/{todoId}/close")
  @Operation(operationId = "closeTodo")
  @RequirePrivilege("todo-close")
  @Audit(action = "todo-close", objectType = "todo")
  @AuditDiff(objectType = "todo")
  public DataEnvelope<TodoView> close(@PathVariable long todoId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.close(resolver.resolve(request), todoId, comment(body)));
  }

  @PostMapping("/todos/{todoId}/assign")
  @Operation(operationId = "assignTodo")
  @RequirePrivilege("todo-assign")
  @Audit(action = "todo-assign", objectType = "todo")
  @AuditDiff(objectType = "todo")
  public DataEnvelope<TodoView> assign(@PathVariable long todoId,
      @RequestBody TodoActionHandler.TodoAssignRequest body, HttpServletRequest request) {
    return DataEnvelope.of(
        actionHandler.assign(resolver.resolve(request), todoId, body.assignee(), body.comment()));
  }

  private static String comment(CommentRequest body) {
    return body == null ? null : body.comment();
  }
}
