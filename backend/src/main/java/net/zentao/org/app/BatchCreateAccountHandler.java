package net.zentao.org.app;

import java.util.ArrayList;
import java.util.List;
import net.zentao.org.domain.Account;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/** 批量创建账号（org 卡 §5：≤50 条逐条校验，部分成功，响应逐项结果）。 */
@Component
public class BatchCreateAccountHandler {

  private final CreateAccountHandler createHandler;

  public BatchCreateAccountHandler(CreateAccountHandler createHandler) {
    this.createHandler = createHandler;
  }

  public record BatchResultItem(int index, boolean ok, Long id, String error) {}

  public record BatchResult(List<BatchResultItem> results) {}

  public BatchResult handle(SessionPrincipal actor, List<CreateAccountHandler.AccountCreateRequest> items) {
    if (items.size() > 50) {
      throw ApiException.validation(java.util.Map.of("items", "tooMany"));
    }
    List<BatchResultItem> results = new ArrayList<>();
    int index = 0;
    for (CreateAccountHandler.AccountCreateRequest command : items) {
      try {
        Account account = createHandler.handle(actor, command);
        results.add(new BatchResultItem(index, true, account.id(), null));
      } catch (ApiException e) {
        results.add(new BatchResultItem(index, false, null, e.errorCode().code() + ":" + e.getMessage()));
      }
      index += 1;
    }
    return new BatchResult(results);
  }
}
