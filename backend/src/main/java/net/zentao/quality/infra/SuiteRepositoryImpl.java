package net.zentao.quality.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.platform.persistence.SoftDeletes;
import net.zentao.quality.domain.Suite;
import net.zentao.quality.domain.SuiteRepository;
import org.springframework.stereotype.Component;

/** 套件/用例库仓储实现（infra：suite 主表 + suite_case 关联行）。 */
@Component
public class SuiteRepositoryImpl implements SuiteRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");

  private final SuiteMapper mapper;
  private final SuiteCaseMapper caseMapper;

  public SuiteRepositoryImpl(SuiteMapper mapper, SuiteCaseMapper caseMapper) {
    this.mapper = mapper;
    this.caseMapper = caseMapper;
  }

  @Override
  public Optional<Suite> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(po -> toDomain(po, findCaseIds(po.getId())));
  }

  private List<Long> findCaseIds(long suiteId) {
    return caseMapper.selectListByCondition(new QueryColumn("suite_id").eq(suiteId)).stream()
        .map(SuiteCasePO::getCaseId)
        .toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Suite> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(po -> toDomain(po, List.of()))
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public Suite insert(Suite suite) {
    SuitePO po = toPo(suite);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    replaceCases(po.getId(), suite.caseIds());
    return toDomain(mapper.selectOneById(po.getId()), suite.caseIds());
  }

  @Override
  public Optional<Suite> update(Suite suite) {
    // 全量覆盖（含 null）；回读行取库内自增后的 lockVersion
    if (mapper.update(toPo(suite), false) <= 0) {
      return Optional.empty();
    }
    return findActiveById(suite.id());
  }

  @Override
  public void replaceCases(long suiteId, List<Long> caseIds) {
    caseMapper.deleteByCondition(new QueryColumn("suite_id").eq(suiteId));
    for (Long caseId : caseIds) {
      SuiteCasePO po = new SuiteCasePO();
      po.setSuiteId(suiteId);
      po.setCaseId(caseId);
      caseMapper.insert(po);
    }
  }

  @Override
  public Map<Long, Long> countCases(List<Long> suiteIds) {
    if (suiteIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, Long> counts = new LinkedHashMap<>();
    for (SuiteCasePO po : caseMapper.selectListByCondition(new QueryColumn("suite_id").in(suiteIds))) {
      counts.merge(po.getSuiteId(), 1L, Long::sum);
    }
    return counts;
  }

  @Override
  public void softDelete(long id, String actor, Instant at) {
    // suite_case 关联行保留：套件软删后自然失效（A-07）
    SoftDeletes.apply("suite", Row.of("deleted_at", at).set("updated_by", actor),
        new QueryColumn("id").eq(id).and(DELETED_AT.isNull()));
  }

  private Suite toDomain(SuitePO po, List<Long> caseIds) {
    return new Suite(
        po.getId(),
        po.getProductId() == null ? 0 : po.getProductId(),
        po.getName(),
        po.getDescription(),
        po.getType(),
        po.getSort() == null ? 0 : po.getSort(),
        caseIds,
        po.getCreatedBy(),
        po.getCreatedAt(),
        po.getUpdatedBy(),
        po.getUpdatedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private SuitePO toPo(Suite suite) {
    SuitePO po = new SuitePO();
    po.setId(suite.id() == 0 ? null : suite.id());
    po.setProductId(suite.productId());
    po.setName(suite.name());
    po.setDescription(suite.description());
    po.setType(suite.type());
    po.setSort(suite.sort());
    po.setCreatedBy(suite.createdBy());
    po.setCreatedAt(suite.createdAt());
    po.setUpdatedBy(suite.updatedBy());
    po.setUpdatedAt(suite.updatedAt());
    po.setLockVersion(suite.lockVersion());
    return po;
  }
}
