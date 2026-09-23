package net.zentao.doc.app;

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
 * 文档批量动作（doc 卡 §5：action ∈ delete|move，≤50 条，逐项部分成功）。
 * 动作码在请求级校验（无码 40301）；逐项越权/失败走 error 列（`<code>:<message>`），逐项独立事务。
 */
@Component
public class BatchDocHandler {

  private static final int MAX_IDS = 50;

  private final DeleteDocHandler deleteHandler;
  private final MoveDocHandler moveHandler;
  private final PrivilegeChecker checker;
  private final MessageResolver messages;

  public BatchDocHandler(DeleteDocHandler deleteHandler, MoveDocHandler moveHandler, PrivilegeChecker checker,
      MessageResolver messages) {
    this.deleteHandler = deleteHandler;
    this.moveHandler = moveHandler;
    this.checker = checker;
    this.messages = messages;
  }

  public BatchActionResult handle(SessionPrincipal actor, BatchActionRequest command) {
    if (command.ids() == null || command.ids().isEmpty()) {
      throw ApiException.validation(Map.of("ids", "required"));
    }
    if (command.ids().size() > MAX_IDS) {
      throw ApiException.validation(Map.of("ids", "tooMany"));
    }
    String action = command.action() == null ? "" : command.action();
    String code = switch (action) {
      case "delete" -> "doc-delete";
      case "move" -> "doc-edit";
      default -> null;
    };
    if (code == null) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "batch.action.unsupported", action);
    }
    if (!checker.hasPrivilege(actor, code)) {
      throw ApiException.keyed(ErrorCode.FORBIDDEN, "error.privilege.missing", code);
    }
    MoveDocHandler.DocMoveRequest move = "move".equals(action) ? moveRequest(command.params()) : null;
    List<BatchActionResult.Item> results = new ArrayList<>();
    for (Long id : command.ids()) {
      try {
        if (move == null) {
          deleteHandler.handle(actor, id, null);
        } else {
          moveHandler.handle(actor, id, move);
        }
        results.add(BatchActionResult.ok(id));
      } catch (ApiException e) {
        results.add(BatchActionResult.failed(id, e.errorCode().code() + ":" + messages.forRequest(e)));
      }
    }
    return new BatchActionResult(results);
  }

  private static MoveDocHandler.DocMoveRequest moveRequest(Map<String, Object> params) {
    Map<String, Object> raw = params == null ? Map.of() : params;
    return new MoveDocHandler.DocMoveRequest(longValue(raw, "docSpaceId"), longValue(raw, "categoryId"),
        longValue(raw, "parentId"));
  }

  private static Long longValue(Map<String, Object> raw, String key) {
    Object value = raw.get(key);
    if (value instanceof Number number) {
      return number.longValue();
    }
    return value == null ? null : Long.valueOf(String.valueOf(value));
  }
}
