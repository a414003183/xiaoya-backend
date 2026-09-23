package net.zentao.platform.rbac;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 权限码注册式编目（platform 卡 §7.1）：各域向此登记自己的权限码全集；
 * 供矩阵写校验（未注册码 → 42201）与 GET /dicts/privileges（org 矩阵页数据源）。
 */
@Component
public class PrivilegeCatalog {

  private final Map<String, List<String>> codesByDomain = new LinkedHashMap<>();

  public PrivilegeCatalog() {
    // 平台域自带码（platform 卡 §7.1）
    register("file", List.of("file-upload"));
    register("setting", List.of("setting-manage"));
    register("lang", List.of("lang-manage"));
    register("audit", List.of("audit-log-view"));
    // T13 P1-1：在线用户（看列表 / 强退分开授权——只读审计员不必能踢人）
    register("online-user", List.of("online-user-view", "online-user-kick"));
    // T14 P1-2：运行时接口文档（/v3/api-docs、/swagger-ui 的访问码）
    register("api-doc", List.of("api-doc-view"));
    // T17 P1-5：服务监控（只读负载快照）
    register("monitor", List.of("monitor-view"));
    // T19 P2-1：菜单管理（合并视图 + DB 菜单行的增删改）
    register("menu", List.of("menu-manage"));
  }

  /** 登记域权限码（幂等合并，保持顺序）。 */
  public synchronized void register(String domain, List<String> codes) {
    List<String> merged = new ArrayList<>(codesByDomain.getOrDefault(domain, List.of()));
    for (String code : codes) {
      if (!merged.contains(code)) {
        merged.add(code);
      }
    }
    codesByDomain.put(domain, merged);
  }

  public synchronized List<String> allCodes() {
    return codesByDomain.values().stream().flatMap(List::stream).toList();
  }

  public synchronized boolean isRegistered(String code) {
    return codesByDomain.values().stream().anyMatch(codes -> codes.contains(code));
  }

  /** 目录项（code + 所属域），dicts/privileges 条目形。 */
  public record Entry(String code, String domain) {}

  public synchronized List<Entry> entries() {
    List<Entry> entries = new ArrayList<>();
    for (Map.Entry<String, List<String>> byDomain : codesByDomain.entrySet()) {
      for (String code : byDomain.getValue()) {
        entries.add(new Entry(code, byDomain.getKey()));
      }
    }
    return entries;
  }
}
