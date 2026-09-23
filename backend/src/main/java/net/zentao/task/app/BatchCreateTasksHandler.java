package net.zentao.task.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * 批量创建任务（task 卡 §5：≤50 条逐条校验，部分成功）。
 * 本类不加事务：逐条走 {@link CreateTaskHandler#handle} 的独立事务，一条失败不影响其余；
 * 子任务用 parentIndex 引用同行父行，父行失败则子行连带 error（§3）。
 */
@Component
public class BatchCreateTasksHandler {

  private static final int MAX_ITEMS = 50;

  private final CreateTaskHandler createHandler;
  private final MessageResolver messages;

  public BatchCreateTasksHandler(CreateTaskHandler createHandler,
      MessageResolver messages) {
    this.createHandler = createHandler;
    this.messages = messages;
  }

  public record TaskBatchCreateRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
      List<CreateTaskHandler.TaskCreateRequest> items) {}

  public record ResultItem(int index, boolean ok, Long id, String error) {}

  public record TaskBatchCreateResult(List<ResultItem> results) {}

  public TaskBatchCreateResult handle(SessionPrincipal actor, long executionId, TaskBatchCreateRequest command) {
    List<CreateTaskHandler.TaskCreateRequest> items = command == null ? null : command.items();
    if (items == null || items.isEmpty()) {
      throw ApiException.validation(Map.of("items", "required"));
    }
    if (items.size() > MAX_ITEMS) {
      throw ApiException.validation(Map.of("items", "tooMany"));
    }
    List<ResultItem> results = new ArrayList<>();
    Map<Integer, Long> createdIds = new HashMap<>();
    Map<Integer, String> failures = new HashMap<>();
    for (int index = 0; index < items.size(); index++) {
      CreateTaskHandler.TaskCreateRequest item = items.get(index);
      Long parentId = item.parentId();
      if (item.parentIndex() != null) {
        Long created = createdIds.get(item.parentIndex());
        if (created == null) {
          String parentError = failures.getOrDefault(item.parentIndex(), "42201:" + messages.forRequest("task.batch.parentRowMissing"));
          failures.put(index, parentError);
          results.add(new ResultItem(index, false, null, parentError));
          continue;
        }
        parentId = created;
      }
      try {
        long id = createHandler.create(actor, executionId, item, parentId).id();
        createdIds.put(index, id);
        results.add(new ResultItem(index, true, id, null));
      } catch (ApiException e) {
        failures.put(index, e.errorCode().code() + ":" + messages.forRequest(e));
        results.add(new ResultItem(index, false, null, e.errorCode().code() + ":" + messages.forRequest(e)));
      }
    }
    return new TaskBatchCreateResult(results);
  }
}
