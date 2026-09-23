package net.zentao.quality.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.platform.persistence.SoftDeletes;
import net.zentao.quality.domain.TestCase;
import net.zentao.quality.domain.TestCaseRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 用例仓储实现（infra：主表 PO ↔ 领域对象 + case_step 整体替换写）。 */
@Component
public class TestCaseRepositoryImpl implements TestCaseRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final TestCaseMapper mapper;
  private final CaseStepMapper stepMapper;
  private final JsonMapper jsonMapper;

  public TestCaseRepositoryImpl(TestCaseMapper mapper, CaseStepMapper stepMapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.stepMapper = stepMapper;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<TestCase> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(po -> toDomain(po, findSteps(List.of(po.getId())).getOrDefault(po.getId(), List.of())));
  }

  @Override
  public List<TestCase> findActiveByIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    List<TestCasePO> pos = mapper.selectListByCondition(new QueryColumn("id").in(ids).and(DELETED_AT.isNull()));
    Map<Long, List<TestCase.Step>> steps = findSteps(pos.stream().map(TestCasePO::getId).toList());
    return pos.stream().map(po -> toDomain(po, steps.getOrDefault(po.getId(), List.of()))).toList();
  }

  @Override
  public Map<Long, List<TestCase.Step>> findSteps(List<Long> caseIds) {
    if (caseIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, List<TestCase.Step>> grouped = new LinkedHashMap<>();
    for (CaseStepPO po : stepMapper.selectListByQuery(
        QueryWrapper.create().where(new QueryColumn("case_id").in(caseIds))
            .orderBy(new QueryColumn("sort").asc()))) { // banned-words-ok：MyBatis-Flex 构造器方法名
      grouped.computeIfAbsent(po.getCaseId(), key -> new ArrayList<>())
          .add(new TestCase.Step(po.getSort(), po.getDescription(), po.getExpects()));
    }
    return grouped;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<TestCase> queryPage(Object whereWrapper, int offset, int limit) {
    List<TestCasePO> pos = mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit));
    Map<Long, List<TestCase.Step>> steps = findSteps(pos.stream().map(TestCasePO::getId).toList());
    return pos.stream().map(po -> toDomain(po, steps.getOrDefault(po.getId(), List.of()))).toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public TestCase insert(TestCase testCase) {
    TestCasePO po = toPo(testCase);
    po.setId(null);
    po.setVersion(1);
    po.setLockVersion(0);
    mapper.insert(po);
    insertSteps(po.getId(), testCase.steps());
    return toDomain(mapper.selectOneById(po.getId()),
        findSteps(List.of(po.getId())).getOrDefault(po.getId(), List.of()));
  }

  @Override
  public Optional<TestCase> update(TestCase testCase) {
    // 全量覆盖主表（含 null）+ steps 整体替换：旧行全删，新行按 sort 落
    int rows = mapper.update(toPo(testCase), false);
    if (rows == 0) {
      return Optional.empty();
    }
    Db.deleteByCondition("case_step", new QueryColumn("case_id").eq(testCase.id()));
    insertSteps(testCase.id(), testCase.steps());
    // 回读行：lockVersion 由库内自增（回内存聚合会给出过期版本）
    return findActiveById(testCase.id());
  }

  @Override
  public int updateLastRun(List<Long> caseIds, String result, String runner, Instant runAt) {
    if (caseIds.isEmpty()) {
      return 0;
    }
    Row values = new Row();
    values.set("last_run_result", result);
    values.set("last_runner", runner);
    values.set("last_run_at", runAt == null ? null : LocalDateTime.ofInstant(runAt, ZoneOffset.UTC));
    return Db.updateByCondition("test_case", values, new QueryColumn("id").in(caseIds));
  }

  @Override
  public long countActiveInLibrary(long libraryId) {
    return mapper.selectCountByCondition(new QueryColumn("product_id").eq(0)
        .and(new QueryColumn("library_id").eq(libraryId)).and(DELETED_AT.isNull()));
  }

  @Override
  public void softDelete(long id, String actor, Instant at) {
    SoftDeletes.apply("test_case", Row.of("deleted_at", at).set("updated_by", actor),
        new QueryColumn("id").eq(id).and(DELETED_AT.isNull()));
  }

  private void insertSteps(Long caseId, List<TestCase.Step> steps) {
    for (TestCase.Step step : steps) {
      CaseStepPO po = new CaseStepPO();
      po.setCaseId(caseId);
      po.setSort(step.sort());
      po.setDescription(step.description());
      po.setExpects(step.expects());
      stepMapper.insert(po);
    }
  }

  private TestCase toDomain(TestCasePO po, List<TestCase.Step> steps) {
    return new TestCase(
        po.getId(),
        po.getProductId() == null ? 0 : po.getProductId(),
        po.getBranchId() == null ? 0 : po.getBranchId(),
        po.getLibraryId() == null ? 0 : po.getLibraryId(),
        po.getCategoryId() == null ? 0 : po.getCategoryId(),
        po.getStoryId(),
        po.getTitle(),
        po.getPrecondition(),
        po.getKeywords(),
        po.getPriority() == null ? 3 : po.getPriority(),
        po.getType(),
        readStringList(po.getStage()),
        po.getStatus(),
        steps,
        po.getFromBugId(),
        po.getLastRunResult(),
        po.getLastRunner(),
        po.getLastRunAt(),
        readStringList(po.getReviewers()),
        po.getReviewedAt(),
        po.getVersion() == null ? 1 : po.getVersion(),
        readMap(po.getCustomFields()),
        po.getCreatedBy(),
        po.getCreatedAt(),
        po.getUpdatedBy(),
        po.getUpdatedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private TestCasePO toPo(TestCase testCase) {
    TestCasePO po = new TestCasePO();
    po.setId(testCase.id() == 0 ? null : testCase.id());
    po.setProductId(testCase.productId());
    po.setBranchId(testCase.branchId());
    po.setLibraryId(testCase.libraryId());
    po.setCategoryId(testCase.categoryId());
    po.setStoryId(testCase.storyId());
    po.setTitle(testCase.title());
    po.setPrecondition(testCase.precondition());
    po.setKeywords(testCase.keywords());
    po.setPriority(testCase.priority());
    po.setType(testCase.type());
    po.setStage(write(testCase.stage()));
    po.setStatus(testCase.status());
    po.setFromBugId(testCase.fromBugId());
    po.setLastRunResult(testCase.lastRunResult());
    po.setLastRunner(testCase.lastRunner());
    po.setLastRunAt(testCase.lastRunAt());
    po.setReviewers(write(testCase.reviewers()));
    po.setReviewedAt(testCase.reviewedAt());
    po.setVersion(testCase.version());
    po.setCustomFields(write(testCase.customFields()));
    po.setCreatedBy(testCase.createdBy());
    po.setCreatedAt(testCase.createdAt());
    po.setUpdatedBy(testCase.updatedBy());
    po.setUpdatedAt(testCase.updatedAt());
    po.setLockVersion(testCase.lockVersion());
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
