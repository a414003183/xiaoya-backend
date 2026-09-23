package net.zentao.workspace.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.persistence.SoftDeletes;
import net.zentao.workspace.domain.Todo;
import net.zentao.workspace.domain.TodoRepository;
import org.springframework.stereotype.Component;

/** 待办仓储实现（infra：PO ↔ 领域对象）。begin_time/end_time 在库内为 HHmm char(4)，出库补冒号。 */
@Component
public class TodoRepositoryImpl implements TodoRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final DateTimeFormatter DB_TIME = DateTimeFormatter.ofPattern("HHmm");

  private final TodoMapper mapper;

  public TodoRepositoryImpl(TodoMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Todo> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(this::toDomain);
  }

  @Override
  public List<Todo> findActiveByIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return mapper.selectListByCondition(new QueryColumn("id").in(ids).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public Todo insert(Todo todo) {
    TodoPO po = toPo(todo);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public Optional<Todo> update(Todo todo) {
    // 全量覆盖（含 null 字段）：聚合持有完整状态，activate 清 finished/closed 四列必须落库
    TodoPO po = toPo(todo);
    if (!(mapper.update(po, false) > 0)) {
      return Optional.empty();
    }
    // 回读：lock_version 由库内自增，内存态是旧值（决策⑺）
    return Optional.ofNullable(mapper.selectOneById(todo.id())).map(this::toDomain);
  }

  @Override
  public List<Todo> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public void softDelete(long id) {
    SoftDeletes.apply("todo", Row.of("deleted_at", Instant.now()), new QueryColumn("id").eq(id));
  }

  private Todo toDomain(TodoPO po) {
    return new Todo(
        po.getId(),
        po.getTitle(),
        po.getType(),
        po.getObjectId() == null ? 0 : po.getObjectId(),
        po.getTodoDate(),
        fromDbTime(po.getBeginTime()),
        fromDbTime(po.getEndTime()),
        po.getPriority() == null ? 3 : po.getPriority(),
        po.getDescription(),
        po.getStatus(),
        Boolean.TRUE.equals(po.getIsPrivate()),
        po.getAssignee(),
        po.getAssignedBy(),
        po.getAssignedAt(),
        po.getFinishedBy(),
        po.getFinishedAt(),
        po.getClosedBy(),
        po.getClosedAt(),
        po.getCreatedBy(),
        po.getCreatedAt(),
        po.getUpdatedBy(),
        po.getUpdatedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private TodoPO toPo(Todo todo) {
    TodoPO po = new TodoPO();
    po.setId(todo.id() == 0 ? null : todo.id());
    po.setTitle(todo.title());
    po.setType(todo.type());
    po.setObjectId(todo.objectId());
    po.setTodoDate(todo.date());
    po.setBeginTime(toDbTime(todo.beginTime()));
    po.setEndTime(toDbTime(todo.endTime()));
    po.setPriority(todo.priority());
    po.setDescription(todo.description());
    po.setStatus(todo.status());
    po.setIsPrivate(todo.isPrivate());
    po.setAssignee(todo.assignee());
    po.setAssignedBy(todo.assignedBy());
    po.setAssignedAt(todo.assignedAt());
    po.setFinishedBy(todo.finishedBy());
    po.setFinishedAt(todo.finishedAt());
    po.setClosedBy(todo.closedBy());
    po.setClosedAt(todo.closedAt());
    po.setCreatedBy(todo.createdBy());
    po.setCreatedAt(todo.createdAt());
    po.setUpdatedBy(todo.updatedBy());
    po.setUpdatedAt(todo.updatedAt());
    po.setLockVersion(todo.lockVersion());
    return po;
  }

  /** JSON HH:mm → DB HHmm（格式校验在 app 层 TodoFields；此处只做形态转换）。 */
  static String toDbTime(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return LocalTime.parse(value).format(DB_TIME);
  }

  /** DB HHmm → JSON HH:mm（存量脏值容错为 null，不炸列表）。 */
  static String fromDbTime(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.length() == 4 && trimmed.chars().allMatch(Character::isDigit)) {
      return trimmed.substring(0, 2) + ":" + trimmed.substring(2);
    }
    return trimmed.length() == 5 ? trimmed : null;
  }
}
