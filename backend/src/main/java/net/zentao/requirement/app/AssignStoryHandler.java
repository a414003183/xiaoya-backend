package net.zentao.requirement.app;

import io.swagger.v3.oas.annotations.media.Schema;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.requirement.api.StoryView;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 指派需求（requirement 卡 §4：任意非 closed 状态，落 assignee + assignedAt，通知 assignee）。 */
@Component
public class AssignStoryHandler {

  private final StoryRepository repository;
  private final ProductApi productApi;
  private final AccountApi accountApi;
  private final WorkflowEngine engine;

  public AssignStoryHandler(StoryRepository repository, ProductApi productApi, AccountApi accountApi,
      WorkflowEngine engine) {
    this.repository = repository;
    this.productApi = productApi;
    this.accountApi = accountApi;
    this.engine = engine;
  }

  public record StoryAssignRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String assignee,
      String comment) {}

  @Transactional
  public StoryView handle(SessionPrincipal actor, long storyId, StoryAssignRequest command) {
    String assignee = command == null ? null : command.assignee();
    StoryFields.validateAssignee(assignee, accountApi);
    return StoryActionSupport.fire(actor, repository, productApi, engine, storyId, "assign",
        command == null ? null : command.comment(), story -> story.assignTo(assignee));
  }
}
