package net.zentao.quality.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.quality.api.BatchCreateResult;
import org.springframework.stereotype.Component;

/** 批量创建用例（quality 卡 §5：≤50 条逐条校验，部分成功）。逐条独立事务。 */
@Component
public class BatchCreateTestCaseHandler {

  private static final int MAX_ITEMS = 50;

  private final CreateTestCaseHandler createHandler;
  private final MessageResolver messages;

  public BatchCreateTestCaseHandler(CreateTestCaseHandler createHandler,
      MessageResolver messages) {
    this.createHandler = createHandler;
    this.messages = messages;
  }

  public BatchCreateResult handle(SessionPrincipal actor, long productId, long libraryId,
      List<CreateTestCaseHandler.TestCaseCreateRequest> items) {
    if (items == null || items.isEmpty()) {
      throw ApiException.validation(Map.of("items", "required"));
    }
    if (items.size() > MAX_ITEMS) {
      throw ApiException.validation(Map.of("items", "tooMany"));
    }
    List<BatchCreateResult.Item> results = new ArrayList<>();
    int index = 0;
    for (CreateTestCaseHandler.TestCaseCreateRequest item : items) {
      try {
        results.add(BatchCreateResult.Item.ok(index, createHandler.handle(actor, productId, libraryId, item).id()));
      } catch (ApiException e) {
        results.add(BatchCreateResult.Item.failed(index, e.errorCode().code() + ":" + messages.forRequest(e)));
      }
      index += 1;
    }
    return new BatchCreateResult(results);
  }
}
