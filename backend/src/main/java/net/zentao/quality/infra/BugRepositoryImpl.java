package net.zentao.quality.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.quality.domain.Bug;
import net.zentao.quality.domain.BugRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Bug 仓储实现（infra：PO ↔ 领域对象；三个 JSON 文本列）。 */
@Component
public class BugRepositoryImpl implements BugRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final BugMapper mapper;
  private final JsonMapper jsonMapper;

  public BugRepositoryImpl(BugMapper mapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<Bug> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(this::toDomain);
  }

  @Override
  public List<Bug> findActiveByIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return mapper.selectListByCondition(new QueryColumn("id").in(ids).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<Bug> findActiveByProduct(long productId) {
    return mapper.selectListByCondition(
        new QueryColumn("product_id").eq(productId).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Bug> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public Bug insert(Bug bug) {
    BugPO po = toPo(bug);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public Optional<Bug> update(Bug bug) {
    // 全量覆盖（含 null）：activate 清 resolution 三字段必须落库
    // 回读行：lockVersion 由库内自增，回内存聚合会给出过期版本（下次 PATCH 必 40901）——同 project/task 口径。
    if (mapper.update(toPo(bug), false) <= 0) {
      return Optional.empty();
    }
    return findActiveById(bug.id());
  }

  @Override
  public int updatePlanId(List<Long> ids, Long planId) {
    if (ids.isEmpty()) {
      return 0;
    }
    return Db.updateByCondition("bug", Row.of("plan_id", planId), new QueryColumn("id").in(ids));
  }

  @Override
  public void softDelete(long id, String actor, Instant at) {
    Db.updateByCondition("bug", Row.of("deleted_at", at).set("updated_by", actor),
        new QueryColumn("id").eq(id).and(DELETED_AT.isNull()));
  }

  private Bug toDomain(BugPO po) {
    return new Bug(
        po.getId(),
        po.getProductId(),
        po.getBranchId() == null ? 0 : po.getBranchId(),
        po.getCategoryId() == null ? 0 : po.getCategoryId(),
        po.getProjectId() == null ? 0 : po.getProjectId(),
        po.getExecutionId() == null ? 0 : po.getExecutionId(),
        po.getPlanId(),
        po.getStoryId(),
        po.getTaskId(),
        po.getTestCaseId(),
        po.getTestRunId(),
        po.getTitle(),
        po.getKeywords(),
        po.getSeverity() == null ? 3 : po.getSeverity(),
        po.getPriority() == null ? 3 : po.getPriority(),
        po.getType(),
        po.getOs(),
        po.getBrowser(),
        po.getSteps(),
        po.getOpenedBuilds(),
        po.getStatus(),
        po.getConfirmed() != null && po.getConfirmed() == 1,
        po.getActivatedCount() == null ? 0 : po.getActivatedCount(),
        po.getDeadline(),
        po.getAssignee(),
        po.getAssignedAt(),
        po.getResolution(),
        po.getResolvedBy(),
        po.getResolvedAt(),
        po.getResolvedBuild(),
        po.getDuplicateOfId(),
        readLongList(po.getRelatedBugIds()),
        readStringList(po.getNotifyAccounts()),
        po.getClosedBy(),
        po.getClosedAt(),
        readMap(po.getCustomFields()),
        po.getCreatedBy(),
        po.getCreatedAt(),
        po.getUpdatedBy(),
        po.getUpdatedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private BugPO toPo(Bug bug) {
    BugPO po = new BugPO();
    po.setId(bug.id() == 0 ? null : bug.id());
    po.setProductId(bug.productId());
    po.setBranchId(bug.branchId());
    po.setCategoryId(bug.categoryId());
    po.setProjectId(bug.projectId());
    po.setExecutionId(bug.executionId());
    po.setPlanId(bug.planId());
    po.setStoryId(bug.storyId());
    po.setTaskId(bug.taskId());
    po.setTestCaseId(bug.testCaseId());
    po.setTestRunId(bug.testRunId());
    po.setTitle(bug.title());
    po.setKeywords(bug.keywords());
    po.setSeverity(bug.severity());
    po.setPriority(bug.priority());
    po.setType(bug.type());
    po.setOs(bug.os());
    po.setBrowser(bug.browser());
    po.setSteps(bug.steps());
    po.setOpenedBuilds(bug.openedBuilds());
    po.setStatus(bug.status());
    po.setConfirmed(bug.confirmed() ? 1 : 0);
    po.setActivatedCount(bug.activatedCount());
    po.setDeadline(bug.deadline());
    po.setAssignee(bug.assignee());
    po.setAssignedAt(bug.assignedAt());
    po.setResolution(bug.resolution());
    po.setResolvedBy(bug.resolvedBy());
    po.setResolvedAt(bug.resolvedAt());
    po.setResolvedBuild(bug.resolvedBuild());
    po.setDuplicateOfId(bug.duplicateOfId());
    po.setRelatedBugIds(write(bug.relatedBugIds()));
    po.setNotifyAccounts(write(bug.notifyAccounts()));
    po.setClosedBy(bug.closedBy());
    po.setClosedAt(bug.closedAt());
    po.setCustomFields(write(bug.customFields()));
    po.setCreatedBy(bug.createdBy());
    po.setCreatedAt(bug.createdAt());
    po.setUpdatedBy(bug.updatedBy());
    po.setUpdatedAt(bug.updatedAt());
    po.setLockVersion(bug.lockVersion());
    return po;
  }

  private List<Long> readLongList(String json) {
    return json == null || json.isBlank() ? List.of() : jsonMapper.readValue(json, LONG_LIST);
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
