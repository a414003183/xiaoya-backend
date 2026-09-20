package net.zentao.quality.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.api.ResultView;
import net.zentao.quality.api.RunCaseList;
import net.zentao.quality.api.TestRunList;
import net.zentao.quality.api.TestRunView;
import net.zentao.quality.domain.Result;
import net.zentao.quality.domain.ResultRepository;
import net.zentao.quality.domain.TestCase;
import net.zentao.quality.domain.TestCaseRepository;
import net.zentao.quality.domain.TestRunRepository;
import org.springframework.stereotype.Component;

/**
 * 测试单查询（quality 卡 §3.4 DSL 白名单 + §7 DataScope；执行清单 = test_run_case + 用例摘要批量 IN 联结）。
 */
@Component
public class TestRunQueryService {

  private static final FieldRegistry RUN_REGISTRY = FieldRegistry.allowing(
      Set.of("status", "priority", "type", "owner", "executionId", "buildId", "createdAt", "id"),
      Set.of("id", "priority", "status", "beginDate", "createdAt"),
      Set.of("name"));

  private static final Map<String, String> RUN_COLUMNS = Map.ofEntries(
      Map.entry("status", "status"),
      Map.entry("priority", "priority"),
      Map.entry("type", "type"),
      Map.entry("owner", "owner"),
      Map.entry("executionId", "execution_id"),
      Map.entry("buildId", "build_id"),
      Map.entry("createdAt", "created_at"),
      Map.entry("id", "id"),
      Map.entry("name", "name"));

  private static final FieldRegistry RUN_CASE_REGISTRY = FieldRegistry.allowing(
      Set.of("assignee", "result"),
      Set.of("id"),
      Set.of());

  private static final Map<String, String> RUN_CASE_COLUMNS = Map.of(
      "assignee", "assignee",
      "result", "result",
      "id", "id");

  private final TestRunRepository repository;
  private final ResultRepository resultRepository;
  private final TestCaseRepository caseRepository;
  private final ProductApi productApi;
  private final TestRunHandlers handlers;

  public TestRunQueryService(TestRunRepository repository, ResultRepository resultRepository,
      TestCaseRepository caseRepository, ProductApi productApi, TestRunHandlers handlers) {
    this.repository = repository;
    this.resultRepository = resultRepository;
    this.caseRepository = caseRepository;
    this.productApi = productApi;
    this.handlers = handlers;
  }

  public TestRunList pageByProduct(long productId, SessionPrincipal principal, Map<String, String[]> params) {
    Filters filters = Filters.parse(params, RUN_REGISTRY);
    QueryCondition injected = new QueryColumn("deleted_at").isNull()
        .and(new QueryColumn("product_id").eq(productId));
    injected = withScope(injected, principal);
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    QueryWrapper query = FilterPredicate.compile(filters, RUN_COLUMNS::get, specialOf(principal), injected);
    List<TestRunView> items = repository.queryPage(query, filters.offset(), filters.limit()).stream()
        .map(TestRunView::of)
        .toList();
    Filters countFilters = new Filters(filters.clauses(), List.of(), 1, 1, filters.q());
    QueryWrapper countQuery = FilterPredicate.compile(countFilters, RUN_COLUMNS::get, specialOf(principal), injected);
    return new TestRunList(items, repository.countByQuery(countQuery));
  }

  public TestRunView detail(SessionPrincipal principal, long testRunId) {
    return TestRunView.of(handlers.require(principal, testRunId));
  }

  /** 用例通过率计数（workspace 卡 §5：pass/fail/blocked/n-a；测试单不可见 → 40302）。 */
  public net.zentao.quality.api.TestRunApi.CasePassRate casePassRate(SessionPrincipal principal, long testRunId) {
    handlers.require(principal, testRunId);
    List<Result> results = resultRepository.findByTestRun(testRunId);
    return new net.zentao.quality.api.TestRunApi.CasePassRate(results.size(), count(results, "pass"),
        count(results, "fail"), count(results, "blocked"), count(results, "n/a"));
  }

  private static long count(List<Result> results, String value) {
    return results.stream().filter(result -> value.equals(result.result())).count();
  }

  /** 执行清单（runs + 用例摘要 title/priority）。 */
  public RunCaseList runCases(SessionPrincipal principal, long testRunId, Map<String, String[]> params) {
    handlers.require(principal, testRunId); // 前置校验（40401/40302）
    Filters filters = Filters.parse(params, RUN_CASE_REGISTRY);
    QueryCondition injected = new QueryColumn("test_run_id").eq(testRunId);
    QueryWrapper query = FilterPredicate.compile(filters, RUN_CASE_COLUMNS::get, specialOf(principal), injected);
    List<Result> results = resultRepository.queryPage(query, filters.offset(), filters.limit());
    Map<Long, TestCase> cases = caseRepository.findActiveByIds(
        results.stream().map(Result::testCaseId).distinct().toList()).stream()
        .collect(java.util.stream.Collectors.toMap(TestCase::id, testCase -> testCase));
    List<ResultView> items = results.stream()
        .map(result -> {
          TestCase testCase = cases.get(result.testCaseId());
          return ResultView.of(result,
              testCase == null ? null : testCase.title(),
              testCase == null ? null : testCase.priority());
        })
        .toList();
    Filters countFilters = new Filters(filters.clauses(), List.of(), 1, 1, filters.q());
    QueryWrapper countQuery = FilterPredicate.compile(countFilters, RUN_CASE_COLUMNS::get, specialOf(principal), injected);
    return new RunCaseList(items, resultRepository.countByQuery(countQuery));
  }

  private QueryCondition withScope(QueryCondition injected, SessionPrincipal principal) {
    ProductApi.ProductScope scope = productApi.visibleScope(principal);
    if (!scope.visibleToAll()) {
      return injected.and(new QueryColumn("product_id")
          .in(scope.productIds().isEmpty() ? List.of(-1L) : List.copyOf(scope.productIds())));
    }
    return injected;
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
