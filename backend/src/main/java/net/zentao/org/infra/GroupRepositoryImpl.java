package net.zentao.org.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.util.List;
import java.util.Optional;
import net.zentao.org.domain.Group;
import net.zentao.org.domain.GroupAcl;
import net.zentao.org.domain.GroupRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 权限组仓储实现（infra；级联删除成员与矩阵行；acl 为 JSON 文本列，参照 product.whitelist 范式）。 */
@Component
public class GroupRepositoryImpl implements GroupRepository {

  private final GroupMapper mapper;
  private final JsonMapper jsonMapper;

  public GroupRepositoryImpl(GroupMapper mapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<Group> findById(long id) {
    return Optional.ofNullable(mapper.selectOneById(id)).map(this::toDomain);
  }

  @Override
  public Optional<Group> findByName(String name) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("name").eq(name)))
        .map(this::toDomain);
  }

  @Override
  public List<Group> findAll() {
    return mapper.selectAll().stream().map(this::toDomain).toList();
  }

  @Override
  public Group insert(Group group) {
    GroupPO po = new GroupPO();
    po.setName(group.name());
    po.setDescription(group.description() == null ? "" : group.description());
    po.setAcl(writeAcl(group.acl()));
    po.setCreatedBy(group.createdBy());
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(po);
  }

  @Override
  public Optional<Group> update(Group group) {
    GroupPO po = new GroupPO();
    po.setId(group.id());
    po.setName(group.name());
    po.setDescription(group.description());
    // acl 恒写 JSON（空集为全空键对象）：整体替换含"清空"，部分更新跳 null 会吞掉清空语义
    po.setAcl(writeAcl(group.acl()));
    po.setLockVersion(group.lockVersion());
    if (mapper.update(po) <= 0) {
      return Optional.empty();
    }
    // 回读真行（lockVersion 由库内自增，返回内存聚会给过期版本——project/task 同口径）
    return Optional.ofNullable(mapper.selectOneById(group.id())).map(this::toDomain);
  }

  @Override
  public void delete(long id) {
    Db.deleteByCondition("user_group", new QueryColumn("group_id").eq(id));
    Db.deleteByCondition("group_priv", new QueryColumn("group_id").eq(id));
    mapper.deleteById(id);
  }

  @Override
  public List<Long> memberIdsOf(long groupId) {
    return Db.selectListByCondition("user_group", new QueryColumn("group_id").eq(groupId)).stream()
        .map(row -> row.getLong("account_id"))
        .toList();
  }

  @Override
  public void replaceMembers(long groupId, List<Long> accountIds) {
    Db.deleteByCondition("user_group", new QueryColumn("group_id").eq(groupId));
    for (Long accountId : accountIds) {
      Db.insert("user_group", Row.of("account_id", accountId).set("group_id", groupId));
    }
  }

  @Override
  public List<String> privCodesOf(long groupId) {
    return Db.selectListByCondition("group_priv", new QueryColumn("group_id").eq(groupId)).stream()
        .map(row -> row.getString("priv_code"))
        .toList();
  }

  @Override
  public void replacePrivCodes(long groupId, List<String> codes) {
    Db.deleteByCondition("group_priv", new QueryColumn("group_id").eq(groupId));
    for (String code : codes) {
      Db.insert("group_priv", Row.of("group_id", groupId).set("priv_code", code));
    }
  }

  private Group toDomain(GroupPO po) {
    return new Group(po.getId(), po.getName(), po.getDescription(), readAcl(po.getAcl()), po.getCreatedBy(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private GroupAcl readAcl(String json) {
    if (json == null || json.isBlank()) {
      return GroupAcl.EMPTY;
    }
    try {
      return jsonMapper.readValue(json, GroupAcl.class);
    } catch (Exception e) {
      // 与 DataScope.AclParser 同口径：坏数据视为空，不炸读路径
      return GroupAcl.EMPTY;
    }
  }

  private String writeAcl(GroupAcl acl) {
    return jsonMapper.writeValueAsString(acl == null ? GroupAcl.EMPTY : acl);
  }
}
