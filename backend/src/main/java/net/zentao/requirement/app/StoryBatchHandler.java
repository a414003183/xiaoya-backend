package net.zentao.requirement.app;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.rbac.PrivilegeChecker;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.web.BatchActionRequest;
import net.zentao.platform.web.BatchActionResult;
import org.springframework.stereotype.Component;

/**
 * 需求批量动作（requirement 卡 §5：action ∈ close|activate|assign|edit，逐项结果部分成功）。
 * 动作码请求级校验（无码 40301）；逐项失败原因走 error 列（`<code>:<message>`）。
 */
@Component
public class StoryBatchHandler {

  private final CloseStoryHandler closeHandler;
  private final ActivateStoryHandler activateHandler;
  private final AssignStoryHandler assignHandler;
  private final UpdateStoryHandler updateHandler;
  private final PrivilegeChecker checker;
  private final MessageResolver messages;

  public StoryBatchHandler(CloseStoryHandler closeHandler, ActivateStoryHandler activateHandler,
      AssignStoryHandler assignHandler, UpdateStoryHandler updateHandler, PrivilegeChecker checker,
      MessageResolver messages) {
    this.closeHandler = closeHandler;
    this.activateHandler = activateHandler;
    this.assignHandler = assignHandler;
    this.updateHandler = updateHandler;
    this.checker = checker;
    this.messages = messages;
  }

  public BatchActionResult handle(SessionPrincipal actor, BatchActionRequest command) {
    if (command.ids() == null || command.ids().isEmpty()) {
      throw ApiException.validation(Map.of("ids", "required"));
    }
    String action = command.action() == null ? "" : command.action();
    String code = switch (action) {
      case "close" -> "story-close";
      case "activate" -> "story-activate";
      case "assign" -> "story-assign";
      case "edit" -> "story-edit";
      default -> null;
    };
    if (code == null) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "batch.action.unsupported", action);
    }
    if (!checker.hasPrivilege(actor, code)) {
      throw ApiException.keyed(ErrorCode.FORBIDDEN, "error.privilege.missing", code);
    }
    Map<String, Object> params = command.params() == null ? Map.of() : command.params();
    List<BatchActionResult.Item> results = new ArrayList<>();
    for (Long id : command.ids()) {
      try {
        switch (action) {
          case "close" -> closeHandler.handle(actor, id, new CloseStoryHandler.StoryCloseRequest(
              text(params, "closedReason"), longNumber(params, "duplicateOfId"), text(params, "comment")));
          case "activate" -> activateHandler.handle(actor, id, null);
          case "assign" -> assignHandler.handle(actor, id, new AssignStoryHandler.StoryAssignRequest(
              text(params, "assignee"), text(params, "comment")));
          default -> updateHandler.handle(actor, id, toUpdate(params));
        }
        results.add(BatchActionResult.ok(id));
      } catch (ApiException e) {
        results.add(BatchActionResult.failed(id, e.errorCode().code() + ":" + messages.forRequest(e)));
      }
    }
    return new BatchActionResult(results);
  }

  private static UpdateStoryHandler.StoryUpdateRequest toUpdate(Map<String, Object> params) {
    Object estimate = params.get("estimateHours");
    return new UpdateStoryHandler.StoryUpdateRequest(
        text(params, "title"), text(params, "keywords"), number(params, "priority"),
        estimate == null ? null : new BigDecimal(String.valueOf(estimate)), longNumber(params, "categoryId"),
        longNumber(params, "planId"), longNumber(params, "parentId"), text(params, "description"),
        texts(params, "notifyAccounts"), longIds(params, "linkedStoryIds"), number(params, "lockVersion"));
  }

  private static String text(Map<String, Object> params, String key) {
    Object value = params.get(key);
    return value == null ? null : String.valueOf(value);
  }

  private static Integer number(Map<String, Object> params, String key) {
    Object value = params.get(key);
    if (value instanceof Number raw) {
      return raw.intValue();
    }
    return value == null ? null : Integer.valueOf(String.valueOf(value));
  }

  private static Long longNumber(Map<String, Object> params, String key) {
    Object value = params.get(key);
    if (value instanceof Number raw) {
      return raw.longValue();
    }
    return value == null ? null : Long.valueOf(String.valueOf(value));
  }

  private static List<String> texts(Map<String, Object> params, String key) {
    Object value = params.get(key);
    return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : null;
  }

  private static List<Long> longIds(Map<String, Object> params, String key) {
    Object value = params.get(key);
    if (value instanceof List<?> list) {
      return list.stream().map(item -> Long.valueOf(String.valueOf(item))).toList();
    }
    return null;
  }
}
