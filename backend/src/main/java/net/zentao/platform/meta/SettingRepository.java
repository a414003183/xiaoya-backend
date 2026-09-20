package net.zentao.platform.meta;

import com.mybatisflex.core.query.QueryColumn;
import java.util.List;
import java.util.Optional;
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
      throw net.zentao.platform.error.ApiException.badRequest("非法设置键：" + flatKey + "（应为 <domain>.<key>）");
    }
    return new String[] {flatKey.substring(0, dot), flatKey.substring(dot + 1)};
  }

  public Optional<SettingPO> find(String owner, String domain, String key) {
    return Optional.ofNullable(mapper.selectOneByCondition(
        OWNER.eq(owner).and(DOMAIN.eq(domain)).and(SECTION.eq("")).and(ITEM_KEY.eq(key))));
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
