package net.zentao.requirement.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.requirement.api.StoryView;
import net.zentao.requirement.domain.Story;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 部分更新需求（requirement 卡 §5 PATCH 白名单；lockVersion 不符 → 40901）。 */
@Component
public class UpdateStoryHandler {

  private final StoryRepository repository;
  private final ProductApi productApi;
  private final AccountApi accountApi;

  public UpdateStoryHandler(StoryRepository repository, ProductApi productApi, AccountApi accountApi) {
    this.repository = repository;
    this.productApi = productApi;
    this.accountApi = accountApi;
  }

  public record StoryUpdateRequest(
      String title, String keywords, Integer priority, BigDecimal estimateHours, Long categoryId, Long planId,
      Long parentId, String description, List<String> notifyAccounts, List<Long> linkedStoryIds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  @Transactional
  public StoryView handle(SessionPrincipal actor, long storyId, StoryUpdateRequest command) {
    Story story = StoryActionSupport.require(actor, repository, productApi, storyId);
    if (command.lockVersion() == null || command.lockVersion() != story.lockVersion()) {
      throw ApiException.lockConflict();
    }
    StoryFields.validate(command.title(), command.keywords(), null, command.priority(), command.estimateHours(),
        null, null, null, command.notifyAccounts(), accountApi);
    StoryFields.validateReferences(story.productId(), null, command.linkedStoryIds(), repository);
    StoryFields.validateParentChange(story, command.parentId(), repository);
    story.update(command.title(), command.keywords(), command.priority(), command.estimateHours(),
        command.categoryId(), command.planId(), command.parentId(), command.description(),
        command.notifyAccounts(), command.linkedStoryIds());
    story.markUpdatedBy(actor.account());
    return StoryView.of(StoryActionSupport.save(repository, story));
  }
}
