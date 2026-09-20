package net.zentao.task.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.api.ExecutionApi;
import net.zentao.project.api.ProjectView;
import net.zentao.task.api.TaskView;
import net.zentao.task.domain.Task;
import net.zentao.task.domain.TaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建任务（task 卡 §3/§5/§8）：执行可见 40302 + 功能码 40301（控制器注解）；
 * parentId 一层校验（跨执行 42201、子任务再作父 42203）；创建时 status 恒 wait、consumedHours 恒 0。
 */
@Component
public class CreateTaskHandler {

  private final TaskRepository repository;
  private final ExecutionApi executionApi;
  private final AccountApi accountApi;
  private final ActivityRecorder activityRecorder;
  private final TaskParentRollup parentRollup;

  public CreateTaskHandler(TaskRepository repository, ExecutionApi executionApi, AccountApi accountApi,
      ActivityRecorder activityRecorder, TaskParentRollup parentRollup) {
    this.repository = repository;
    this.executionApi = executionApi;
    this.accountApi = accountApi;
    this.activityRecorder = activityRecorder;
    this.parentRollup = parentRollup;
  }

  /** 创建请求体（contract：TaskCreateRequest）；parentIndex 仅批量创建使用。 */
  public record TaskCreateRequest(
      Long storyId, Long parentId, Integer parentIndex, Long categoryId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
      @Schema(allowableValues = {"affair", "design", "devel", "discuss", "misc", "request", "study", "test",
          "ui"}) String type,
      Integer priority,
      BigDecimal estimateHours, LocalDate estStartedDate, LocalDate deadline, String assignee, String keywords,
      String description, List<String> notifyAccounts) {}

  @Transactional
  public TaskView handle(SessionPrincipal actor, long executionId, TaskCreateRequest command) {
    return TaskView.of(create(actor, executionId, command, command.parentId()));
  }

  /** 批量创建内部入口：parentIndex 已由批处理器解析为 parentId（null=顶层）。 */
  Task create(SessionPrincipal actor, long executionId, TaskCreateRequest command, Long resolvedParentId) {
    ProjectView execution = executionApi.requireWritable(actor, executionId);
    String title = TaskFields.requireTitle(command.title());
    TaskFields.hours("estimateHours", command.estimateHours(), false);
    TaskFields.accounts(accountApi, Map.of("assignee", command.assignee() == null ? "" : command.assignee()),
        command.notifyAccounts());

    long parentId = resolvedParentId == null ? 0 : resolvedParentId;
    if (parentId != 0) {
      Task parent = repository.findActiveById(parentId)
          .orElseThrow(() -> ApiException.validation(Map.of("parentId", "notFound")));
      if (parent.executionId() != executionId) {
        throw ApiException.validation(Map.of("parentId", "crossExecution"));
      }
      if (parent.parentId() != 0) {
        throw ApiException.guardNotSatisfied("子任务不可再有子任务（父子仅一层）。");
      }
    }

    Instant now = Instant.now();
    Task task = repository.insert(new Task(0, executionId, execution.parentId(), value(command.storyId()),
        parentId, value(command.categoryId()), title, TaskFields.type(command.type()),
        "wait", TaskFields.priority(command.priority()), command.estimateHours(), BigDecimal.ZERO, null,
        command.estStartedDate(), command.deadline(), command.assignee(), null, null, null, null, null, null, null,
        null, null, null, command.keywords(), command.description(), false, command.notifyAccounts(), null,
        actor.account(), now, null, null, 0));
    activityRecorder.record(actor.account(), "task", task.id(), "created", null, null);
    if (parentId != 0) {
      parentRollup.markParent(parentId);
    }
    return task;
  }

  private static long value(Long id) {
    return id == null ? 0 : id;
  }
}
