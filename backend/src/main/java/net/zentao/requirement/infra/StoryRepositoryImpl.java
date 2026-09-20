package net.zentao.requirement.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.requirement.domain.Story;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 需求仓储实现（infra：PO ↔ 领域对象；四个 JSON 文本列）。 */
@Component
public class StoryRepositoryImpl implements StoryRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final StoryMapper mapper;
  private final JsonMapper jsonMapper;

  public StoryRepositoryImpl(StoryMapper mapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<Story> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(this::toDomain);
  }

  @Override
  public List<Story> findActiveByIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return mapper.selectListByCondition(new QueryColumn("id").in(ids).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<Story> findActiveByProduct(long productId) {
    return mapper.selectListByCondition(
        new QueryColumn("product_id").eq(productId).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Story> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public Story insert(Story story) {
    StoryPO po = toPo(story);
    po.setId(null);
    po.setVersion(1);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public Optional<Story> update(Story story) {
    // 全量覆盖（含 null）：activate 清 closed_* 等置空字段必须落库
    return mapper.update(toPo(story), false) > 0 ? Optional.of(story) : Optional.empty();
  }

  @Override
  public int updatePlanId(List<Long> ids, Long planId) {
    if (ids.isEmpty()) {
      return 0;
    }
    return Db.updateByCondition("story", Row.of("plan_id", planId), new QueryColumn("id").in(ids));
  }

  @Override
  public boolean existsActiveByProduct(long productId) {
    return mapper.selectCountByCondition(
        new QueryColumn("product_id").eq(productId).and(DELETED_AT.isNull())) > 0;
  }

  @Override
  public boolean existsActiveByBranch(long branchId) {
    return mapper.selectCountByCondition(
        new QueryColumn("branch_id").eq(branchId).and(DELETED_AT.isNull())) > 0;
  }

  @Override
  public boolean existsActiveByPlan(long planId) {
    return mapper.selectCountByCondition(
        new QueryColumn("plan_id").eq(planId).and(DELETED_AT.isNull())) > 0;
  }

  @Override
  public boolean existsActiveByParent(long parentId) {
    return mapper.selectCountByCondition(
        new QueryColumn("parent_id").eq(parentId).and(DELETED_AT.isNull())) > 0;
  }

  @Override
  public void softDelete(long id) {
    Db.updateByCondition("story", Row.of("deleted_at", Instant.now()), new QueryColumn("id").eq(id));
  }

  private Story toDomain(StoryPO po) {
    return new Story(po.getId(), po.getProductId(), po.getBranchId() == null ? 0 : po.getBranchId(),
        po.getCategoryId() == null ? 0 : po.getCategoryId(), po.getPlanId(), po.getParentId(), po.getTitle(),
        po.getKeywords(), po.getType(), po.getStatus(), po.getPriority() == null ? 3 : po.getPriority(),
        po.getEstimateHours(), po.getSource(), po.getDescription(), po.getStage(), po.getAssignee(),
        po.getAssignedAt(), readStringList(po.getReviewers()), po.getNeedNotReview() != null && po.getNeedNotReview() == 1,
        readStringList(po.getNotifyAccounts()), readLongList(po.getLinkedStoryIds()), po.getDuplicateOfId(),
        po.getVersion() == null ? 1 : po.getVersion(), readMap(po.getCustomFields()), po.getCreatedBy(),
        po.getCreatedAt(), po.getUpdatedBy(), po.getUpdatedAt(), po.getClosedBy(), po.getClosedAt(),
        po.getClosedReason(), po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private StoryPO toPo(Story story) {
    StoryPO po = new StoryPO();
    po.setId(story.id() == 0 ? null : story.id());
    po.setProductId(story.productId());
    po.setBranchId(story.branchId());
    po.setCategoryId(story.categoryId());
    po.setPlanId(story.planId());
    po.setParentId(story.parentId());
    po.setTitle(story.title());
    po.setKeywords(story.keywords());
    po.setType(story.type());
    po.setStatus(story.status());
    po.setPriority(story.priority());
    po.setEstimateHours(story.estimateHours());
    po.setSource(story.source());
    po.setDescription(story.description());
    po.setStage(story.stage());
    po.setAssignee(story.assignee());
    po.setAssignedAt(story.assignedAt());
    po.setReviewers(write(story.reviewers()));
    po.setNeedNotReview(story.needNotReview() ? 1 : 0);
    po.setNotifyAccounts(write(story.notifyAccounts()));
    po.setLinkedStoryIds(write(story.linkedStoryIds()));
    po.setDuplicateOfId(story.duplicateOfId());
    po.setVersion(story.version());
    po.setCustomFields(write(story.customFields()));
    po.setCreatedBy(story.createdBy());
    po.setCreatedAt(story.createdAt());
    po.setUpdatedBy(story.updatedBy());
    po.setUpdatedAt(story.updatedAt());
    po.setClosedBy(story.closedBy());
    po.setClosedAt(story.closedAt());
    po.setClosedReason(story.closedReason());
    po.setLockVersion(story.lockVersion());
    return po;
  }

  private List<String> readStringList(String json) {
    return json == null || json.isBlank() ? List.of() : jsonMapper.readValue(json, STRING_LIST);
  }

  private List<Long> readLongList(String json) {
    return json == null || json.isBlank() ? List.of() : jsonMapper.readValue(json, LONG_LIST);
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
