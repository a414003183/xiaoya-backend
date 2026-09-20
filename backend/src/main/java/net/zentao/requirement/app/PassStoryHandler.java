package net.zentao.requirement.app;

import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.requirement.api.StoryView;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 评审通过（requirement 卡 §4：reviewing → active，通知 createdBy）。 */
@Component
public class PassStoryHandler {

  private final StoryRepository repository;
  private final ProductApi productApi;
  private final WorkflowEngine engine;

  public PassStoryHandler(StoryRepository repository, ProductApi productApi, WorkflowEngine engine) {
    this.repository = repository;
    this.productApi = productApi;
    this.engine = engine;
  }

  public record StoryPassRequest(String comment) {}

  @Transactional
  public StoryView handle(SessionPrincipal actor, long storyId, StoryPassRequest command) {
    return StoryActionSupport.fire(actor, repository, productApi, engine, storyId, "pass",
        command == null ? null : command.comment());
  }
}
