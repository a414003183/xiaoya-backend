package net.zentao.project.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.project.domain.Stakeholder;
import net.zentao.project.domain.StakeholderRepository;
import org.springframework.stereotype.Component;

/** 干系人仓储实现（infra：PO ↔ 领域对象；移除为软删，同键再添加复活同键行）。 */
@Component
public class StakeholderRepositoryImpl implements StakeholderRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");

  private final StakeholderMapper mapper;

  public StakeholderRepositoryImpl(StakeholderMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Stakeholder> findActiveById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("id").eq(id).and(DELETED_AT.isNull())))
        .map(StakeholderRepositoryImpl::toDomain);
  }

  @Override
  public Optional<Stakeholder> findActiveByAccount(String objectType, long objectId, String account) {
    return Optional.ofNullable(mapper.selectOneByCondition(key(objectType, objectId, account)
        .and(DELETED_AT.isNull()))).map(StakeholderRepositoryImpl::toDomain);
  }

  @Override
  public Stakeholder insert(Stakeholder stakeholder) {
    StakeholderPO existing = mapper.selectOneByCondition(key(stakeholder.objectType(), stakeholder.objectId(),
        stakeholder.account()));
    if (existing != null) {
      // 软删行复活：提交值覆盖，created_by/created_at 保留（审计语义），deleted_at 清空
      existing.setType(stakeholder.type());
      existing.setIsKey(stakeholder.isKey() ? 1 : 0);
      existing.setSource(stakeholder.source());
      existing.setUpdatedBy(stakeholder.updatedBy());
      existing.setUpdatedAt(stakeholder.updatedAt());
      existing.setDeletedAt(null);
      mapper.update(existing, false);
      return toDomain(existing);
    }
    StakeholderPO po = toPo(stakeholder);
    po.setId(null);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public void softDelete(long id, String actor) {
    Db.updateByCondition("stakeholder",
        Row.of("deleted_at", Instant.now()).set("updated_by", actor).set("updated_at", Instant.now()),
        new QueryColumn("id").eq(id));
  }

  @Override
  public Map<String, Set<Long>> objectsOf(String account) {
    Map<String, Set<Long>> byType = new LinkedHashMap<>();
    for (StakeholderPO po : mapper.selectListByCondition(
        new QueryColumn("account").eq(account).and(DELETED_AT.isNull()))) {
      byType.computeIfAbsent(po.getObjectType(), type -> new LinkedHashSet<>()).add(po.getObjectId());
    }
    return byType;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Stakeholder> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(StakeholderRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  private static QueryCondition key(String objectType, long objectId, String account) {
    return new QueryColumn("object_type").eq(objectType)
        .and(new QueryColumn("object_id").eq(objectId))
        .and(new QueryColumn("account").eq(account));
  }

  private static Stakeholder toDomain(StakeholderPO po) {
    return new Stakeholder(po.getId(), po.getObjectType(), po.getObjectId(), po.getAccount(), po.getType(),
        po.getIsKey() != null && po.getIsKey() == 1, po.getSource(), po.getCreatedBy(), po.getCreatedAt(),
        po.getUpdatedBy(), po.getUpdatedAt());
  }

  private static StakeholderPO toPo(Stakeholder stakeholder) {
    StakeholderPO po = new StakeholderPO();
    po.setId(stakeholder.id() == 0 ? null : stakeholder.id());
    po.setObjectType(stakeholder.objectType());
    po.setObjectId(stakeholder.objectId());
    po.setAccount(stakeholder.account());
    po.setType(stakeholder.type());
    po.setIsKey(stakeholder.isKey() ? 1 : 0);
    po.setSource(stakeholder.source());
    po.setCreatedBy(stakeholder.createdBy());
    po.setCreatedAt(stakeholder.createdAt());
    po.setUpdatedBy(stakeholder.updatedBy());
    po.setUpdatedAt(stakeholder.updatedAt());
    return po;
  }
}
