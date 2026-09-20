package net.zentao.task.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.api.ExecutionApi;
import net.zentao.task.api.TaskView;
import net.zentao.task.domain.Task;
import net.zentao.task.domain.TaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 部分更新任务（task 卡 §3/§5）：PATCH 白名单 + lockVersion → 40901；consumedHours 拒改（不在请求体里）；
 * 清空语义：storyId/categoryId 传 0 清空，文本字段传空串清空（null=不修改，03 §1）。
 */
@Component
public class UpdateTaskHandler {

  private final TaskRepository repository;
  private final ExecutionApi executionApi;
  private final AccountApi accountApi;

  public UpdateTaskHandler(TaskRepository repository, ExecutionApi executionApi, AccountApi accountApi) {
    this.repository = repository;
    this.executionApi = executionApi;
    this.accountApi = accountApi;
  }

  /** 更新请求体（contract：TaskUpdateRequest）。 */
  public record TaskUpdateRequest(
      String title,
      @Schema(allowableValues = {"affair", "design", "devel", "discuss", "misc", "request", "study", "test",
          "ui"}) String type,
      Integer priority, Long categoryId, Long storyId, BigDecimal estimateHours,
      LocalDate estStartedDate, LocalDate deadline, String keywords, String description,
      List<String> notifyAccounts,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  @Transactional
  public TaskView handle(SessionPrincipal actor, long taskId, TaskUpdateRequest command) {
    Task task = TaskGuard.requireWritable(repository, executionApi, actor, taskId);
    if (command.lockVersion() == null || command.lockVersion() != task.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新后重试。");
    }
    TaskFields.hours("estimateHours", command.estimateHours(), false);
    TaskFields.accounts(accountApi, Map.of(), command.notifyAccounts());
    task.update(command.title() == null ? null : TaskFields.requireTitle(command.title()),
        command.type() == null ? null : TaskFields.type(command.type()),
        command.priority() == null ? null : TaskFields.priority(command.priority()), command.categoryId(),
        command.storyId(), command.estimateHours(), command.estStartedDate(), command.deadline(),
        command.keywords(), command.description(), command.notifyAccounts());
    task.markUpdatedBy(actor.account());
    Task saved = repository.update(task)
        .orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
    return TaskView.of(saved);
  }
}
