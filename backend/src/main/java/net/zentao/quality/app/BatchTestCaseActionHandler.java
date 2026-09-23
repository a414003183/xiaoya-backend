package net.zentao.quality.app;

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
 * 用例批量动作（quality 卡 §5：action ∈ review|edit，逐项结果部分成功）。
 * review 走 ReviewTestCaseHandler（仅 wait），edit 走 UpdateTestCaseHandler 逐行乐观锁（params.rows，A-03）。
 */
@Component
public class BatchTestCaseActionHandler {

  private final ReviewTestCaseHandler reviewHandler;
  private final UpdateTestCaseHandler updateHandler;
  private final PrivilegeChecker checker;
  private final MessageResolver messages;

  public BatchTestCaseActionHandler(ReviewTestCaseHandler reviewHandler, UpdateTestCaseHandler updateHandler,
      PrivilegeChecker checker,
      MessageResolver messages) {
    this.reviewHandler = reviewHandler;
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
      case "review" -> "testcase-review";
      case "edit" -> "testcase-edit";
      default -> null;
    };
    if (code == null) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "batch.action.unsupported", action);
    }
    if (!checker.hasPrivilege(actor, code)) {
      throw ApiException.keyed(ErrorCode.FORBIDDEN, "error.privilege.missing", code);
    }
    Map<String, Object> params = command.params() == null ? Map.of() : command.params();
    // edit 逐行自带 id + lockVersion（A-03），review 循环对象取 command.ids()
    if ("edit".equals(action)) {
      return editRows(actor, params);
    }
    List<BatchActionResult.Item> results = new ArrayList<>();
    for (Long id : command.ids()) {
      try {
        reviewHandler.handle(actor, id,
            new ReviewTestCaseHandler.TestCaseReviewRequest(text(params, "result"), text(params, "comment")));
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
    for (Map<String, Object> row : BatchBugActionHandler.rowsOf(params)) {
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

  private static UpdateTestCaseHandler.TestCaseUpdateRequest toUpdate(Map<String, Object> params) {
    Object steps = params.get("steps");
    return new UpdateTestCaseHandler.TestCaseUpdateRequest(
        text(params, "title"), text(params, "precondition"), text(params, "keywords"), number(params, "priority"),
        text(params, "type"), texts(params, "stage"), longNumber(params, "categoryId"),
        longNumber(params, "storyId"), text(params, "status"),
        steps == null ? null : ((List<?>) steps).stream()
            .map(BatchTestCaseActionHandler::stepOf).toList(),
        number(params, "lockVersion"));
  }

  private static TestCaseFields.StepInput stepOf(Object raw) {
    if (raw instanceof Map<?, ?> map) {
      Object sort = map.get("sort");
      Object description = map.get("description");
      Object expects = map.get("expects");
      return new TestCaseFields.StepInput(
          sort == null ? null : Integer.valueOf(String.valueOf(sort)),
          description == null ? null : String.valueOf(description),
          expects == null ? null : String.valueOf(expects));
    }
    throw ApiException.validation(Map.of("steps", "invalid"));
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
}
