package net.zentao.requirement.app;

import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.requirement.api.StoryView;
import net.zentao.requirement.domain.Story;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 变更完成（requirement 卡 §4：changing → changed；请求体即变更内容，须有实际修改）。 */
@Component
public class ChangeDoneStoryHandler {

  private final StoryRepository repository;
  private final ProductApi productApi;
  private final AccountApi accountApi;
  private final WorkflowEngine engine;

  public ChangeDoneStoryHandler(StoryRepository repository, ProductApi productApi, AccountApi accountApi,
      WorkflowEngine engine) {
    this.repository = repository;
    this.productApi = productApi;
    this.accountApi = accountApi;
    this.engine = engine;
  }

  @Transactional
  public StoryView handle(SessionPrincipal actor, long storyId, UpdateStoryHandler.StoryUpdateRequest command) {
    if (command == null || isEmpty(command)) {
      throw ApiException.validation(Map.of("title", "noChange"));
    }
    Story story = StoryActionSupport.require(actor, repository, productApi, storyId);
    StoryFields.validate(command.title(), command.keywords(), null, command.priority(), command.estimateHours(),
        null, null, null, command.notifyAccounts(), accountApi);
    StoryFields.validateReferences(story.productId(), null, command.linkedStoryIds(), repository);
    StoryFields.validateParentChange(story, command.parentId(), repository);
    return StoryActionSupport.fire(actor, repository, productApi, engine, storyId, "change-done", null,
        target -> target.update(command.title(), command.keywords(), command.priority(), command.estimateHours(),
            command.categoryId(), command.planId(), command.parentId(), command.description(),
            command.notifyAccounts(), command.linkedStoryIds()));
  }

  private static boolean isEmpty(UpdateStoryHandler.StoryUpdateRequest command) {
    return command.title() == null && command.keywords() == null && command.priority() == null
        && command.estimateHours() == null && command.categoryId() == null && command.planId() == null
        && command.parentId() == null && command.description() == null && command.notifyAccounts() == null
        && command.linkedStoryIds() == null;
  }
}
