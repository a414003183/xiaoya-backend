package net.zentao.requirement.app;

import net.zentao.platform.error.ApiException;
import net.zentao.requirement.api.StoryApi;
import net.zentao.requirement.domain.Story;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务进展驱动的需求 stage 重算（task 卡 §4 需求联动）：规则持有在本域（{@link Story#advanceStage}），
 * task 域只送任务侧事实；需求已释放（released）或不存在时静默结束（任务照常落库）。
 */
@Component
public class AdvanceStoryStageHandler {

  private final StoryRepository repository;

  public AdvanceStoryStageHandler(StoryRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void advance(long storyId, StoryApi.TaskProgress progress) {
    repository.findActiveById(storyId).ifPresent(story -> {
      story.advanceStage(progress.anyDoing(), progress.allDone());
      repository.update(story).orElseThrow(() -> ApiException.lockConflict());
    });
  }
}
