package net.zentao.requirement.app;

import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.requirement.api.StoryView;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 发起变更（requirement 卡 §4：active → changing）。 */
@Component
public class ChangeStoryHandler {

  private final StoryRepository repository;
  private final ProductApi productApi;
  private final WorkflowEngine engine;

  public ChangeStoryHandler(StoryRepository repository, ProductApi productApi, WorkflowEngine engine) {
    this.repository = repository;
    this.productApi = productApi;
    this.engine = engine;
  }

  @Transactional
  public StoryView handle(SessionPrincipal actor, long storyId) {
    return StoryActionSupport.fire(actor, repository, productApi, engine, storyId, "change", null);
  }
}
