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

  public DictRegistry(net.zentao.platform.rbac.PrivilegeCatalog catalog) {
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
        Map.of("value", "Asia/Shanghai", "label", "(GMT+08:00) 北京"),
        Map.of("value", "Asia/Taipei", "label", "(GMT+08:00) 台北"),
        Map.of("value", "Asia/Tokyo", "label", "(GMT+09:00) 东京"),
        Map.of("value", "Asia/Singapore", "label", "(GMT+08:00) 新加坡"),
        Map.of("value", "Europe/London", "label", "(GMT+00:00) 伦敦"),
        Map.of("value", "Europe/Berlin", "label", "(GMT+01:00) 柏林"),
        Map.of("value", "America/New_York", "label", "(GMT-05:00) 纽约"),
        Map.of("value", "America/Los_Angeles", "label", "(GMT-08:00) 洛杉矶"),
        Map.of("value", "UTC", "label", "(GMT+00:00) UTC"))));
    register(builtin("locales", List.of(
        Map.of("value", "zh-cn", "label", "简体中文"),
        Map.of("value", "zh-tw", "label", "繁體中文"),
        Map.of("value", "en", "label", "English"))));
  }

  public void register(DictProvider provider) {
    providers.put(provider.name(), provider);
  }

  public Optional<DictProvider> get(String name) {
    return Optional.ofNullable(providers.get(name));
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
