package net.zentao.task.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.PrivilegeChecker;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.web.BatchActionRequest;
import net.zentao.platform.web.BatchActionResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 任务批量动作（task 卡 §5：action ∈ edit|assign|start|pause|resume|cancel|close，逐项结果部分成功）。
 * finish/activate 表单个体差异大，不进批量；动作码在请求级校验（无码 40301），逐项成败走 error 列。
 * 本类不加事务：逐项走对应处理器的独立事务，一条失败不影响其余。
 */
@Component
public class BatchTaskActionHandler {

  private final TaskActionHandler actionHandler;
  private final UpdateTaskHandler updateHandler;
  private final PrivilegeChecker checker;
  private final JsonMapper jsonMapper;

  public BatchTaskActionHandler(TaskActionHandler actionHandler, UpdateTaskHandler updateHandler,
      PrivilegeChecker checker, JsonMapper jsonMapper) {
    this.actionHandler = actionHandler;
    this.updateHandler = updateHandler;
    this.checker = checker;
    this.jsonMapper = jsonMapper;
  }

  public BatchActionResult handle(SessionPrincipal actor, BatchActionRequest command) {
    if (command.ids() == null || command.ids().isEmpty()) {
      throw ApiException.validation(Map.of("ids", "required"));
    }
    String action = command.action() == null ? "" : command.action();
    if (!List.of("edit", "assign", "start", "pause", "resume", "cancel", "close").contains(action)) {
      throw ApiException.badRequest("不支持的批量动作：" + action);
    }
    String code = "task-" + action;
    if (!checker.hasPrivilege(actor, code)) {
      throw ApiException.forbidden("无权限：" + code);
    }
    Map<String, Object> params = command.params();
    List<BatchActionResult.Item> results = new ArrayList<>();
    for (Long id : command.ids()) {
      try {
        apply(actor, id, action, params);
        results.add(BatchActionResult.ok(id));
      } catch (ApiException e) {
        results.add(BatchActionResult.failed(id, e.errorCode().code() + ":" + e.getMessage()));
      }
    }
    return new BatchActionResult(results);
  }

  private void apply(SessionPrincipal actor, long taskId, String action, Map<String, Object> params) {
    switch (action) {
      case "edit" -> updateHandler.handle(actor, taskId, convert(params, UpdateTaskHandler.TaskUpdateRequest.class));
      case "assign" -> actionHandler.assign(actor, taskId,
          convert(params, TaskActionHandler.TaskAssignRequest.class));
      case "start" -> actionHandler.start(actor, taskId, convert(params, TaskActionHandler.TaskStartRequest.class));
      case "close" -> actionHandler.close(actor, taskId, convert(params, TaskActionHandler.TaskCloseRequest.class));
      case "pause" -> actionHandler.pause(actor, taskId, comment(params));
      case "resume" -> actionHandler.resume(actor, taskId, comment(params));
      case "cancel" -> actionHandler.cancel(actor, taskId, comment(params));
      default -> throw ApiException.badRequest("不支持的批量动作：" + action);
    }
  }

  private String comment(Map<String, Object> params) {
    Object value = params == null ? null : params.get("comment");
    return value == null ? null : String.valueOf(value);
  }

  private <T> T convert(Map<String, Object> params, Class<T> type) {
    Map<String, Object> body = params == null ? Map.of() : params;
    try {
      return jsonMapper.convertValue(body, type);
    } catch (RuntimeException e) {
      throw ApiException.validation(Map.of("params", "invalid"));
    }
  }
}
