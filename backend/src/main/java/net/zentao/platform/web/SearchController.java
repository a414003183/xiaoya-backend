package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.ratelimit.RateLimits;
import net.zentao.platform.search.SearchRegistry;
import net.zentao.platform.search.SearchResultView;
import net.zentao.platform.search.SearchScope;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.session.SessionResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /search（platform 卡 §5.2）：q 必填 1–100；scope 白名单外 40001；
 * 逐 scope 调用域侧执行器（域侧先注 DataScope 再 LIKE），合并后按 updatedAt 倒序分页。
 */
@RestController
@RequestMapping("/api/v1")
public class SearchController {

  private final SearchRegistry registry;
  private final SessionResolver resolver;
  private final RateLimits rateLimits;

  public SearchController(SearchRegistry registry, SessionResolver resolver, RateLimits rateLimits) {
    this.registry = registry;
    this.resolver = resolver;
    this.rateLimits = rateLimits;
  }

  @GetMapping("/search")
  @Operation(operationId = "globalSearch")
  public DataEnvelope<SearchResultList> search(
      @RequestParam String q,
      @RequestParam(required = false) String scope,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int limit,
      jakarta.servlet.http.HttpServletRequest request) {
    SessionPrincipal principal = resolver.resolve(request);
    // T59 SEC-07：逐 scope 打各域 LIKE，按账号计窗（超限 42901）
    rateLimits.requireAllowed(RateLimits.Scope.search, principal.account());
    if (q.isBlank() || q.length() > 100) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "search.query.required");
    }
    List<SearchScope> scopes;
    if (scope != null && !scope.isBlank()) {
      scopes = List.of(registry.requireScope(scope));
    } else {
      scopes = registry.all();
    }
    // A-04：format=csv 导出时上限放宽到 5000（行数仍受 CSV 横切闸门约束）
    int cap = "csv".equals(request.getParameter("format"))
        ? net.zentao.platform.filters.Filters.CSV_MAX_LIMIT
        : 200;
    int effectiveLimit = Math.min(Math.max(limit, 1), cap);
    List<SearchResultView> merged = new ArrayList<>();
    for (SearchScope registered : scopes) {
      if (registered == null) {
        continue;
      }
      merged.addAll(registered.executor().search(q, effectiveLimit, principal));
    }
    merged.sort(Comparator.comparing(SearchResultView::updatedAt,
        Comparator.nullsLast(Comparator.reverseOrder())));
    int offset = Math.min((Math.max(page, 1) - 1) * effectiveLimit, merged.size());
    int end = Math.min(offset + effectiveLimit, merged.size());
    return DataEnvelope.of(new SearchResultList(List.copyOf(merged.subList(offset, end)), merged.size()));
  }

  /** SearchResultList（contract：items + total）。 */
  public record SearchResultList(List<SearchResultView> items, long total) {}
}
