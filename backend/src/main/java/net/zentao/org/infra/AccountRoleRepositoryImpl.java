package net.zentao.org.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.org.domain.AccountRole;
import net.zentao.org.domain.AccountRoleRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 账号角色字典仓储实现（infra；labels 走 JSON 文本列，读写口径同 auth_group.acl）。 */
@Component
public class AccountRoleRepositoryImpl implements AccountRoleRepository {

  private static final TypeReference<LinkedHashMap<String, String>> LABELS_TYPE = new TypeReference<>() {};

  private final AccountRoleMapper mapper;
  private final JsonMapper jsonMapper;

  public AccountRoleRepositoryImpl(AccountRoleMapper mapper, JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<AccountRole> findByCode(String code) {
    return Optional.ofNullable(mapper.selectOneByCondition(new QueryColumn("code").eq(code)))
        .map(this::toDomain);
  }

  @Override
  public boolean existsByCode(String code) {
    return mapper.selectCountByCondition(new QueryColumn("code").eq(code)) > 0;
  }

  @Override
  public List<AccountRole> findAll() {
    QueryWrapper query = QueryWrapper.create()
        .from("account_role")
        .orderBy("sort", true) // banned-words-ok：MyBatis-Flex 构造器方法名
        .orderBy("code", true); // banned-words-ok：MyBatis-Flex 构造器方法名
    return mapper.selectListByQuery(query).stream().map(this::toDomain).toList();
  }

  @Override
  public AccountRole insert(AccountRole role) {
    AccountRolePO po = new AccountRolePO();
    po.setCode(role.code());
    po.setLabels(writeLabels(role.labels()));
    po.setSort(role.sort());
    po.setBuiltin(role.builtin() ? 1 : 0);
    mapper.insert(po);
    return toDomain(po);
  }

  @Override
  public Optional<AccountRole> update(AccountRole role) {
    // 业务键是 code，但 MyBatis-Flex 的 update(entity) 按 @Id 定位（且靠它带乐观锁）；
    // 故先按 code 取行拿 id，再走主键更新（口径同 GroupRepositoryImpl 的"主键更新 + 回读"）。
    AccountRolePO existing = mapper.selectOneByCondition(new QueryColumn("code").eq(role.code()));
    if (existing == null) {
      return Optional.empty();
    }
    AccountRolePO po = new AccountRolePO();
    po.setId(existing.getId());
    po.setCode(role.code());
    po.setLabels(writeLabels(role.labels()));
    po.setSort(role.sort());
    po.setUpdatedBy(role.updatedBy());
    po.setLockVersion(role.lockVersion());
    if (mapper.update(po) <= 0) {
      return Optional.empty();
    }
    // 回读真行（lockVersion 由库内自增；不回读会把过期版本返回给前端）
    return findByCode(role.code());
  }

  @Override
  public void delete(String code) {
    mapper.deleteByCondition(new QueryColumn("code").eq(code));
  }

  @Override
  public int nextSort() {
    return findAll().stream().mapToInt(AccountRole::sort).max().orElse(0) + 10;
  }

  private AccountRole toDomain(AccountRolePO po) {
    return new AccountRole(po.getCode(), readLabels(po.getLabels()), po.getSort() == null ? 0 : po.getSort(),
        po.getBuiltin() != null && po.getBuiltin() == 1, po.getLockVersion() == null ? 0 : po.getLockVersion());
  }

  private Map<String, String> readLabels(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return jsonMapper.readValue(json, LABELS_TYPE);
    } catch (Exception e) {
      // 坏数据视为空标签（不炸读路径；写路径已由 normalize 保证形状）
      return Map.of();
    }
  }

  private String writeLabels(Map<String, String> labels) {
    try {
      return jsonMapper.writeValueAsString(labels == null ? Map.of() : labels);
    } catch (Exception e) {
      throw new IllegalStateException("角色名写入失败", e);
    }
  }
}
