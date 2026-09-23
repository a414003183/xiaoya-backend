package net.zentao.quality.app;

import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.api.BugView;
import net.zentao.quality.domain.BugRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 确认 Bug（quality 卡 §4.1）：active 且 confirmed=false；已确认再 confirm → 42202；可顺带改派。 */
@Component
public class ConfirmBugHandler {

  private final BugRepository repository;
  private final ProductApi productApi;
  private final AccountApi accountApi;
  private final WorkflowEngine engine;

  public ConfirmBugHandler(BugRepository repository, ProductApi productApi, AccountApi accountApi,
      WorkflowEngine engine) {
    this.repository = repository;
    this.productApi = productApi;
    this.accountApi = accountApi;
    this.engine = engine;
  }

  public record BugConfirmRequest(String assignee, String comment) {}

  @Transactional
  public BugView handle(SessionPrincipal actor, long bugId, BugConfirmRequest command) {
    if (command.assignee() != null) {
      BugFields.validateAssignee(command.assignee(), accountApi);
    }
    return BugActionSupport.fire(actor, repository, productApi, engine, bugId, "confirm", command.comment(),
        bug -> {
          if (bug.confirmed()) {
            throw ApiException.keyed(ErrorCode.STATE_ACTION_NOT_ALLOWED, "bug.state.alreadyConfirmed");
          }
          bug.confirmBy(command.assignee());
        });
  }
}
