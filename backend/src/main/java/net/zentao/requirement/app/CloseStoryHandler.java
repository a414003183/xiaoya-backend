package net.zentao.requirement.app;

import io.swagger.v3.oas.annotations.media.Schema;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.requirement.api.StoryView;
import net.zentao.requirement.domain.Story;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 关闭需求（requirement 卡 §4：closedReason 必填；=duplicate 时 duplicateOfId 必填且同产品）。 */
@Component
public class CloseStoryHandler {

  private final StoryRepository repository;
  private final ProductApi productApi;
  private final WorkflowEngine engine;

  public CloseStoryHandler(StoryRepository repository, ProductApi productApi, WorkflowEngine engine) {
    this.repository = repository;
    this.productApi = productApi;
    this.engine = engine;
  }

  public record StoryCloseRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
          allowableValues = {"done", "duplicate", "postponed", "rejected", "willnotfix"}) String closedReason,
      Long duplicateOfId, String comment) {}

  @Transactional
  public StoryView handle(SessionPrincipal actor, long storyId, StoryCloseRequest command) {
    Story story = StoryActionSupport.require(actor, repository, productApi, storyId);
    StoryFields.validateClose(command.closedReason(), command.duplicateOfId(), story.productId(), repository);
    return StoryActionSupport.fire(actor, repository, productApi, engine, storyId, "close", command.comment(),
        target -> target.markClosedReason(command.closedReason(), command.duplicateOfId()));
  }
}
