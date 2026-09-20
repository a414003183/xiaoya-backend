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
import net.zentao.quality.api.SuiteList;
import net.zentao.quality.api.SuiteView;
import net.zentao.quality.domain.Suite;
import net.zentao.quality.domain.SuiteRepository;
import org.springframework.stereotype.Component;

/**
 * 套件/用例库查询（quality 卡 §3.3 DSL 白名单 + §7）：套件面 = 产品 ACL + private 仅创建者；
 * 库面 = type=library 全员可读。
 */
@Component
public class SuiteQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("type", "createdBy", "id"),
      Set.of("id", "sort", "createdAt"),
      Set.of("name"));

  private static final Map<String, String> COLUMNS = Map.of(
      "type", "type",
      "createdBy", "created_by",
      "id", "id",
      "sort", "sort",
      "createdAt", "created_at",
      "name", "name");

  private final SuiteRepository repository;
  private final ProductApi productApi;

  public SuiteQueryService(SuiteRepository repository, ProductApi productApi) {
    this.repository = repository;
    this.productApi = productApi;
  }

  /** 套件列表（type≠library；private 仅创建者可见，超管不受限）。 */
  public SuiteList pageByProduct(long productId, SessionPrincipal principal, Map<String, String[]> params) {
    return query(principal, params, new QueryColumn("product_id").eq(productId)
        .and(new QueryColumn("type").ne(Suite.TYPE_LIBRARY)), true);
  }

  /** 用例库列表（全员可读）。 */
  public SuiteList pageLibraries(SessionPrincipal principal, Map<String, String[]> params) {
    return query(principal, params, new QueryColumn("type").eq(Suite.TYPE_LIBRARY), false);
  }

  /** 套件详情（含 caseIds + caseCount）。 */
  public SuiteView detailSuite(SessionPrincipal principal, long suiteId) {
    Suite suite = requireSuite(principal, suiteId);
    return SuiteView.detailOf(suite, countOne(suiteId));
  }

  /** 用例库详情。 */
  public SuiteView detailLibrary(SessionPrincipal principal, long libraryId) {
    return SuiteView.detailOf(requireLibrary(libraryId), countOne(libraryId));
  }

  /** 套件前置：40401 → private 非创建者 40302 → 产品不可见 40302（超管不受限）。 */
  Suite requireSuite(SessionPrincipal principal, long suiteId) {
    Suite suite = repository.findActiveById(suiteId).orElseThrow(() -> ApiException.notFound("套件"));
    if (suite.isLibrary()) {
      return suite;
    }
    if (!visible(principal, suite)) {
      throw ApiException.dataForbidden("无权访问该套件。");
    }
    return suite;
  }

  Suite requireLibrary(long libraryId) {
    Suite suite = repository.findActiveById(libraryId).orElseThrow(() -> ApiException.notFound("用例库"));
    if (!suite.isLibrary()) {
      throw ApiException.notFound("用例库");
    }
    return suite;
  }

  private boolean visible(SessionPrincipal principal, Suite suite) {
    if ("private".equals(suite.type())) {
      if (suite.createdBy() != null && suite.createdBy().equals(principal.account())) {
        return true;
      }
      // 超管不受限：产品可见集 visibleToAll 即超管口径（product §7）
      return productApi.visibleScope(principal).visibleToAll();
    }
    return productApi.canAccess(principal, suite.productId());
  }

  private long countOne(long suiteId) {
    return repository.countCases(List.of(suiteId)).getOrDefault(suiteId, 0L);
  }

  private SuiteList query(SessionPrincipal principal, Map<String, String[]> params, QueryCondition scopeCondition,
      boolean withPrivateRule) {
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition injected = new QueryColumn("deleted_at").isNull().and(scopeCondition);
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    if (withPrivateRule && !productApi.visibleScope(principal).visibleToAll()) {
      ProductApi.ProductScope scope = productApi.visibleScope(principal);
      // (产品可见 AND 非 private) OR (private AND 本人创建)——§7 private 仅 createdBy
      QueryCondition visibleProducts = new QueryColumn("product_id")
          .in(scope.productIds().isEmpty() ? List.of(-1L) : List.copyOf(scope.productIds()))
          .and(new QueryColumn("type").ne("private"));
      QueryCondition ownPrivate = new QueryColumn("type").eq("private")
          .and(new QueryColumn("created_by").eq(principal.account()));
      injected = injected.and(visibleProducts.or(ownPrivate));
    }
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, specialOf(principal), injected);
    List<Suite> suites = repository.queryPage(query, filters.offset(), filters.limit());
    Map<Long, Long> counts = repository.countCases(suites.stream().map(Suite::id).toList());
    List<SuiteView> items = suites.stream()
        .map(suite -> SuiteView.listRow(suite, counts.getOrDefault(suite.id(), 0L)))
        .toList();
    Filters countFilters = new Filters(filters.clauses(), List.of(), 1, 1, filters.q());
    QueryWrapper countQuery = FilterPredicate.compile(countFilters, COLUMNS::get, specialOf(principal), injected);
    return new SuiteList(items, repository.countByQuery(countQuery));
  }

  private static Function<String, Optional<String>> specialOf(SessionPrincipal principal) {
    return value -> "@me".equals(value) ? Optional.of(principal.account()) : Optional.empty();
  }

  private QueryCondition keywordCondition(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    return new QueryColumn("name").like("%" + q + "%");
  }
}
