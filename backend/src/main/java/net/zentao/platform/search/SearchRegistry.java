package net.zentao.platform.search;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * scope 白名单注册表（platform 卡 §5.2）：DataScope 注入先于 LIKE；白名单外 → 40001。
 * 各域在启动期 register 自己的 scope（含执行器）；白名单内未注册的 scope 返回空集。
 */
@Component
public class SearchRegistry {

  /** 固定 scope 词表（platform 卡 §5.2）；注册表 = 各域登记的可查字段映射。 */
  private static final Set<String> KNOWN_SCOPES = Set.of("story", "epic", "requirement", "task", "bug", "testCase", "doc", "todo");

  private final Map<String, SearchScope> scopes = new ConcurrentHashMap<>();

  public void register(SearchScope scope) {
    if (!KNOWN_SCOPES.contains(scope.objectType())) {
      throw new IllegalArgumentException("未知搜索 scope：" + scope.objectType());
    }
    scopes.put(scope.objectType(), scope);
  }

  /** 白名单外 → 40001；白名单内未注册 → 空集（P1 语义）。 */
  public SearchScope requireScope(String scope) {
    if (!KNOWN_SCOPES.contains(scope)) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "search.scope.unregistered", scope);
    }
    return scopes.get(scope);
  }

  public List<String> registeredScopes() {
    return List.copyOf(scopes.keySet());
  }

  /** 已注册 scope 全集（无 scope 参数的全局搜索用；未注册的 scope 不参与）。 */
  public List<SearchScope> all() {
    return List.copyOf(scopes.values());
  }

  public Optional<SearchScope> get(String scope) {
    return Optional.ofNullable(scopes.get(scope));
  }
}
