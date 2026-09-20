package net.zentao.quality.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.quality.domain.TestRun;
import net.zentao.quality.domain.TestRunRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 测试单仓储实现（infra：PO ↔ 领域对象；三个 JSON 文本列）。 */
@Component
public class TestRunRepositoryImpl implements TestRunRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final TestRunMapper mapper;
  private final JsonMapper jsonMapper;

  public TestRunRepositoryImpl(TestRunMapper mapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<TestRun> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(this::toDomain);
  }

  @Override
  public List<TestRun> findActiveByIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return mapper.selectListByCondition(new QueryColumn("id").in(ids).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<TestRun> findActiveByExecution(long executionId) {
    return mapper.selectListByCondition(new QueryColumn("execution_id").eq(executionId)
        .and(DELETED_AT.isNull())).stream().map(this::toDomain).toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<TestRun> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public TestRun insert(TestRun testRun) {
    TestRunPO po = toPo(testRun);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public void softDelete(long id, String actor, Instant at) {
    // test_run_case 历史行与已回填 reportId 保留（A-07）
    Db.updateByCondition("test_run", Row.of("deleted_at", at).set("updated_by", actor),
        new QueryColumn("id").eq(id).and(DELETED_AT.isNull()));
  }

  @Override
  public Optional<TestRun> update(TestRun testRun) {
    // 全量覆盖（含 null）；回读行取库内自增后的 lockVersion
    if (mapper.update(toPo(testRun), false) <= 0) {
      return Optional.empty();
    }
    return findActiveById(testRun.id());
  }

  private TestRun toDomain(TestRunPO po) {
    return new TestRun(
        po.getId(),
        po.getProductId(),
        po.getProjectId() == null ? 0 : po.getProjectId(),
        po.getExecutionId(),
        po.getBuildId() == null ? 0 : po.getBuildId(),
        po.getName(),
        po.getOwner(),
        po.getPriority() == null ? 3 : po.getPriority(),
        po.getType(),
        po.getBeginDate(),
        po.getEndDate(),
        po.getRealBeganAt(),
        po.getRealFinishedAt(),
        po.getDescription(),
        readStringList(po.getMembers()),
        readStringList(po.getNotifyAccounts()),
        po.getStatus(),
        po.getReportId(),
        readMap(po.getCustomFields()),
        po.getCreatedBy(),
        po.getCreatedAt(),
        po.getUpdatedBy(),
        po.getUpdatedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private TestRunPO toPo(TestRun run) {
    TestRunPO po = new TestRunPO();
    po.setId(run.id() == 0 ? null : run.id());
    po.setProductId(run.productId());
    po.setProjectId(run.projectId());
    po.setExecutionId(run.executionId());
    po.setBuildId(run.buildId());
    po.setName(run.name());
    po.setOwner(run.owner());
    po.setPriority(run.priority());
    po.setType(run.type());
    po.setBeginDate(run.beginDate());
    po.setEndDate(run.endDate());
    po.setRealBeganAt(run.realBeganAt());
    po.setRealFinishedAt(run.realFinishedAt());
    po.setDescription(run.description());
    po.setMembers(write(run.members()));
    po.setNotifyAccounts(write(run.notifyAccounts()));
    po.setStatus(run.status());
    po.setReportId(run.reportId());
    po.setCustomFields(write(run.customFields()));
    po.setCreatedBy(run.createdBy());
    po.setCreatedAt(run.createdAt());
    po.setUpdatedBy(run.updatedBy());
    po.setUpdatedAt(run.updatedAt());
    po.setLockVersion(run.lockVersion());
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
