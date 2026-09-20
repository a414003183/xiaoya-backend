package net.zentao.project.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.project.domain.TeamMember;
import net.zentao.project.domain.TeamMemberRepository;
import org.springframework.stereotype.Component;

/** 团队成员仓储实现（infra：PO ↔ 领域对象；全量提交的删为软删，同键复活避免 unique 冲突）。 */
@Component
public class TeamMemberRepositoryImpl implements TeamMemberRepository {

  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");

  private final TeamMemberMapper mapper;

  public TeamMemberRepositoryImpl(TeamMemberMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<TeamMember> findActive(String objectType, long objectId) {
    return mapper.selectListByQuery(QueryWrapper.create()
        .where(new QueryColumn("object_type").eq(objectType)
            .and(new QueryColumn("object_id").eq(objectId))
            .and(DELETED_AT.isNull()))
        .orderBy(new QueryColumn("sort").asc(), new QueryColumn("id").asc())) // banned-words-ok：MyBatis-Flex 构造器方法名
        .stream()
        .map(TeamMemberRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  public TeamMember insert(TeamMember member) {
    TeamMemberPO existing = findAnyKey(member.objectType(), member.objectId(), member.account());
    if (existing != null) {
      // 复活软删行：业务字段覆盖，created_by/created_at 保留（审计语义），deleted_at 清空
      existing.setRole(member.role());
      existing.setJoinDate(member.joinDate());
      existing.setDays(member.days());
      existing.setHours(member.hours());
      existing.setSort(member.sort());
      existing.setUpdatedBy(member.updatedBy());
      existing.setUpdatedAt(member.updatedAt());
      existing.setDeletedAt(null);
      mapper.update(existing, false);
      return toDomain(existing);
    }
    TeamMemberPO po = toPo(member);
    po.setId(null);
    mapper.insert(po);
    return toDomain(mapper.selectOneById(po.getId()));
  }

  @Override
  public Optional<TeamMember> update(TeamMember member) {
    TeamMemberPO po = toPo(member);
    if (mapper.update(po, false) <= 0) {
      return Optional.empty();
    }
    return Optional.ofNullable(mapper.selectOneById(po.getId())).map(TeamMemberRepositoryImpl::toDomain);
  }

  @Override
  public void softDelete(long id, String actor) {
    Db.updateByCondition("team_member",
        Row.of("deleted_at", Instant.now()).set("updated_by", actor).set("updated_at", Instant.now()),
        new QueryColumn("id").eq(id));
  }

  @Override
  public Map<String, Set<Long>> objectsOf(String account) {
    Map<String, Set<Long>> byType = new LinkedHashMap<>();
    for (TeamMemberPO po : mapper.selectListByCondition(
        new QueryColumn("account").eq(account).and(DELETED_AT.isNull()))) {
      byType.computeIfAbsent(po.getObjectType(), type -> new LinkedHashSet<>()).add(po.getObjectId());
    }
    return byType;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<TeamMember> queryPage(Object whereWrapper, int offset, int limit) {
    return mapper.selectListByQuery(((QueryWrapper) whereWrapper).limit(offset, limit)).stream()
        .map(TeamMemberRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  public long countByQuery(Object whereWrapper) {
    return mapper.selectCountByQuery((QueryWrapper) whereWrapper);
  }

  private TeamMemberPO findAnyKey(String objectType, long objectId, String account) {
    QueryCondition key = new QueryColumn("object_type").eq(objectType)
        .and(new QueryColumn("object_id").eq(objectId))
        .and(new QueryColumn("account").eq(account));
    return mapper.selectOneByCondition(key);
  }

  private static TeamMember toDomain(TeamMemberPO po) {
    return new TeamMember(po.getId(), po.getObjectType(), po.getObjectId(), po.getAccount(), po.getRole(),
        po.getJoinDate(), po.getDays() == null ? 0 : po.getDays(),
        po.getHours() == null ? BigDecimal.ZERO : po.getHours(), po.getSort() == null ? 0 : po.getSort(),
        po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(), po.getUpdatedAt());
  }

  private static TeamMemberPO toPo(TeamMember member) {
    TeamMemberPO po = new TeamMemberPO();
    po.setId(member.id() == 0 ? null : member.id());
    po.setObjectType(member.objectType());
    po.setObjectId(member.objectId());
    po.setAccount(member.account());
    po.setRole(member.role());
    po.setJoinDate(member.joinDate());
    po.setDays(member.days());
    po.setHours(member.hours());
    po.setSort(member.sort());
    po.setCreatedBy(member.createdBy());
    po.setCreatedAt(member.createdAt());
    po.setUpdatedBy(member.updatedBy());
    po.setUpdatedAt(member.updatedAt());
    return po;
  }
}
