package net.zentao.workspace.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.workspace.api.TodoView;
import net.zentao.workspace.domain.Todo;
import net.zentao.workspace.domain.TodoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建待办（workspace 卡 §3.1/§5）：assignee 缺省当前账号且必须存在；type≠custom 需关联对象存在。
 * 日期语义：date 传值即落该日，null → 待定（NULL）——「默认今天」由前端表单给缺省值（见 STATE 决策登记）。
 */
@Component
public class CreateTodoHandler {

  private final TodoRepository repository;
  private final AccountApi accountApi;
  private final TodoTitleResolver titleResolver;
  private final ActivityRecorder activityRecorder;

  public CreateTodoHandler(TodoRepository repository, AccountApi accountApi, TodoTitleResolver titleResolver,
      ActivityRecorder activityRecorder) {
    this.repository = repository;
    this.accountApi = accountApi;
    this.titleResolver = titleResolver;
    this.activityRecorder = activityRecorder;
  }

  public record TodoCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
      @Schema(allowableValues = {"bug", "custom", "epic", "requirement", "story", "task",
          "testRun"}) String type,
      Long objectId, LocalDate date, String beginTime, String endTime, Integer priority,
      String description, Boolean isPrivate, String assignee) {}

  @Transactional
  public TodoView handle(SessionPrincipal actor, TodoCreateRequest command) {
    String type = command.type() == null ? "custom" : command.type();
    long objectId = command.objectId() == null ? 0 : command.objectId();
    TodoFields.validateCreate(command.title(), type, objectId, command.priority(), command.beginTime(),
        command.endTime());
    if (!"custom".equals(type) && !titleResolver.exists(type, objectId)) {
      throw ApiException.validation(Map.of("objectId", "notFound"));
    }
    String assignee = command.assignee() == null || command.assignee().isBlank()
        ? actor.account()
        : command.assignee().trim();
    List<String> missing = accountApi.missingAccounts(List.of(assignee));
    if (!missing.isEmpty()) {
      throw ApiException.validation(Map.of("assignee", "notFound"));
    }

    Todo todo = repository.insert(new Todo(
        0,
        command.title().trim(),
        type,
        objectId,
        command.date(),
        command.beginTime(),
        command.endTime(),
        command.priority() == null ? 3 : command.priority(),
        command.description(),
        "wait",
        Boolean.TRUE.equals(command.isPrivate()),
        assignee,
        null,
        null,
        null,
        null,
        null,
        null,
        actor.account(),
        Instant.now(),
        null,
        null,
        0));
    activityRecorder.record(actor.account(), "todo", todo.id(), "created", null, null);
    return TodoView.of(todo, titleResolver.resolve(todo));
  }
}
