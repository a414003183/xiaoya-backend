package net.zentao.workspace.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.PrivilegeChecker;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.workspace.api.TodoBatchResult;
import org.springframework.stereotype.Component;

/**
 * 待办批量端点（workspace 卡 §5 POST /todos/batch）：body 含 items → 批量创建（≤50）；
 * body 含 ids+action+params → 批量动作。逐项套用本域守卫与 §7 数据权限，逐项成败互不影响
 * （故本类不加事务，让每个 item 走各自 handler 的事务）。
 * 动作码在请求级校验（无码 40301，P2 product 批量先例）；逐项失败原因走 `error` 列（`<code>:<message>`）。
 */
@Component
public class BatchTodoHandler {

  private static final int MAX_ITEMS = 50;

  private final CreateTodoHandler createHandler;
  private final TodoActionHandler actionHandler;
  private final PrivilegeChecker checker;

  public BatchTodoHandler(CreateTodoHandler createHandler, TodoActionHandler actionHandler,
      PrivilegeChecker checker) {
    this.createHandler = createHandler;
    this.actionHandler = actionHandler;
    this.checker = checker;
  }

  public record TodoBatchRequest(
      List<CreateTodoHandler.TodoCreateRequest> items, List<Long> ids, String action, Map<String, Object> params) {}

  public TodoBatchResult handle(SessionPrincipal actor, TodoBatchRequest body) {
    if (body.items() != null) {
      return createBatch(actor, body.items());
    }
    return actionBatch(actor, body);
  }

  private TodoBatchResult createBatch(SessionPrincipal actor, List<CreateTodoHandler.TodoCreateRequest> items) {
    if (items.isEmpty()) {
      throw ApiException.validation(Map.of("items", "required"));
    }
    if (items.size() > MAX_ITEMS) {
      throw ApiException.badRequest("批量创建最多 " + MAX_ITEMS + " 条。");
    }
    if (!checker.hasPrivilege(actor, "todo-create")) {
      throw ApiException.forbidden("无权限：todo-create");
    }
    List<TodoBatchResult.Item> results = new ArrayList<>();
    for (int index = 0; index < items.size(); index++) {
      try {
        results.add(TodoBatchResult.Item.created(index, createHandler.handle(actor, items.get(index)).id()));
      } catch (ApiException e) {
        results.add(TodoBatchResult.Item.failed(index, e.errorCode().code() + ":" + e.getMessage()));
      }
    }
    return new TodoBatchResult(results);
  }

  private TodoBatchResult actionBatch(SessionPrincipal actor, TodoBatchRequest body) {
    if (body.ids() == null || body.ids().isEmpty()) {
      throw ApiException.validation(Map.of("ids", "required"));
    }
    String action = body.action() == null ? "" : body.action();
    String code = switch (action) {
      case "start" -> "todo-start";
      case "finish" -> "todo-finish";
      case "activate" -> "todo-activate";
      case "close" -> "todo-close";
      case "assign" -> "todo-assign";
      default -> null;
    };
    if (code == null) {
      throw ApiException.badRequest("不支持的批量动作：" + action);
    }
    if (!checker.hasPrivilege(actor, code)) {
      throw ApiException.forbidden("无权限：" + code);
    }
    String comment = text(body.params(), "comment");
    String assignee = text(body.params(), "assignee");
    List<TodoBatchResult.Item> results = new ArrayList<>();
    for (Long id : body.ids()) {
      try {
        switch (action) {
          case "start" -> actionHandler.start(actor, id);
          case "finish" -> actionHandler.finish(actor, id);
          case "activate" -> actionHandler.activate(actor, id, comment);
          case "close" -> actionHandler.close(actor, id, comment);
          default -> actionHandler.assign(actor, id, assignee, comment);
        }
        results.add(TodoBatchResult.Item.acted(id));
      } catch (ApiException e) {
        results.add(TodoBatchResult.Item.actionFailed(id, e.errorCode().code() + ":" + e.getMessage()));
      }
    }
    return new TodoBatchResult(results);
  }

  private static String text(Map<String, Object> raw, String key) {
    if (raw == null) {
      return null;
    }
    Object value = raw.get(key);
    return value == null ? null : String.valueOf(value);
  }
}
