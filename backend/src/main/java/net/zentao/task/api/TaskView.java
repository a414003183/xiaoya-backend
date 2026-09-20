package net.zentao.task.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.zentao.task.domain.Task;

/** 任务视图（contract：TaskView；task 卡 §3 读侧字段 + children/storyTitle 联查字段）。 */
public record TaskView(
    long id,
    long executionId,
    long projectId,
    long storyId,
    long parentId,
    long categoryId,
    String title,
    @Schema(allowableValues = {"affair", "design", "devel", "discuss", "misc", "request", "study", "test",
        "ui"}) String type,
    int priority,
    @Schema(allowableValues = {"cancel", "closed", "doing", "done", "pause", "wait"}) String status,
    BigDecimal estimateHours,
    BigDecimal consumedHours,
    BigDecimal leftHours,
    LocalDate estStartedDate,
    LocalDate deadline,
    String assignee,
    Instant assignedAt,
    Instant startedAt,
    Instant activatedAt,
    String finishedBy,
    Instant finishedAt,
    String canceledBy,
    Instant canceledAt,
    String closedBy,
    Instant closedAt,
    @Schema(allowableValues = {"cancel", "done"}) String closedReason,
    String keywords,
    String description,
    boolean isParent,
    List<String> notifyAccounts,
    Map<String, Object> customFields,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    int lockVersion,
    List<TaskChildSummary> children,
    String storyTitle) {

  /** 子任务摘要（contract：TaskChildSummary；仅详情响应填充）。 */
  public record TaskChildSummary(long id, String title,
      @Schema(allowableValues = {"cancel", "closed", "doing", "done", "pause", "wait"}) String status,
      String assignee) {

    public static TaskChildSummary of(Task task) {
      return new TaskChildSummary(task.id(), task.title(), task.status(), task.assignee());
    }
  }

  public static TaskView of(Task task) {
    return of(task, null, null);
  }

  public static TaskView of(Task task, List<TaskChildSummary> children, String storyTitle) {
    return new TaskView(task.id(), task.executionId(), task.projectId(), task.storyId(), task.parentId(),
        task.categoryId(), task.title(), task.type(), task.priority(), task.status(),
        task.estimateHours(), task.consumedHours(), task.leftHours(), task.estStartedDate(), task.deadline(),
        task.assignee(), task.assignedAt(), task.startedAt(), task.activatedAt(), task.finishedBy(),
        task.finishedAt(), task.canceledBy(), task.canceledAt(), task.closedBy(), task.closedAt(),
        task.closedReason(), task.keywords(), task.description(), task.isParent(), task.notifyAccounts(),
        task.customFields(), task.createdBy(), task.createdAt(), task.updatedBy(), task.updatedAt(),
        task.lockVersion(), children, storyTitle);
  }
}
