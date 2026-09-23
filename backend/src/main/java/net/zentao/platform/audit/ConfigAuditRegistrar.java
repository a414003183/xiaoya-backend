package net.zentao.platform.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.platform.meta.DictRepository;
import net.zentao.platform.meta.SettingRepository;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * 参数与字典资源的审计登记（T10 / VISION 事项 4 第 3 行「配置/开关/密钥变更 → 字段级 diff」）。
 *
 * <p>为什么注册点写在 {@code platform.audit} 而不是 {@code platform.meta}：platform 关注点之间不许成环
 * （ArchUnit A2），而 {@code audit → i18n} 与 {@code i18n → meta} 两条既有边在那里，{@code meta → audit}
 * 会立刻把环闭上（实测）。故 platform 自身资源（菜单/字典/参数）的登记表统一放在 audit 侧，
 * **边方向恒为 audit → 资源包**，与业务域的「域 → audit」方向相反但同属一个无环图。
 */
@Configuration
public class ConfigAuditRegistrar {

  public ConfigAuditRegistrar(AuditCatalog auditCatalog, AuditSnapshotRegistry auditSnapshots,
      SettingRepository settingRepository, DictRepository dictRepository, JsonMapper jsonMapper) {
    // 设置项的键名是**数据**（mail.password、common.timezone…），写不进动作级的 keyFields，
    // 故留空 —— 比对字段取 provider 给出的键（即这条设置自己的 key 名），同名才谈得上 diff
    auditCatalog.register("setting-entry-create", AuditCategory.CONFIG, AuditLevel.FULL, List.of(), false);
    auditCatalog.register("setting-entry-update", AuditCategory.CONFIG, AuditLevel.FULL, List.of(), false);
    auditCatalog.register("setting-entry-delete", AuditCategory.CONFIG, AuditLevel.FULL, List.of(), false);
    // PUT /settings 是整页批量写（一次落多个键，还含 owner=本人 的个人偏好行）：只记「谁批量改了设置」，不逐键 diff
    auditCatalog.register("setting-update", AuditCategory.CONFIG, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("dict-type-create", AuditCategory.CONFIG, AuditLevel.FULL,
        List.of("code", "name", "status"), false);
    auditCatalog.register("dict-type-update", AuditCategory.CONFIG, AuditLevel.FULL,
        List.of("code", "name", "status"), false);
    auditCatalog.register("dict-type-delete", AuditCategory.CONFIG, AuditLevel.FULL,
        List.of("code", "name", "status"), false);
    auditCatalog.register("dict-item-create", AuditCategory.CONFIG, AuditLevel.FULL,
        List.of("itemLabel", "itemValue", "sortNo", "status"), false);
    auditCatalog.register("dict-item-update", AuditCategory.CONFIG, AuditLevel.FULL,
        List.of("itemLabel", "itemValue", "sortNo", "status"), false);
    auditCatalog.register("dict-item-delete", AuditCategory.CONFIG, AuditLevel.FULL,
        List.of("itemLabel", "itemValue", "sortNo", "status"), false);

    // 快照 provider：**每次返回新 Map**（框架留着 before 再取 after 比对，同一个可变 Map 会让 diff 恒为空）；
    // 字段用 LinkedHashMap 装（Map.of 不收 null，而 sortNo/icon 这类字段可以为空）
    // 键寻址：设置项的「主键」是 <domain>.<itemKey> 扁平键、字典类型的是 code，都进不了 BIGINT 的 object_id
    auditSnapshots.registerKeyed("settingEntry", flatKey -> {
      String[] parts = SettingRepository.splitKey(flatKey);
      return settingRepository.findSystem(parts[0], parts[1])
          .map(po -> {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            // 字段名 = 设置项自己的 key 名：既让审计看得出改的是哪一项，
            // 也让 mail.password 这类名字自动命中框架的敏感名表（值掩码 ***）
            snapshot.put(flatKey, po.getItemValue() == null ? null : jsonMapper.readTree(po.getItemValue()));
            return snapshot;
          })
          .orElse(null);
    });
    auditSnapshots.registerKeyed("dictType", code -> dictRepository.findType(code)
        .map(po -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("code", po.getCode());
          snapshot.put("name", po.getName());
          snapshot.put("status", po.getStatus());
          return snapshot;
        })
        .orElse(null));
    auditSnapshots.register("dictItem", id -> dictRepository.findData(id)
        .map(po -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          // 字典数据项存的是字面文案与存储值，都是关键字段（类型归属改不了，不进比对集）
          snapshot.put("itemLabel", po.getItemLabel());
          snapshot.put("itemValue", po.getItemValue());
          snapshot.put("sortNo", po.getSortNo());
          snapshot.put("status", po.getStatus());
          return snapshot;
        })
        .orElse(null));
  }
}
