package net.zentao.workspace.infra;

import com.mybatisflex.core.query.QueryColumn;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import net.zentao.workspace.domain.Burn;
import net.zentao.workspace.domain.BurnRepository;
import org.springframework.stereotype.Component;

/** 燃尽日行仓储实现（infra）：UNIQUE(execution_id, burn_date, task_id) 兜底同日 upsert 幂等。 */
@Component
public class BurnRepositoryImpl implements BurnRepository {

  private final BurnMapper mapper;

  public BurnRepositoryImpl(BurnMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<Burn> findByExecution(long executionId) {
    return mapper.selectListByCondition(new QueryColumn("execution_id").eq(executionId)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<Burn> findByExecutionAndDate(long executionId, LocalDate burnDate) {
    return mapper.selectListByCondition(
        new QueryColumn("execution_id").eq(executionId).and(new QueryColumn("burn_date").eq(burnDate))).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public void upsert(Burn burn) {
    BurnPO existing = mapper.selectOneByCondition(new QueryColumn("execution_id").eq(burn.executionId())
        .and(new QueryColumn("burn_date").eq(burn.burnDate()))
        .and(new QueryColumn("task_id").eq(burn.taskId())));
    BurnPO po = toPo(burn);
    if (existing == null) {
      po.setId(null);
      mapper.insert(po);
    } else {
      po.setId(existing.getId());
      mapper.update(po, false);
    }
  }

  @Override
  public BigDecimal totalLeftHours(long executionId) {
    return findByExecutionAndDate(executionId, LocalDate.now()).stream()
        .filter(row -> row.taskId() == 0)
        .map(Burn::leftHours)
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private Burn toDomain(BurnPO po) {
    return new Burn(po.getId(), po.getExecutionId(), po.getBurnDate(), po.getTaskId() == null ? 0 : po.getTaskId(),
        po.getEstimateHours(), po.getConsumedHours(), po.getLeftHours(), po.getStoryPoint());
  }

  private BurnPO toPo(Burn burn) {
    BurnPO po = new BurnPO();
    po.setExecutionId(burn.executionId());
    po.setBurnDate(burn.burnDate());
    po.setTaskId(burn.taskId());
    po.setEstimateHours(burn.estimateHours());
    po.setConsumedHours(burn.consumedHours());
    po.setLeftHours(burn.leftHours());
    po.setStoryPoint(burn.storyPoint());
    return po;
  }
}
