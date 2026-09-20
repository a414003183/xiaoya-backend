package net.zentao.workspace.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import net.zentao.workspace.domain.Todo;

/** 待办视图（contract：TodoView；workspace 卡 §3.1 + 现算 objectTitle）。 */
public record TodoView(
    long id,
    String title,
    @Schema(allowableValues = {"bug", "custom", "epic", "requirement", "story", "task",
        "testRun"}) String type,
    long objectId,
    String objectTitle,
    LocalDate date,
    String beginTime,
    String endTime,
    int priority,
    String description,
    @Schema(allowableValues = {"closed", "doing", "done", "wait"}) String status,
    boolean isPrivate,
    String assignee,
    String assignedBy,
    Instant assignedAt,
    String finishedBy,
    Instant finishedAt,
    String closedBy,
    Instant closedAt,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    int lockVersion) {

  public static TodoView of(Todo todo, String objectTitle) {
    return new TodoView(
        todo.id(),
        todo.title(),
        todo.type(),
        todo.objectId(),
        objectTitle,
        todo.date(),
        todo.beginTime(),
        todo.endTime(),
        todo.priority(),
        todo.description(),
        todo.status(),
        todo.isPrivate(),
        todo.assignee(),
        todo.assignedBy(),
        todo.assignedAt(),
        todo.finishedBy(),
        todo.finishedAt(),
        todo.closedBy(),
        todo.closedAt(),
        todo.createdBy(),
        todo.createdAt(),
        todo.updatedBy(),
        todo.updatedAt(),
        todo.lockVersion());
  }
}
