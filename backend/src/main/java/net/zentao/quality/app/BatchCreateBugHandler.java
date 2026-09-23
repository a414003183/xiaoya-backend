package net.zentao.quality.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.quality.api.BatchCreateResult;
import org.springframework.stereotype.Component;

/** 批量创建 Bug（quality 卡 §5：≤50 条逐条校验，部分成功）。逐条走 CreateBugHandler 独立事务。 */
@Component
public class BatchCreateBugHandler {

  private static final int MAX_ITEMS = 50;

  private final CreateBugHandler createHandler;
  private final MessageResolver messages;

  public BatchCreateBugHandler(CreateBugHandler createHandler,
      MessageResolver messages) {
    this.createHandler = createHandler;
    this.messages = messages;
  }

  public BatchCreateResult handle(SessionPrincipal actor, long productId,
      List<CreateBugHandler.BugCreateRequest> items) {
    if (items == null || items.isEmpty()) {
      throw ApiException.validation(Map.of("items", "required"));
    }
    if (items.size() > MAX_ITEMS) {
      throw ApiException.validation(Map.of("items", "tooMany"));
    }
    List<BatchCreateResult.Item> results = new ArrayList<>();
    int index = 0;
    for (CreateBugHandler.BugCreateRequest item : items) {
      try {
        results.add(BatchCreateResult.Item.ok(index, createHandler.handle(actor, productId, item).id()));
      } catch (ApiException e) {
        results.add(BatchCreateResult.Item.failed(index, e.errorCode().code() + ":" + messages.forRequest(e)));
      }
      index += 1;
    }
    return new BatchCreateResult(results);
  }
}
