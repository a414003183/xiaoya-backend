package net.zentao.requirement.app;

import java.util.List;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.requirement.domain.Story;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 需求侧被 product 域驱动的写操作（A5：事务归 app 层）：
 * 计划关联落 `story.planId`（product §4.3）、发布创建落 stage=released + `linked2release` 动态流（product §4.4）。
 */
@Component
public class StoryLinkHandler {

  private final StoryRepository repository;
  private final ActivityRecorder activityRecorder;

  public StoryLinkHandler(StoryRepository repository, ActivityRecorder activityRecorder) {
    this.repository = repository;
    this.activityRecorder = activityRecorder;
  }

  @Transactional
  public void linkPlan(List<Long> ids, long planId) {
    repository.updatePlanId(ids, planId);
  }

  @Transactional
  public void unlinkPlan(List<Long> ids, long planId) {
    repository.updatePlanId(ids, null);
  }

  @Transactional
  public void markReleased(List<Long> ids, String actor) {
    for (Story story : repository.findActiveByIds(ids.stream().distinct().toList())) {
      story.markReleased();
      story.markUpdatedBy(actor);
      repository.update(story);
      activityRecorder.record(actor, "story", story.id(), "linked2release", null, null);
    }
  }
}
