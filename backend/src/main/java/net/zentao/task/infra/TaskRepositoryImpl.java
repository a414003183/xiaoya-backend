package net.zentao.task.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.task.domain.Task;
import net.zentao.task.domain.TaskRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 任务仓储实现（infra：PO ↔ 领域对象；notify_accounts/custom_fields 为 JSON 文本列）。 */
@Component
public class TaskRepositoryImpl implements TaskRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final TaskMapper mapper;
  private final JsonMapper jsonMapper;

  public TaskRepositoryImpl(TaskMapper mapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<Task> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(this::toDomain);
  }

  @Override
  public List<Task> findActiveByIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return mapper.selectListByCondition(new QueryColumn("id").in(ids).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<Task> findActiveChildren(long parentId) {
    return mapper.selectListByCondition(new QueryColumn("parent_id").eq(parentId).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<Task> findActiveByStory(long storyId) {
    return mapper.selectListByCondition(new QueryColumn("story_id").eq(storyId).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<Task> findActiveByExecution(long executionId) {
    return mapper.selectListByCondition(new QueryColumn("execution_id").eq(executionId).and(DELETED_AT.isNull()))
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<Task> findActiveByProject(long projectId) {
    return mapper.selectListByCondition(new QueryColumn("project_id").eq(projectId).and(DELETED_AT.isNull()))
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countActiveChildren(long parentId) {
    return mapper.selectCountByCondition(new QueryColumn("parent_id").eq(parentId).and(DELETED_AT.isNull()));
  }

  @Override
  public void softDelete(long id) {
    Db.updateByCondition("task", Row.of("deleted_at", Instant.now()), new QueryColumn("id").eq(id));
  }

  @Override
  public Task insert(Task task) {
    TaskPO po = toPo(task);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return findActiveById(po.getId()).orElseThrow();
  }

  @Override
  public Optional<Task> update(Task task) {
    // 全量覆盖（含 null 字段）：聚合持有完整状态，清空字段（如 activate 清 finished_at）必须落库。
    // 回读行：lockVersion 由库内自增，前端下一次 PATCH 需要最新值。
    if (mapper.update(toPo(task), false) <= 0) {
      return Optional.empty();
    }
    return findActiveById(task.id());
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Task> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  private Task toDomain(TaskPO po) {
    return new Task(po.getId(), po.getExecutionId() == null ? 0 : po.getExecutionId(),
        po.getProjectId() == null ? 0 : po.getProjectId(), po.getStoryId() == null ? 0 : po.getStoryId(),
        po.getParentId() == null ? 0 : po.getParentId(), po.getCategoryId() == null ? 0 : po.getCategoryId(),
        po.getTitle(), po.getType(), po.getStatus(), po.getPriority() == null ? 3 : po.getPriority(),
        po.getEstimateHours(), po.getConsumedHours(), po.getLeftHours(), po.getEstStartedDate(), po.getDeadline(),
        po.getAssignee(), po.getAssignedAt(), po.getStartedAt(), po.getActivatedAt(), po.getFinishedBy(),
        po.getFinishedAt(), po.getCanceledBy(), po.getCanceledAt(), po.getClosedBy(), po.getClosedAt(),
        po.getClosedReason(), po.getKeywords(), po.getDescription(),
        po.getIsParent() != null && po.getIsParent() == 1, readStringList(po.getNotifyAccounts()),
        readMap(po.getCustomFields()), po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(), po.getUpdatedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private TaskPO toPo(Task task) {
    TaskPO po = new TaskPO();
    po.setId(task.id() == 0 ? null : task.id());
    po.setExecutionId(task.executionId());
    po.setProjectId(task.projectId());
    po.setStoryId(task.storyId());
    po.setParentId(task.parentId());
    po.setCategoryId(task.categoryId());
    po.setTitle(task.title());
    po.setType(task.type());
    po.setStatus(task.status());
    po.setPriority(task.priority());
    po.setEstimateHours(task.estimateHours());
    po.setConsumedHours(task.consumedHours());
    po.setLeftHours(task.leftHours());
    po.setEstStartedDate(task.estStartedDate());
    po.setDeadline(task.deadline());
    po.setAssignee(task.assignee());
    po.setAssignedAt(task.assignedAt());
    po.setStartedAt(task.startedAt());
    po.setActivatedAt(task.activatedAt());
    po.setFinishedBy(task.finishedBy());
    po.setFinishedAt(task.finishedAt());
    po.setCanceledBy(task.canceledBy());
    po.setCanceledAt(task.canceledAt());
    po.setClosedBy(task.closedBy());
    po.setClosedAt(task.closedAt());
    po.setClosedReason(task.closedReason());
    po.setKeywords(task.keywords());
    po.setDescription(task.description());
    po.setIsParent(task.isParent() ? 1 : 0);
    po.setNotifyAccounts(write(task.notifyAccounts()));
    po.setCustomFields(write(task.customFields()));
    po.setCreatedBy(task.createdBy());
    po.setCreatedAt(task.createdAt());
    po.setUpdatedBy(task.updatedBy());
    po.setUpdatedAt(task.updatedAt());
    po.setLockVersion(task.lockVersion());
    return po;
  }

  private List<String> readStringList(String json) {
    return json == null || json.isBlank() ? List.of() : jsonMapper.readValue(json, STRING_LIST);
  }

  private Map<String, Object> readMap(String json) {
    return json == null || json.isBlank() ? Map.of() : jsonMapper.readValue(json, MAP);
  }

  private String write(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof List<?> list && list.isEmpty()) {
      return null;
    }
    if (value instanceof Map<?, ?> map && map.isEmpty()) {
      return null;
    }
    return jsonMapper.writeValueAsString(value);
  }
}
