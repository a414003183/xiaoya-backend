package net.zentao.platform.meta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 设置读取（platform 卡 §5）：keys=csv 扁平键，不存在的键不出现；owner=账号 的仅本人可见。 */
@Component
public class SettingQueryService {

  private final SettingRepository repository;
  private final JsonMapper jsonMapper;

  public SettingQueryService(SettingRepository repository, JsonMapper jsonMapper) {
    this.repository = repository;
    this.jsonMapper = jsonMapper;
  }

  public Map<String, Object> get(SessionPrincipal principal, List<String> flatKeys) {
    List<String[]> parts = flatKeys.stream().map(SettingRepository::splitKey).toList();
    Map<String, Object> result = new LinkedHashMap<>();
    for (SettingPO po : repository.findByKeys(principal.account(), parts)) {
      String flatKey = po.getDomain() + "." + po.getItemKey();
      // 个人级行覆盖同键系统行
      if (!result.containsKey(flatKey) || principal.account().equals(po.getOwner())) {
        result.put(flatKey, po.getItemValue() == null ? null : jsonMapper.readTree(po.getItemValue()));
      }
    }
    return result;
  }
}
