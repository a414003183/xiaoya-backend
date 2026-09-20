package net.zentao.task.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.zentao.task.domain.Effort;
import net.zentao.task.domain.EffortRepository;
import org.springframework.stereotype.Component;

/** 工时仓储实现（infra：软删过滤 + 未删流水查询）。 */
@Component
public class EffortRepositoryImpl implements EffortRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");

  private final EffortMapper mapper;

  public EffortRepositoryImpl(EffortMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Effort> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(this::toDomain);
  }

  @Override
  public List<Effort> findActiveByTask(long taskId) {
    return mapper.selectListByCondition(new QueryColumn("task_id").eq(taskId).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<Effort> findActiveByProject(long projectId) {
    return mapper.selectListByCondition(new QueryColumn("project_id").eq(projectId).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public Effort insert(Effort effort) {
    EffortPO po = toPo(effort);
    po.setId(null);
    mapper.insert(po);
    return findActiveById(po.getId()).orElseThrow();
  }

  @Override
  public Optional<Effort> update(Effort effort) {
    if (mapper.update(toPo(effort), false) <= 0) {
      return Optional.empty();
    }
    return findActiveById(effort.id());
  }

  @Override
  public void softDelete(long id) {
    EffortPO po = new EffortPO();
    po.setId(id);
    po.setDeletedAt(Instant.now());
    mapper.update(po);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Effort> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  private Effort toDomain(EffortPO po) {
    return new Effort(po.getId(), po.getTaskId(), po.getExecutionId() == null ? 0 : po.getExecutionId(),
        po.getProjectId() == null ? 0 : po.getProjectId(), po.getAccount(), po.getWorkDate(),
        po.getConsumedHours(), po.getLeftHours(), po.getWork(), po.getCreatedBy(), po.getCreatedAt(),
        po.getUpdatedBy(), po.getUpdatedAt());
  }

  private EffortPO toPo(Effort effort) {
    EffortPO po = new EffortPO();
    po.setId(effort.id() == 0 ? null : effort.id());
    po.setTaskId(effort.taskId());
    po.setExecutionId(effort.executionId());
    po.setProjectId(effort.projectId());
    po.setAccount(effort.account());
    po.setWorkDate(effort.workDate());
    po.setConsumedHours(effort.consumedHours());
    po.setLeftHours(effort.leftHours());
    po.setWork(effort.work());
    po.setCreatedBy(effort.createdBy());
    po.setCreatedAt(effort.createdAt());
    po.setUpdatedBy(effort.updatedBy());
    po.setUpdatedAt(effort.updatedAt());
    return po;
  }
}
