package net.zentao.quality.app;

import net.zentao.org.api.AccountApi;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.api.BugView;
import net.zentao.quality.domain.Bug;
import net.zentao.quality.domain.BugRepository;
import net.zentao.requirement.api.StoryApi;
import net.zentao.requirement.api.StoryView;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 解决 Bug（quality 卡 §4.1）：resolution 守卫 42201（duplicate/fixed 联动）；
 * resolution=tostory 经 {@link StoryApi#createFromBug} 建需求并回填 storyId（同事务）。
 */
@Component
public class ResolveBugHandler {

  private final BugRepository repository;
  private final ProductApi productApi;
  private final AccountApi accountApi;
  private final WorkflowEngine engine;
  private final StoryApi storyApi;

  public ResolveBugHandler(BugRepository repository, ProductApi productApi, AccountApi accountApi,
      WorkflowEngine engine, StoryApi storyApi) {
    this.repository = repository;
    this.productApi = productApi;
    this.accountApi = accountApi;
    this.engine = engine;
    this.storyApi = storyApi;
  }

  public record BugResolveRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"bydesign", "duplicate",
          "external", "fixed", "notrepro", "postponed", "tostory", "willnotfix"}) String resolution,
      String resolvedBuild, Long duplicateOfId, String assignee, String comment) {}

  @Transactional
  public BugView handle(SessionPrincipal actor, long bugId, BugResolveRequest command) {
    if (command.assignee() != null) {
      BugFields.validateAssignee(command.assignee(), accountApi);
    }
    return BugActionSupport.fire(actor, repository, productApi, engine, bugId, "resolve", command.comment(),
        bug -> {
          BugFields.validateResolve(command.resolution(), command.resolvedBuild(), command.duplicateOfId(),
              bug.productId(), repository);
          Long storyId = null;
          if ("tostory".equals(command.resolution())) {
            StoryView story = storyApi.createFromBug(actor, new StoryApi.BugSource(
                bug.productId(), bug.branchId(), bug.title(), bug.priority(), bug.steps()));
            storyId = story.id();
          }
          bug.resolveBy(command.resolution(), command.resolvedBuild(), command.duplicateOfId(),
              command.assignee(), storyId);
        });
  }
}
