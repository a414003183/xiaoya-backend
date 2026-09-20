package net.zentao.requirement.app;

import java.util.List;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.requirement.api.StoryView;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 提交评审（requirement 卡 §4）：reviewers 传入则覆盖；needNotReview=true 由 YAML 分派直达 active。 */
@Component
public class SubmitReviewStoryHandler {

  private final StoryRepository repository;
  private final ProductApi productApi;
  private final AccountApi accountApi;
  private final WorkflowEngine engine;

  public SubmitReviewStoryHandler(StoryRepository repository, ProductApi productApi, AccountApi accountApi,
      WorkflowEngine engine) {
    this.repository = repository;
    this.productApi = productApi;
    this.accountApi = accountApi;
    this.engine = engine;
  }

  public record StorySubmitReviewRequest(List<String> reviewers, String comment) {}

  @Transactional
  public StoryView handle(SessionPrincipal actor, long storyId, StorySubmitReviewRequest command) {
    StorySubmitReviewRequest request = command == null
        ? new StorySubmitReviewRequest(null, null)
        : command;
    return StoryActionSupport.fire(actor, repository, productApi, engine, storyId, "submit-review",
        request.comment(), story -> {
          if (request.reviewers() != null) {
            StoryFields.validateReviewers(request.reviewers(), accountApi);
            story.reviewBy(request.reviewers());
          }
        });
  }
}
