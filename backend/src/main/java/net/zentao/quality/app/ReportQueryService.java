package net.zentao.quality.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.project.api.ExecutionApi;
import net.zentao.quality.api.ReportList;
import net.zentao.quality.api.ReportView;
import net.zentao.quality.domain.Report;
import net.zentao.quality.domain.ReportRepository;
import org.springframework.stereotype.Component;

/** 测试报告查询（quality 卡 §3.6 DSL 白名单 + §7 随冗余 productId 走产品 ACL）。 */
@Component
public class ReportQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("owner", "createdAt", "id"),
      Set.of("id", "createdAt"),
      Set.of("title"));

  private static final Map<String, String> COLUMNS = Map.of(
      "owner", "owner",
      "createdAt", "created_at",
      "id", "id",
      "title", "title");

  private final ReportRepository repository;
  private final ReportHandlers handlers;
  private final ProductApi productApi;
  private final ExecutionApi executionApi;

  public ReportQueryService(ReportRepository repository, ReportHandlers handlers, ProductApi productApi,
      ExecutionApi executionApi) {
    this.repository = repository;
    this.handlers = handlers;
    this.productApi = productApi;
    this.executionApi = executionApi;
  }

  public ReportList pageByExecution(SessionPrincipal principal, long executionId,
      Map<String, String[]> params) {
    executionApi.requireExecution(principal, executionId); // 40401/40302 前置
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition injected = new QueryColumn("deleted_at").isNull()
        .and(new QueryColumn("execution_id").eq(executionId));
    ProductApi.ProductScope scope = productApi.visibleScope(principal);
    if (!scope.visibleToAll()) {
      injected = injected.and(new QueryColumn("product_id")
          .in(scope.productIds().isEmpty() ? List.of(-1L) : List.copyOf(scope.productIds())));
    }
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, specialOf(principal), injected);
    List<ReportView> items = repository.queryPage(query, filters.offset(), filters.limit()).stream()
        .map(ReportView::of)
        .toList();
    Filters countFilters = new Filters(filters.clauses(), List.of(), 1, 1, filters.q());
    QueryWrapper countQuery = FilterPredicate.compile(countFilters, COLUMNS::get, specialOf(principal), injected);
    return new ReportList(items, repository.countByQuery(countQuery));
  }

  public ReportView detail(SessionPrincipal principal, long reportId) {
    return ReportView.of(handlers.require(principal, reportId));
  }

  private static Function<String, Optional<String>> specialOf(SessionPrincipal principal) {
    return value -> "@me".equals(value) ? Optional.of(principal.account()) : Optional.empty();
  }

  private QueryCondition keywordCondition(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    return new QueryColumn("title").like("%" + q + "%");
  }
}
