package net.zentao.org.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.util.List;
import java.util.Optional;
import net.zentao.org.domain.Role;
import net.zentao.org.domain.RoleAcl;
import net.zentao.org.domain.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 角色仓储实现（infra；级联删除成员与权限码行；acl 为 JSON 文本列，参照 product.whitelist 范式）。 */
@Component
public class RoleRepositoryImpl implements RoleRepository {

  private static final Logger log = LoggerFactory.getLogger(RoleRepositoryImpl.class);

  private final RoleMapper mapper;
  private final JsonMapper jsonMapper;

  public RoleRepositoryImpl(RoleMapper mapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<Role> findById(long id) {
    return Optional.ofNullable(mapper.selectOneById(id)).map(this::toDomain);
  }

  @Override
  public Optional<Role> findByName(String name) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("name").eq(name))).map(this::toDomain);
  }

  @Override
  public List<Role> findAll() {
    return mapper.selectListByQuery(QueryWrapper.create()
        .orderBy(new QueryColumn("sort").asc(), new QueryColumn("id").asc())) // banned-words-ok：MyBatis-Flex 构造器方法名
        .stream().map(this::toDomain).toList();
  }

  @Override
  public Role insert(Role role) {
    RolePO po = new RolePO();
    po.setCode(role.code());
    po.setName(role.name());
    po.setDescription(role.description() == null ? "" : role.description());
    po.setAcl(writeAcl(role.acl()));
    po.setBuiltin(role.builtin() ? 1 : 0);
    po.setSort(role.sort());
    po.setCreatedBy(role.createdBy());
    po.setCreatedAt(role.createdAt() == null ? java.time.Instant.now() : role.createdAt());
    po.setLockVersion(0);
    mapper.insert(po);
    return toDomain(po);
  }

  @Override
  public Optional<Role> update(Role role) {
    RolePO po = new RolePO();
    po.setId(role.id());
    po.setName(role.name());
    po.setDescription(role.description());
    po.setSort(role.sort());
    // acl 恒写 JSON（空集为全空键对象）：整体替换含「清空」，部分更新跳 null 会吞掉清空语义
    po.setAcl(writeAcl(role.acl()));
    po.setUpdatedBy(role.updatedBy());
    po.setUpdatedAt(role.updatedAt());
    po.setLockVersion(role.lockVersion());
    if (mapper.update(po) <= 0) {
      return Optional.empty();
    }
    // 回读真行（lockVersion 由库内自增，返回内存聚会给过期版本——project/task 同口径）
    return Optional.ofNullable(mapper.selectOneById(role.id())).map(this::toDomain);
  }

  @Override
  public void delete(long id) {
    Db.deleteByCondition("user_role", new QueryColumn("role_id").eq(id));
    Db.deleteByCondition("role_priv", new QueryColumn("role_id").eq(id));
    mapper.deleteById(id);
  }

  @Override
  public List<Long> memberIdsOf(long roleId) {
    return Db.selectListByCondition("user_role", new QueryColumn("role_id").eq(roleId)).stream()
        .map(row -> row.getLong("account_id"))
        .toList();
  }

  @Override
  public void replaceMembers(long roleId, List<Long> accountIds) {
    Db.deleteByCondition("user_role", new QueryColumn("role_id").eq(roleId));
    for (Long accountId : accountIds) {
      Db.insert("user_role", Row.of("account_id", accountId).set("role_id", roleId));
    }
  }

  @Override
  public List<String> privCodesOf(long roleId) {
    return privCodesOfRoles(List.of(roleId));
  }

  @Override
  public List<String> privCodesOfRoles(List<Long> roleIds) {
    if (roleIds.isEmpty()) {
      return List.of();
    }
    return Db.selectListByCondition("role_priv", new QueryColumn("role_id").in(roleIds)).stream()
        .map(row -> row.getString("priv_code"))
        .filter(code -> code != null && !code.isBlank())
        .distinct()
        .toList();
  }

  @Override
  public void replacePrivCodes(long roleId, List<String> codes) {
    Db.deleteByCondition("role_priv", new QueryColumn("role_id").eq(roleId));
    for (String code : codes) {
      Db.insert("role_priv", Row.of("role_id", roleId).set("priv_code", code));
    }
  }

  @Override
  public long countPrivCodes(long roleId) {
    return Db.selectCountByCondition("role_priv", new QueryColumn("role_id").eq(roleId));
  }

  @Override
  public long countMembers(long roleId) {
    return Db.selectCountByCondition("user_role", new QueryColumn("role_id").eq(roleId));
  }

  private Role toDomain(RolePO po) {
    return new Role(po.getId(), po.getCode(), po.getName(), po.getDescription(), readAcl(po.getId(), po.getAcl()),
        po.getBuiltin() != null && po.getBuiltin() == 1, po.getSort() == null ? 0 : po.getSort(),
        po.getCreatedBy(), po.getCreatedAt(), po.getUpdatedBy(), po.getUpdatedAt(),
        po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  /**
   * 坏 ACL JSON → 空 ACL（读路径不炸；T57/BE-10 起 WARN 留痕）：静默吞掉会让权限**悄悄变窄**
   * （acl 是追加可见集），运维与排障都看不见。
   */
  private RoleAcl readAcl(Long roleId, String json) {
    if (json == null || json.isBlank()) {
      return RoleAcl.EMPTY;
    }
    try {
      return jsonMapper.readValue(json, RoleAcl.class);
    } catch (Exception e) {
      log.atWarn().setCause(e).log("role.acl 解析失败，按空 ACL 处理 roleId={}", roleId);
      return RoleAcl.EMPTY;
    }
  }

  private String writeAcl(RoleAcl acl) {
    return jsonMapper.writeValueAsString(acl == null ? RoleAcl.EMPTY : acl);
  }
}
