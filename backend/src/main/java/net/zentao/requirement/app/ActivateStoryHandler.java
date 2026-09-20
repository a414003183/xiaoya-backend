package net.zentao.requirement.app;

import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.requirement.api.StoryView;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 激活需求（requirement 卡 §4：closed → active，清 closedAt/closedBy/closedReason）。 */
@Component
public class ActivateStoryHandler {

  private final StoryRepository repository;
  private final ProductApi productApi;
  private final WorkflowEngine engine;

  public ActivateStoryHandler(StoryRepository repository, ProductApi productApi, WorkflowEngine engine) {
    this.repository = repository;
    this.productApi = productApi;
    this.engine = engine;
  }

  @Transactional
  public StoryView handle(SessionPrincipal actor, long storyId, CommentRequest command) {
    return StoryActionSupport.fire(actor, repository, productApi, engine, storyId, "activate",
        command == null ? null : command.comment());
  }
}
