package net.zentao.quality.app;

import java.time.LocalDate;
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
import net.zentao.platform.web.CommentRequest;
import org.springframework.stereotype.Component;

/**
 * Bug 批量动作（quality 卡 §5：action ∈ confirm|resolve|activate|close|assign|edit，逐项结果部分成功）。
 * edit 走 UpdateBugHandler 逐行乐观锁（params.rows，A-03），其余复用单动作处理器（P3 ⑪ 同款）。
 */
@Component
public class BatchBugActionHandler {

  private final ConfirmBugHandler confirmHandler;
  private final ResolveBugHandler resolveHandler;
  private final ActivateBugHandler activateHandler;
  private final CloseBugHandler closeHandler;
  private final AssignBugHandler assignHandler;
  private final UpdateBugHandler updateHandler;
  private final PrivilegeChecker checker;
  private final MessageResolver messages;

  public BatchBugActionHandler(ConfirmBugHandler confirmHandler, ResolveBugHandler resolveHandler,
      ActivateBugHandler activateHandler, CloseBugHandler closeHandler, AssignBugHandler assignHandler,
      UpdateBugHandler updateHandler, PrivilegeChecker checker,
      MessageResolver messages) {
    this.confirmHandler = confirmHandler;
    this.resolveHandler = resolveHandler;
    this.activateHandler = activateHandler;
    this.closeHandler = closeHandler;
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
      case "confirm" -> "bug-confirm";
      case "resolve" -> "bug-resolve";
      case "activate" -> "bug-activate";
      case "close" -> "bug-close";
      case "assign" -> "bug-assign";
      case "edit" -> "bug-edit";
      default -> null;
    };
    if (code == null) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "batch.action.unsupported", action);
    }
    if (!checker.hasPrivilege(actor, code)) {
      throw ApiException.keyed(ErrorCode.FORBIDDEN, "error.privilege.missing", code);
    }
    Map<String, Object> params = command.params() == null ? Map.of() : command.params();
    // edit 逐行自带 id + lockVersion（A-03），其余动作统一字段值、循环对象取 command.ids()
    if ("edit".equals(action)) {
      return editRows(actor, params);
    }
    List<BatchActionResult.Item> results = new ArrayList<>();
    for (Long id : command.ids()) {
      try {
        switch (action) {
          case "confirm" -> confirmHandler.handle(actor, id,
              new ConfirmBugHandler.BugConfirmRequest(text(params, "assignee"), text(params, "comment")));
          case "resolve" -> resolveHandler.handle(actor, id, new ResolveBugHandler.BugResolveRequest(
              text(params, "resolution"), text(params, "resolvedBuild"), longNumber(params, "duplicateOfId"),
              text(params, "assignee"), text(params, "comment")));
          case "activate" -> activateHandler.handle(actor, id, new ActivateBugHandler.BugActivateRequest(
              text(params, "openedBuilds"), text(params, "assignee"), text(params, "comment")));
          case "close" -> closeHandler.handle(actor, id,
              new CommentRequest(text(params, "comment")));
          case "assign" -> assignHandler.handle(actor, id,
              new AssignBugHandler.BugAssignRequest(text(params, "assignee"), text(params, "comment")));
          default -> throw ApiException.keyed(ErrorCode.BAD_REQUEST, "batch.action.unsupported", action);
        }
        results.add(BatchActionResult.ok(id));
      } catch (ApiException e) {
        results.add(BatchActionResult.failed(id, e.errorCode().code() + ":" + messages.forRequest(e)));
      }
    }
    return new BatchActionResult(results);
  }

  /** action=edit：{@code params.rows = [{id, lockVersion, …可编辑字段}]} 逐行应用（A-03 定案，契约 BatchActionRequest）。 */
  private BatchActionResult editRows(SessionPrincipal actor, Map<String, Object> params) {
    List<BatchActionResult.Item> results = new ArrayList<>();
    for (Map<String, Object> row : rowsOf(params)) {
      Long id = longNumber(row, "id");
      try {
        if (id == null) {
          throw ApiException.keyed(ErrorCode.BAD_REQUEST, "error.param.missing");
        }
        if (row.get("lockVersion") == null) {
          // 缺 lockVersion 报 40901 是既定口径（错误码冻结；缺参报锁冲突的怪味归 T66 统一时再议）
          throw ApiException.keyed(ErrorCode.LOCK_CONFLICT, "error.param.missing");
        }
        updateHandler.handle(actor, id, toUpdate(row));
        results.add(BatchActionResult.ok(id));
      } catch (ApiException e) {
        results.add(BatchActionResult.failed(id == null ? 0 : id, e.errorCode().code() + ":" + messages.forRequest(e)));
      }
    }
    return new BatchActionResult(results);
  }

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> rowsOf(Map<String, Object> params) {
    Object raw = params.get("rows");
    if (!(raw instanceof List<?> list)) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "bug.batch.editRowsRequired");
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        throw ApiException.keyed(ErrorCode.BAD_REQUEST, "bug.batch.rowObjectRequired");
      }
      rows.add((Map<String, Object>) map);
    }
    return rows;
  }

  private static UpdateBugHandler.BugUpdateRequest toUpdate(Map<String, Object> params) {
    Object deadline = params.get("deadline");
    return new UpdateBugHandler.BugUpdateRequest(
        text(params, "title"), text(params, "keywords"), number(params, "severity"), number(params, "priority"),
        text(params, "type"), text(params, "os"), text(params, "browser"), text(params, "steps"),
        text(params, "openedBuilds"), longNumber(params, "categoryId"), longNumber(params, "executionId"),
        longNumber(params, "planId"), longNumber(params, "storyId"), longNumber(params, "taskId"),
        longNumber(params, "testCaseId"),
        deadline == null ? null : LocalDate.parse(String.valueOf(deadline)), longIds(params, "relatedBugIds"),
        texts(params, "notifyAccounts"), number(params, "lockVersion"));
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
