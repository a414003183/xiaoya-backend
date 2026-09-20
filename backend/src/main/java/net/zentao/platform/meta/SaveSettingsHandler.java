package net.zentao.platform.meta;

import java.util.LinkedHashMap;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.PrivilegeChecker;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 设置批量写（platform 卡 §7.2）：notify.* 个人级 owner=@me 无需权限码；
 * 其余为系统键 owner=system，需 setting-manage，无码 → 40301。
 */
@Component
public class SaveSettingsHandler {

  static final String PERSONAL_PREFIX = "notify.";

  private final SettingRepository repository;
  private final PrivilegeChecker checker;
  private final JsonMapper jsonMapper;

  public SaveSettingsHandler(SettingRepository repository, PrivilegeChecker checker, JsonMapper jsonMapper) {
    this.repository = repository;
    this.checker = checker;
    this.jsonMapper = jsonMapper;
  }

  @Transactional
  public Map<String, Object> save(SessionPrincipal principal, Map<String, Object> settings) {
    boolean hasManage = checker.hasPrivilege(principal, "setting-manage");
    Map<String, Object> saved = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : settings.entrySet()) {
      String flatKey = entry.getKey();
      String[] parts = SettingRepository.splitKey(flatKey);
      boolean personal = flatKey.startsWith(PERSONAL_PREFIX);
      if (!personal && !hasManage) {
        throw ApiException.forbidden("缺少权限码 setting-manage。");
      }
      String owner = personal ? principal.account() : SettingRepository.SYSTEM_OWNER;
      String json = entry.getValue() == null ? null : jsonMapper.writeValueAsString(entry.getValue());
      repository.upsert(owner, parts[0], parts[1], json);
      saved.put(flatKey, entry.getValue());
    }
    return saved;
  }
}
