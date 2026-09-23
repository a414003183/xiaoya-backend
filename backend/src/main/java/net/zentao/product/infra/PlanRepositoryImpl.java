package net.zentao.product.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.platform.persistence.SoftDeletes;
import net.zentao.product.domain.Plan;
import net.zentao.product.domain.PlanRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 计划仓储实现（infra；custom_fields 为 JSON 文本列）。 */
@Component
public class PlanRepositoryImpl implements PlanRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final PlanMapper mapper;
  private final JsonMapper jsonMapper;

  public PlanRepositoryImpl(PlanMapper mapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<Plan> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(this::toDomain);
  }

  @Override
  public List<Plan> findActiveByIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return mapper.selectListByCondition(new QueryColumn("id").in(ids).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<Plan> findChildren(long parentId) {
    return mapper.selectListByCondition(new QueryColumn("parent_id").eq(parentId).and(DELETED_AT.isNull())).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Plan> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  @Override
  public Plan insert(Plan plan) {
    PlanPO po = toPo(plan);
    po.setId(null);
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public Optional<Plan> update(Plan plan) {
    return mapper.update(toPo(plan), false) > 0 ? Optional.of(plan) : Optional.empty();
  }

  @Override
  public int detachChildren(long parentId) {
    return Db.updateByCondition("plan", Row.of("parent_id", 0),
        new QueryColumn("parent_id").eq(parentId).and(DELETED_AT.isNull()));
  }

  @Override
  public boolean existsActiveByProduct(long productId) {
    return mapper.selectCountByCondition(
        new QueryColumn("product_id").eq(productId).and(DELETED_AT.isNull())) > 0;
  }

  @Override
  public void softDelete(long id) {
    SoftDeletes.apply("plan", Row.of("deleted_at", Instant.now()), new QueryColumn("id").eq(id));
  }

  private Plan toDomain(PlanPO po) {
    return new Plan(po.getId(), po.getProductId(), po.getBranchId() == null ? 0 : po.getBranchId(),
        po.getParentId() == null ? 0 : po.getParentId(), po.getTitle(), po.getStatus(), po.getDescription(),
        po.getBeginDate(), po.getEndDate(), po.getFinishedAt(), po.getClosedAt(), po.getClosedReason(),
        readMap(po.getCustomFields()), po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(), po.getUpdatedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private PlanPO toPo(Plan plan) {
    PlanPO po = new PlanPO();
    po.setId(plan.id() == 0 ? null : plan.id());
    po.setProductId(plan.productId());
    po.setBranchId(plan.branchId());
    po.setParentId(plan.parentId());
    po.setTitle(plan.title());
    po.setStatus(plan.status());
    po.setDescription(plan.description());
    po.setBeginDate(plan.beginDate());
    po.setEndDate(plan.endDate());
    po.setFinishedAt(plan.finishedAt());
    po.setClosedAt(plan.closedAt());
    po.setClosedReason(plan.closedReason());
    po.setCustomFields(plan.customFields().isEmpty() ? null : jsonMapper.writeValueAsString(plan.customFields()));
    po.setCreatedBy(plan.createdBy());
    po.setCreatedAt(plan.createdAt());
    po.setUpdatedBy(plan.updatedBy());
    po.setUpdatedAt(plan.updatedAt());
    po.setLockVersion(plan.lockVersion());
    return po;
  }

  private Map<String, Object> readMap(String json) {
    return json == null || json.isBlank() ? Map.of() : jsonMapper.readValue(json, MAP);
  }
}
