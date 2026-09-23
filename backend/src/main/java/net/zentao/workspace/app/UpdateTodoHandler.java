package net.zentao.workspace.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.DataScope;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.workspace.api.TodoView;
import net.zentao.workspace.domain.Todo;
import net.zentao.workspace.domain.TodoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 部分更新待办（workspace 卡 §5 PATCH 白名单；lockVersion 不符 → 40901；写权仅创建人/负责人 → 40302）。 */
@Component
public class UpdateTodoHandler {

  private final TodoRepository repository;
  private final TodoTitleResolver titleResolver;
  private final DataScope dataScope;

  public UpdateTodoHandler(TodoRepository repository, TodoTitleResolver titleResolver, DataScope dataScope) {
    this.repository = repository;
    this.titleResolver = titleResolver;
    this.dataScope = dataScope;
  }

  public record TodoUpdateRequest(
      String title,
      @Schema(allowableValues = {"bug", "custom", "epic", "requirement", "story", "task",
          "testRun"}) String type,
      Long objectId, LocalDate date, String beginTime, String endTime, Integer priority,
      String description, Boolean isPrivate,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  @Transactional
  public TodoView handle(SessionPrincipal actor, long todoId, TodoUpdateRequest command) {
    Todo todo = repository.findActiveById(todoId).orElseThrow(() -> ApiException.notFound("entity.todo"));
    TodoAccess.requireWritable(actor.account(), todo, dataScope.isSuperAdmin(actor));
    if (command.lockVersion() == null || command.lockVersion() != todo.lockVersion()) {
      throw ApiException.lockConflict();
    }
    TodoFields.validateUpdate(command.title(), command.type(), command.objectId(), command.priority(),
        command.beginTime(), command.endTime(), todo);
    String effectiveType = command.type() == null ? todo.type() : command.type();
    long effectiveObjectId = command.objectId() == null ? todo.objectId() : command.objectId();
    if (!"custom".equals(effectiveType) && !titleResolver.exists(effectiveType, effectiveObjectId)) {
      throw ApiException.validation(Map.of("objectId", "notFound"));
    }

    todo.update(command.title() == null ? null : command.title().trim(), command.type(), command.objectId(),
        command.date(), command.beginTime(), command.endTime(), command.priority(), command.description(),
        command.isPrivate());
    todo.markUpdatedBy(actor.account());
    Todo saved = repository.update(todo)
        .orElseThrow(() -> ApiException.lockConflict());
    return TodoView.of(saved, titleResolver.resolve(saved));
  }
}
