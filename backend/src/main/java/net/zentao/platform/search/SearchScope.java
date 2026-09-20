package net.zentao.platform.search;

import java.util.List;
import net.zentao.platform.session.SessionPrincipal;

/**
 * 搜索 scope 注册项（platform 卡 §5.2）：域声明 searchable 字段 + 提供执行器。
 * 执行器由域侧实现（platform 不认识业务域，A3），域侧负责先注入 DataScope 再 LIKE。
 */
public record SearchScope(String objectType, List<String> searchableColumns, Executor executor) {

  public SearchScope {
    searchableColumns = List.copyOf(searchableColumns);
  }

  /** 域侧搜索执行器：q 为原文（非 LIKE 串），limit 为单 scope 上限。 */
  public interface Executor {
    List<SearchResultView> search(String q, int limit, SessionPrincipal principal);
  }
}
