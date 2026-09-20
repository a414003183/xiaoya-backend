package net.zentao.workspace.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.quality.api.BugApi;
import net.zentao.quality.api.TestRunApi;
import net.zentao.requirement.api.StoryApi;
import net.zentao.task.api.TaskApi;
import net.zentao.workspace.domain.Todo;
import org.springframework.stereotype.Component;

/**
 * 待办关联对象标题现算（workspace 卡 §3.1：type≠custom 时经对应 &lt;Entity&gt;Api 取；
 * 对象已删 → null，存量 title 为空时展示用、不回写）。跨域只经各域 api 包（A2）。
 */
@Component
public class TodoTitleResolver {

  private static final Set<String> STORY_TYPES = Set.of("story", "epic", "requirement");

  private final StoryApi storyApi;
  private final BugApi bugApi;
  private final TaskApi taskApi;
  private final TestRunApi testRunApi;

  public TodoTitleResolver(StoryApi storyApi, BugApi bugApi, TaskApi taskApi, TestRunApi testRunApi) {
    this.storyApi = storyApi;
    this.bugApi = bugApi;
    this.taskApi = taskApi;
    this.testRunApi = testRunApi;
  }

  /** 单条现算（详情/创建/动作响应）。 */
  public String resolve(Todo todo) {
    return resolveAll(List.of(todo)).get(todo.id());
  }

  /** 批量现算：按 type 分组一次取回，避免 N+1。 */
  public Map<Long, String> resolveAll(List<Todo> todos) {
    List<Long> storyIds = new ArrayList<>();
    List<Long> bugIds = new ArrayList<>();
    List<Long> taskIds = new ArrayList<>();
    List<Long> testRunIds = new ArrayList<>();
    for (Todo todo : todos) {
      if ("custom".equals(todo.type()) || todo.objectId() <= 0) {
        continue;
      }
      switch (todo.type()) {
        case "bug" -> bugIds.add(todo.objectId());
        case "task" -> taskIds.add(todo.objectId());
        case "testRun" -> testRunIds.add(todo.objectId());
        default -> {
          if (STORY_TYPES.contains(todo.type())) {
            storyIds.add(todo.objectId());
          }
        }
      }
    }
    Map<Long, String> titles = new LinkedHashMap<>();
    if (!storyIds.isEmpty()) {
      storyApi.findByIds(storyIds.stream().distinct().toList())
          .forEach(story -> titles.put(story.id(), story.title()));
    }
    titles.putAll(bugApi.titlesByIds(bugIds));
    titles.putAll(taskApi.titlesByIds(taskIds));
    titles.putAll(testRunApi.titlesByIds(testRunIds));

    Map<Long, String> byTodoId = new LinkedHashMap<>();
    for (Todo todo : todos) {
      byTodoId.put(todo.id(), titles.get(todo.objectId()));
    }
    return byTodoId;
  }

  /** 关联对象存在性校验（§3.1「type≠custom 时必填且对象存在」）。 */
  public boolean exists(String type, long objectId) {
    if ("custom".equals(type)) {
      return true;
    }
    if (STORY_TYPES.contains(type)) {
      return storyApi.findById(objectId).isPresent();
    }
    return switch (type) {
      case "bug" -> bugApi.titlesByIds(List.of(objectId)).containsKey(objectId);
      case "task" -> taskApi.titlesByIds(List.of(objectId)).containsKey(objectId);
      case "testRun" -> testRunApi.titlesByIds(List.of(objectId)).containsKey(objectId);
      default -> false;
    };
  }
}
