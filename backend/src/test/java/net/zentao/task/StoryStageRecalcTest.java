package net.zentao.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.project.api.ExecutionApi;
import net.zentao.requirement.api.StoryApi;
import net.zentao.task.api.TaskView;
import net.zentao.task.app.TaskActionHandler;
import net.zentao.task.app.TaskParentRollup;
import net.zentao.task.app.TaskQueryService;
import net.zentao.task.domain.EffortRepository;
import net.zentao.task.domain.Task;
import net.zentao.task.domain.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 需求联动调用点（task 卡 §4/§8）：storyId≠0 的动作恰好调用一次 StoryApi.advanceStage，storyId=0 不调用。
 * 纯 Mockito 单测——跨域调用次数是契约级事实，不该被真库数据掩盖。
 */
class StoryStageRecalcTest {

  private final TaskRepository repository = mock(TaskRepository.class);
  private final EffortRepository effortRepository = mock(EffortRepository.class);
  private final ExecutionApi executionApi = mock(ExecutionApi.class);
  private final AccountApi accountApi = mock(AccountApi.class);
  private final StoryApi storyApi = mock(StoryApi.class);
  private final WorkflowEngine engine = mock(WorkflowEngine.class);
  private final TaskParentRollup parentRollup = mock(TaskParentRollup.class);
  private final TaskQueryService queryService = mock(TaskQueryService.class);

  private final TaskActionHandler handler = new TaskActionHandler(repository, effortRepository, executionApi,
      accountApi, storyApi, engine, parentRollup, queryService);

  private static final SessionPrincipal ACTOR = new SessionPrincipal(1, "admin");

  private Task task(long storyId) {
    return new Task(9, 5, 4, storyId, 0, 0, "联动任务", "devel", "wait", 3, BigDecimal.TEN, BigDecimal.ZERO, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false, List.of(),
        Map.of(), "admin", Instant.now(), null, null, 0);
  }

  private void stub(Task value) {
    when(repository.findActiveById(9)).thenReturn(Optional.of(value));
    when(repository.update(value)).thenReturn(Optional.of(value));
    when(executionApi.requireWritable(ACTOR, 5)).thenReturn(null);
    when(queryService.viewOf(eq(ACTOR), any(Task.class))).thenReturn(mock(TaskView.class));
  }

  @Test
  @DisplayName("storyId≠0 的 start 恰好调用一次 advanceStage")
  void startWithStoryCallsOnce() {
    Task value = task(7);
    stub(value);
    when(repository.findActiveByStory(7)).thenReturn(List.of(value));

    handler.start(ACTOR, 9, new TaskActionHandler.TaskStartRequest(null, null, null, null));

    verify(storyApi, times(1)).advanceStage(eq(7L), any(StoryApi.TaskProgress.class));
  }

  @Test
  @DisplayName("storyId=0 的动作不调用 advanceStage")
  void noStoryDoesNotCall() {
    Task value = task(0);
    stub(value);

    handler.start(ACTOR, 9, new TaskActionHandler.TaskStartRequest(null, null, null, null));
    handler.pause(ACTOR, 9, null);

    verify(storyApi, never()).advanceStage(anyLong(), any(StoryApi.TaskProgress.class));
  }
}
