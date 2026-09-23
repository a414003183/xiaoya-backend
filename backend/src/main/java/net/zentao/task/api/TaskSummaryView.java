package net.zentao.task.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import net.zentao.task.domain.Task;

/** 任务摘要行（contract：TaskSummaryView；周报三表经 TaskApi 取数，workspace 卡 §3.2）。 */
public record TaskSummaryView(
    long id,
    String title,
    @Schema(allowableValues = {"cancel", "closed", "doing", "done", "pause", "wait"}) String status,
    int priority,
    String assignee,
    BigDecimal estimateHours,
    BigDecimal consumedHours,
    BigDecimal leftHours,
    LocalDate beginDate,
    LocalDate endDate) {

  public static TaskSummaryView of(Task task) {
    return new TaskSummaryView(task.id(), task.title(), task.status(), task.priority(), task.assignee(),
        task.estimateHours(), task.consumedHours(), task.leftHours(), task.estStartedDate(), task.deadline());
  }
}
