package net.zentao.workspace.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.DataScope;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.workspace.api.TodoView;
import net.zentao.workspace.domain.Todo;
import net.zentao.workspace.domain.TodoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 待办状态动作（workspace 卡 §4）：start/finish/activate/close/assign 走 workflow/todo.yml，
 * 状态字段联动（finished、closed、assigned 三组）由 YAML fieldSet 落；assign 的「非本人」在处理器前置判 42203。
 */
@Component
public class TodoActionHandler {

  private final TodoRepository repository;
  private final TodoTitleResolver titleResolver;
  private final WorkflowEngine engine;
  private final AccountApi accountApi;
  private final DataScope dataScope;

  public TodoActionHandler(TodoRepository repository, TodoTitleResolver titleResolver, WorkflowEngine engine,
      AccountApi accountApi, DataScope dataScope) {
    this.repository = repository;
    this.titleResolver = titleResolver;
    this.engine = engine;
    this.accountApi = accountApi;
    this.dataScope = dataScope;
  }

  /** 指派请求体（contract：TodoAssignRequest；assignee 非本人，否则 42203）。 */
  public record TodoAssignRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String assignee, String comment) {}

  @Transactional
  public TodoView start(SessionPrincipal actor, long todoId) {
    return fire(actor, todoId, "start", null);
  }

  @Transactional
  public TodoView finish(SessionPrincipal actor, long todoId) {
    return fire(actor, todoId, "finish", null);
  }

  @Transactional
  public TodoView activate(SessionPrincipal actor, long todoId, String comment) {
    return fire(actor, todoId, "activate", comment);
  }

  @Transactional
  public TodoView close(SessionPrincipal actor, long todoId, String comment) {
    return fire(actor, todoId, "close", comment);
  }

  @Transactional
  public TodoView assign(SessionPrincipal actor, long todoId, String assignee, String comment) {
    if (assignee == null || assignee.isBlank()) {
      throw ApiException.validation(java.util.Map.of("assignee", "required"));
    }
    String target = assignee.trim();
    List<String> missing = accountApi.missingAccounts(List.of(target));
    if (!missing.isEmpty()) {
      throw ApiException.validation(java.util.Map.of("assignee", "notFound"));
    }
    // 「非本人」由 todo.yml 的 not-self 守卫裁决（先过状态守卫，closed → 42202、本人 → 42203）
    boolean assignToSelf = target.equals(actor.account());
    return fire(actor, todoId, "assign", comment, assignToSelf ? null : target, assignToSelf);
  }

  /** 软删（A-07，workspace 卡 §5 DELETE）：仅创建人/负责人/超管可删，他人 → 40302。 */
  @Transactional
  public void delete(SessionPrincipal actor, long todoId) {
    Todo todo = repository.findActiveById(todoId).orElseThrow(() -> ApiException.notFound("待办"));
    TodoAccess.requireWritable(actor.account(), todo, dataScope.isSuperAdmin(actor));
    repository.softDelete(todoId);
  }

  private TodoView fire(SessionPrincipal actor, long todoId, String action, String comment) {
    return fire(actor, todoId, action, comment, null, false);
  }

  private TodoView fire(SessionPrincipal actor, long todoId, String action, String comment, String newAssignee,
      boolean assignToSelf) {
    Todo todo = repository.findActiveById(todoId).orElseThrow(() -> ApiException.notFound("待办"));
    TodoAccess.requireWritable(actor.account(), todo, dataScope.isSuperAdmin(actor));
    if (newAssignee != null) {
      todo.assignTo(newAssignee);
    }
    engine.fire(new TodoWorkflowTargets.TodoTarget(todo, actor.account(), assignToSelf), action, comment);
    todo.markUpdatedBy(actor.account());
    Todo saved = repository.update(todo)
        .orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
    return TodoView.of(saved, titleResolver.resolve(saved));
  }
}
