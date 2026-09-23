package net.zentao.platform.meta;

import com.mybatisflex.core.query.QueryColumn;
import java.util.List;
import java.util.Optional;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import org.springframework.stereotype.Component;

/** setting 表读写（platform 卡 §3.7）。扁平寻址 `<domain>.<key>`：section 参与存储（恒 ''）不参与寻址。 */
@Component
public class SettingRepository {

  static final QueryColumn OWNER = new QueryColumn("owner");
  static final QueryColumn DOMAIN = new QueryColumn("domain");
  static final QueryColumn SECTION = new QueryColumn("section");
  static final QueryColumn ITEM_KEY = new QueryColumn("item_key");

  public static final String SYSTEM_OWNER = "system";

  private final SettingMapper mapper;

  public SettingRepository(SettingMapper mapper) {
    this.mapper = mapper;
  }

  /** 拆扁平键（首个 '.' 分隔）。 */
  public static String[] splitKey(String flatKey) {
    int dot = flatKey.indexOf('.');
    if (dot <= 0 || dot == flatKey.length() - 1) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "setting.key.invalid", flatKey);
    }
    return new String[] {flatKey.substring(0, dot), flatKey.substring(dot + 1)};
  }

  public Optional<SettingPO> find(String owner, String domain, String key) {
    return Optional.ofNullable(mapper.selectOneByCondition(
        OWNER.eq(owner).and(DOMAIN.eq(domain)).and(SECTION.eq("")).and(ITEM_KEY.eq(key))));
  }

  /** 系统行查询（参数管理页 T15：这个页面只管 owner=system 的行，个人偏好行不进）。 */
  public Optional<SettingPO> findSystem(String domain, String key) {
    return find(SYSTEM_OWNER, domain, key);
  }

  /** 参数管理页分页（T15）。 */
  public List<SettingPO> page(com.mybatisflex.core.query.QueryWrapper query, int offset, int limit) {
    return mapper.selectListByQuery(query.limit(offset, limit));
  }

  public long countByQuery(com.mybatisflex.core.query.QueryWrapper query) {
    return mapper.selectCountByQuery(query);
  }

  /** 删系统行（T15）；返回删除行数（0 = 本来就没有，由调用方裁决 404）。 */
  public int deleteSystem(String domain, String key) {
    return mapper.deleteByCondition(
        OWNER.eq(SYSTEM_OWNER).and(DOMAIN.eq(domain)).and(SECTION.eq("")).and(ITEM_KEY.eq(key)));
  }

  public List<SettingPO> findByKeys(String personalOwner, List<String[]> domainAndKeys) {
    if (domainAndKeys.isEmpty()) {
      return List.of();
    }
    return mapper.selectListByCondition(OWNER.in(List.of(personalOwner, SYSTEM_OWNER))
        .and(DOMAIN.in(domainAndKeys.stream().map(parts -> parts[0]).toList()))
        .and(SECTION.eq(""))
        .and(ITEM_KEY.in(domainAndKeys.stream().map(parts -> parts[1]).toList())));
  }

  /** 语义：个人键只查本人，系统键查 system；同键两行时个人优先由查询服务裁决。 */
  public void upsert(String owner, String domain, String key, String jsonValue) {
    SettingPO existing = find(owner, domain, key).orElse(null);
    if (existing == null) {
      SettingPO po = new SettingPO();
      po.setOwner(owner);
      po.setDomain(domain);
      po.setSection("");
      po.setItemKey(key);
      po.setItemValue(jsonValue);
      mapper.insert(po);
      return;
    }
    existing.setItemValue(jsonValue);
    mapper.update(existing);
  }
}
