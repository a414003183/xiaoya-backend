package net.zentao.task.app;

import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.api.ExecutionApi;
import net.zentao.task.domain.Task;
import net.zentao.task.domain.TaskRepository;

/** 任务数据权限守卫（task 卡 §7）：执行可见性决定任务可见性；执行已关闭 → 写端点 42203。 */
final class TaskGuard {

  private TaskGuard() {}

  /** 任务不存在 → 40401；所属执行不可见 → 40302。 */
  static Task requireVisible(TaskRepository repository, ExecutionApi executionApi, SessionPrincipal actor,
      long taskId) {
    Task task = repository.findActiveById(taskId).orElseThrow(() -> ApiException.notFound("任务"));
    executionApi.requireExecution(actor, task.executionId());
    return task;
  }

  /** 读路径：任务所在执行可见即可（含已关闭执行）。 */
  static Task requireReadable(TaskRepository repository, ExecutionApi executionApi, SessionPrincipal actor,
      long taskId) {
    return requireVisible(repository, executionApi, actor, taskId);
  }

  /** 写路径：在可见性之上加「执行已关闭 → 42203」只读闸门（旧 canModify 语义）。 */
  static Task requireWritable(TaskRepository repository, ExecutionApi executionApi, SessionPrincipal actor,
      long taskId) {
    Task task = repository.findActiveById(taskId).orElseThrow(() -> ApiException.notFound("任务"));
    executionApi.requireWritable(actor, task.executionId());
    return task;
  }
}
