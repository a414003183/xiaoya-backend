package net.zentao.requirement.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * 批量创建需求（requirement 卡 §5：≤50 条逐条校验，部分成功，响应逐项 {index, ok, id, error}）。
 * 本类不加事务：逐条提交各走 CreateStoryHandler 的独立事务，一条失败不影响其余。
 */
@Component
public class StoryBatchCreateHandler {

  private static final int MAX_ITEMS = 50;

  private final CreateStoryHandler createHandler;
  private final MessageResolver messages;

  public StoryBatchCreateHandler(CreateStoryHandler createHandler,
      MessageResolver messages) {
    this.createHandler = createHandler;
    this.messages = messages;
  }

  public record ResultItem(int index, boolean ok, Long id, String error) {}

  public record StoryBatchCreateResult(List<ResultItem> results) {}

  public StoryBatchCreateResult handle(SessionPrincipal actor, long productId,
      List<CreateStoryHandler.StoryCreateRequest> items) {
    if (items == null || items.isEmpty()) {
      throw ApiException.validation(Map.of("items", "required"));
    }
    if (items.size() > MAX_ITEMS) {
      throw ApiException.validation(Map.of("items", "tooMany"));
    }
    List<ResultItem> results = new ArrayList<>();
    int index = 0;
    for (CreateStoryHandler.StoryCreateRequest item : items) {
      try {
        results.add(new ResultItem(index, true, createHandler.handle(actor, productId, item).id(), null));
      } catch (ApiException e) {
        results.add(new ResultItem(index, false, null, e.errorCode().code() + ":" + messages.forRequest(e)));
      }
      index += 1;
    }
    return new StoryBatchCreateResult(results);
  }
}
