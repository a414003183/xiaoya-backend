package net.zentao.requirement.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.meta.FieldDefValidator;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.requirement.api.StoryApi;
import net.zentao.requirement.api.StoryView;
import net.zentao.requirement.domain.Story;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 创建需求（requirement 卡 §3/§5）：type 三型同表；parentId 须为同产品 epic；linkedStoryIds 须同产品。 */
@Component
public class CreateStoryHandler {

  private final StoryRepository repository;
  private final ProductApi productApi;
  private final AccountApi accountApi;
  private final ActivityRecorder activityRecorder;
  private final FieldDefValidator fieldDefValidator;

  public CreateStoryHandler(StoryRepository repository, ProductApi productApi, AccountApi accountApi,
      ActivityRecorder activityRecorder,
      FieldDefValidator fieldDefValidator) {
    this.fieldDefValidator = fieldDefValidator;
    this.repository = repository;
    this.productApi = productApi;
    this.accountApi = accountApi;
    this.activityRecorder = activityRecorder;
  }

  public record StoryCreateRequest(
      Long branchId, Long categoryId, Long planId, Long parentId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title, String keywords, String type,
      Integer priority, BigDecimal estimateHours, String source, String description, String assignee,
      List<String> reviewers, Boolean needNotReview, List<String> notifyAccounts, List<Long> linkedStoryIds,
      Map<String, Object> customFields) {}

  @Transactional
  public StoryView handle(SessionPrincipal actor, long productId, StoryCreateRequest command) {
    productApi.requireVisible(actor, productId);
    if (command.title() == null || command.title().trim().isEmpty()) {
      throw ApiException.validation(Map.of("title", "required"));
    }
    StoryFields.validate(command.title(), command.keywords(), command.type(), command.priority(),
        command.estimateHours(), command.source(), command.reviewers(), command.assignee(),
        command.notifyAccounts(), accountApi);
    StoryFields.validateReferences(productId, command.parentId(), command.linkedStoryIds(), repository);
    fieldDefValidator.validate("story", command.customFields() == null ? java.util.Map.of() : command.customFields(), true);
    Instant now = Instant.now();
    Story story = repository.insert(new Story(
        0,
        productId,
        command.branchId() == null ? 0 : command.branchId(),
        command.categoryId() == null ? 0 : command.categoryId(),
        command.planId(),
        command.parentId() == null || command.parentId() == 0 ? null : command.parentId(),
        command.title().trim(),
        command.keywords(),
        command.type() == null ? "story" : command.type(),
        "draft",
        command.priority() == null ? 3 : command.priority(),
        command.estimateHours(),
        command.source() == null ? "manual" : command.source(),
        command.description(),
        "wait",
        command.assignee(),
        null,
        command.reviewers(),
        command.needNotReview() != null && command.needNotReview(),
        command.notifyAccounts(),
        command.linkedStoryIds(),
        null,
        1,
        command.customFields(),
        actor.account(),
        now,
        null,
        null,
        null,
        null,
        null,
        0));
    activityRecorder.record(actor.account(), "story", story.id(), "created", null, null);
    return StoryView.of(story);
  }

  /** Bug 转需求（quality 卡 §4.1 tostory；签名 P4 落地定案，见 STATE 决策追加）。 */
  @Transactional
  public StoryView createFromBug(SessionPrincipal actor, StoryApi.BugSource source) {
    productApi.requireVisible(actor, source.productId());
    Instant now = Instant.now();
    Story story = repository.insert(new Story(
        0,
        source.productId(),
        source.branchId(),
        0,
        null,
        null,
        source.title().trim(),
        null,
        "story",
        "active",
        source.priority(),
        null,
        "bug",
        source.steps(),
        "wait",
        null,
        null,
        null,
        false,
        null,
        null,
        null,
        1,
        null,
        actor.account(),
        now,
        null,
        null,
        null,
        null,
        null,
        0));
    activityRecorder.record(actor.account(), "story", story.id(), "created", null, "Bug 转需求");
    return StoryView.of(story);
  }
}
