package net.zentao.platform.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 计算字典注册表（platform 卡 §3.9）：内建 timezones/locales；
 * accounts/departments 由 org 域反向注册（A3 不被破坏：org 主动调用 platform 的注册口）；
 * privileges 内建条目由 PrivilegeCatalog 提供（T-13 接入）。
 */
@Component
public class DictRegistry {

  private final Map<String, DictProvider> providers = new ConcurrentHashMap<>();
  private final DictRepository dbTypes;

  public DictRegistry(net.zentao.platform.rbac.PrivilegeCatalog catalog, DictRepository dbTypes) {
    this.dbTypes = dbTypes;
    // privileges 字典（platform 卡 §3.9）：权限码目录 = PrivilegeCatalog 全集并集，org 矩阵页数据源
    register(new DictProvider() {
      @Override
      public String name() {
        return "privileges";
      }

      @Override
      public List<Map<String, Object>> items() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (net.zentao.platform.rbac.PrivilegeCatalog.Entry entry : catalog.entries()) {
          items.add(Map.of("code", entry.code(), "domain", entry.domain(), "i18n", "priv." + entry.code()));
        }
        return items;
      }
    });
    register(builtin("timezones", List.of(
        Map.of("value", "Asia/Shanghai", "i18n", "dict.timezone.Asia.Shanghai"),
        Map.of("value", "Asia/Taipei", "i18n", "dict.timezone.Asia.Taipei"),
        Map.of("value", "Asia/Tokyo", "i18n", "dict.timezone.Asia.Tokyo"),
        Map.of("value", "Asia/Singapore", "i18n", "dict.timezone.Asia.Singapore"),
        Map.of("value", "Europe/London", "i18n", "dict.timezone.Europe.London"),
        Map.of("value", "Europe/Berlin", "i18n", "dict.timezone.Europe.Berlin"),
        Map.of("value", "America/New_York", "i18n", "dict.timezone.America.New_York"),
        Map.of("value", "America/Los_Angeles", "i18n", "dict.timezone.America.Los_Angeles"),
        Map.of("value", "UTC", "i18n", "dict.timezone.UTC"))));
    register(builtin("locales", List.of(
        Map.of("value", "zh-cn", "i18n", "dict.locale.zh-cn"),
        Map.of("value", "zh-tw", "i18n", "dict.locale.zh-tw"),
        Map.of("value", "en", "i18n", "dict.locale.en"))));
  }

  public void register(DictProvider provider) {
    providers.put(provider.name(), provider);
  }

  /** 是否为代码注册的字典名（T16：DB 类型不得撞这些名，否则永远查不到——查找是先注册表后 DB）。 */
  public boolean isRegistered(String name) {
    return providers.containsKey(name);
  }

  /**
   * 查字典：**代码注册优先**，未注册时回落到 DB 字典（T16 P1-4）。
   * 回落条目形 `{value, label}`（管理员在字典页填的字面文案），与内建的 `{value, i18n}` 并存——
   * 前端 `dictOptions` 两种都认。
   */
  public Optional<DictProvider> get(String name) {
    DictProvider registered = providers.get(name);
    if (registered != null) {
      return Optional.of(registered);
    }
    return dbTypes.findType(name)
        .filter(type -> DictRepository.ACTIVE.equals(type.getStatus()))
        .map(type -> dbProvider(type.getCode()));
  }

  private DictProvider dbProvider(String code) {
    return new DictProvider() {
      @Override
      public String name() {
        return code;
      }

      @Override
      public List<Map<String, Object>> items() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DictDataPO po : dbTypes.listActiveData(code)) {
          rows.add(Map.of("value", po.getItemValue(), "label", po.getItemLabel()));
        }
        return rows;
      }
    };
  }

  private static DictProvider builtin(String name, List<Map<String, Object>> items) {
    return new DictProvider() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public List<Map<String, Object>> items() {
        return items;
      }
    };
  }
}
