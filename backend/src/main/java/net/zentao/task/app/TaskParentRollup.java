package net.zentao.task.app;

import java.util.List;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.requirement.api.StoryApi;
import net.zentao.task.domain.Task;
import net.zentao.task.domain.TaskHoursPolicy;
import net.zentao.task.domain.TaskRepository;
import org.springframework.stereotype.Component;

/**
 * 父子联动（task 卡 §4/§9）：父任务只能由子任务合计承载——
 * 任一未取消子 doing → 父 doing；全部未取消子 done → 父 done（动态流 autoUpdated）；父三件套按 §2 重算。
 * 同事务执行（调用方在 app 层持事务），失败整体回滚。
 */
@Component
public class TaskParentRollup {

  private final TaskRepository repository;
  private final ActivityRecorder activityRecorder;

  public TaskParentRollup(TaskRepository repository, ActivityRecorder activityRecorder) {
    this.repository = repository;
    this.activityRecorder = activityRecorder;
  }

  /** 首个子任务创建成功 → 父 is_parent=true。 */
  public void markParent(long parentId) {
    repository.findActiveById(parentId).ifPresent(parent -> {
      if (!parent.isParent()) {
        parent.setParentFlag(true);
        repository.update(parent).orElseThrow();
      }
    });
  }

  /** 全部子任务删除后复位 is_parent（不产生动态流，§4）。 */
  public void releaseIfChildless(long parentId) {
    repository.findActiveById(parentId).ifPresent(parent -> {
      if (parent.isParent() && repository.countActiveChildren(parentId) == 0) {
        parent.setParentFlag(false);
        repository.update(parent).orElseThrow();
      }
    });
  }

  /** 子任务状态/工时变化后重算父状态与三件套；返回父任务最新状态（无父则空）。 */
  public Task rollUp(long parentId, String actor) {
    return repository.findActiveById(parentId).map(parent -> {
      List<Task> children = repository.findActiveChildren(parentId);
      List<Task> active = children.stream().filter(child -> !"cancel".equals(child.status())).toList();
      String nextStatus = parent.status();
      if (!active.isEmpty()) {
        if (active.stream().allMatch(child -> "done".equals(child.status()) || "closed".equals(child.status()))) {
          nextStatus = "done";
        } else if (active.stream().anyMatch(child -> !"wait".equals(child.status()))) {
          nextStatus = "doing";
        }
      }
      TaskHoursPolicy.Totals totals = TaskHoursPolicy.rollUp(children);
      parent.setField("estimateHours", totals.estimateHours());
      parent.setField("consumedHours", totals.consumedHours());
      parent.setField("leftHours", totals.leftHours());
      if (!nextStatus.equals(parent.status())) {
        parent.applyStatus(nextStatus);
        parent.markUpdatedBy(actor);
        Task saved = repository.update(parent).orElseThrow();
        activityRecorder.record(actor, "task", parent.id(), "autoUpdated", null, null);
        return saved;
      }
      parent.markUpdatedBy(actor);
      return repository.update(parent).orElseThrow();
    }).orElse(null);
  }

  /** 任务侧事实 → requirement 域 stage 重算信号（storyId=0 不调用，§4）。 */
  public static StoryApi.TaskProgress progressOf(List<Task> storyTasks) {
    List<Task> active = storyTasks.stream().filter(task -> !"cancel".equals(task.status())).toList();
    boolean anyDoing = active.stream().anyMatch(task -> "doing".equals(task.status()) || "pause".equals(task.status()));
    boolean allDone = !active.isEmpty()
        && active.stream().allMatch(task -> "done".equals(task.status()) || "closed".equals(task.status()));
    return new StoryApi.TaskProgress(anyDoing, allDone);
  }
}
