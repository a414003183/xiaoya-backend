package net.zentao.requirement.app;

import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.requirement.domain.Story;
import net.zentao.requirement.domain.StoryRepository;
import net.zentao.task.api.TaskApi;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 删除需求（requirement 卡 §5 DELETE，A-07 落地）：软删 + 动态流 deleted。
 * 守卫：存在未删任务引用 storyId → 42203（TaskApi）；被未删子需求 parentId 引用 → 42203。
 */
@Component
public class DeleteStoryHandler {

  private final StoryRepository repository;
  private final ProductApi productApi;
  private final TaskApi taskApi;
  private final ActivityRecorder activityRecorder;

  public DeleteStoryHandler(StoryRepository repository, ProductApi productApi, TaskApi taskApi,
      ActivityRecorder activityRecorder) {
    this.repository = repository;
    this.productApi = productApi;
    this.taskApi = taskApi;
    this.activityRecorder = activityRecorder;
  }

  @Transactional
  public void handle(SessionPrincipal actor, long storyId) {
    Story story = StoryActionSupport.require(actor, repository, productApi, storyId);
    if (taskApi.hasActiveTasksByStory(storyId)) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "story.guard.hasTasks");
    }
    if (repository.existsActiveByParent(storyId)) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "story.guard.hasChildren");
    }
    repository.softDelete(storyId);
    activityRecorder.record(actor.account(), "story", storyId, "deleted", null, null);
  }
}
