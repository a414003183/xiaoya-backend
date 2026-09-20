package net.zentao.quality.app;

import net.zentao.org.api.AccountApi;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.api.BugView;
import net.zentao.quality.domain.BugRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 指派 Bug（quality 卡 §4.1）：任意非 closed；assignedAt 由 fieldSet 落。 */
@Component
public class AssignBugHandler {

  private final BugRepository repository;
  private final ProductApi productApi;
  private final AccountApi accountApi;
  private final WorkflowEngine engine;

  public AssignBugHandler(BugRepository repository, ProductApi productApi, AccountApi accountApi,
      WorkflowEngine engine) {
    this.repository = repository;
    this.productApi = productApi;
    this.accountApi = accountApi;
    this.engine = engine;
  }

  public record BugAssignRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String assignee, String comment) {}

  @Transactional
  public BugView handle(SessionPrincipal actor, long bugId, BugAssignRequest command) {
    BugFields.validateAssignee(command.assignee(), accountApi);
    return BugActionSupport.fire(actor, repository, productApi, engine, bugId, "assign", command.comment(),
        bug -> bug.assignTo(command.assignee()));
  }
}
