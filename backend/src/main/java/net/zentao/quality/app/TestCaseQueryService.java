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
import net.zentao.platform.search.SearchResultView;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.api.TestCaseList;
import net.zentao.quality.api.TestCaseView;
import net.zentao.quality.domain.TestCaseRepository;
import org.springframework.stereotype.Component;

/**
 * 用例列表查询（quality 卡 §3.2 DSL 白名单）：产品面注入 DataScope（product_id IN 可见集）；
 * 库内面（library_id=路径）全员可读不注入（§7 Library 语义）。
 */
@Component
public class TestCaseQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("status", "priority", "type", "stage", "libraryId", "categoryId", "storyId", "lastRunResult",
          "projectId", "executionId", "createdBy", "createdAt", "id"),
      Set.of("id", "priority", "status", "createdAt"),
      Set.of("title", "keywords"));

  private static final Map<String, String> COLUMNS = Map.ofEntries(
      Map.entry("status", "status"),
      Map.entry("priority", "priority"),
      Map.entry("type", "type"),
      Map.entry("stage", "stage"),
      Map.entry("libraryId", "library_id"),
      Map.entry("categoryId", "category_id"),
      Map.entry("storyId", "story_id"),
      Map.entry("lastRunResult", "last_run_result"),
      Map.entry("createdBy", "created_by"),
      Map.entry("createdAt", "created_at"),
      Map.entry("id", "id"),
      Map.entry("title", "title"),
      Map.entry("keywords", "keywords"));

  private final TestCaseRepository repository;
  private final ProductApi productApi;

  public TestCaseQueryService(TestCaseRepository repository, ProductApi productApi) {
    this.repository = repository;
    this.productApi = productApi;
  }

  /** 产品用例列表（库用例 product_id=0 天然不在内）。 */
  public TestCaseList pageByProduct(long productId, SessionPrincipal principal, Map<String, String[]> params) {
    return query(principal, params, new QueryColumn("product_id").eq(productId), true);
  }

  /** 库内用例列表（全员可读）。 */
  public TestCaseList pageByLibrary(long libraryId, SessionPrincipal principal, Map<String, String[]> params) {
    return query(principal, params, new QueryColumn("library_id").eq(libraryId), false);
  }

  /** 详情：库用例全员可读，产品用例走产品 ACL（40401 先于 40302）。 */
  public TestCaseView detail(SessionPrincipal principal, long caseId) {
    return TestCaseView.of(TestCaseActionSupport.require(principal, repository, productApi, caseId));
  }

  /** 全局搜索（platform 卡 §5.2）：仅产品用例参与（库用例走库面），可见集先于 LIKE。 */
  public List<SearchResultView> search(String q, int limit, SessionPrincipal principal) {
    String like = "%" + q + "%";
    QueryCondition injected = new QueryColumn("deleted_at").isNull()
        .and(new QueryColumn("title").like(like).or(new QueryColumn("keywords").like(like)))
        .and(new QueryColumn("product_id").gt(0));
    ProductApi.ProductScope scope = productApi.visibleScope(principal);
    if (!scope.visibleToAll()) {
      injected = injected.and(new QueryColumn("product_id")
          .in(scope.productIds().isEmpty() ? List.of(-1L) : List.copyOf(scope.productIds())));
    }
    QueryWrapper query = QueryWrapper.create().where(injected)
        .orderBy(new QueryColumn("updated_at").desc(), new QueryColumn("id").desc()) // banned-words-ok：MyBatis-Flex 构造器方法名
        .limit(limit);
    return repository.queryPage(query, 0, limit).stream()
        .map(testCase -> new SearchResultView("testCase", testCase.id(), testCase.title(),
            testCase.keywords(), testCase.updatedAt() == null ? testCase.createdAt() : testCase.updatedAt()))
        .toList();
  }

  private TestCaseList query(SessionPrincipal principal, Map<String, String[]> params,
      QueryCondition scopeCondition, boolean withProductAcl) {
    Filters filters = Filters.parse(params, REGISTRY);
    // projectId/executionId 无本表列（见 runDimensionCondition 注释）：转子查询条件注入，其余条件照常编译
    QueryCondition runDimension = runDimensionCondition(filters);
    Filters rest = withoutRunDimensions(filters);
    QueryCondition injected = new QueryColumn("deleted_at").isNull().and(scopeCondition);
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    if (runDimension != null) {
      injected = injected.and(runDimension);
    }
    if (withProductAcl) {
      ProductApi.ProductScope scope = productApi.visibleScope(principal);
      if (!scope.visibleToAll()) {
        injected = injected.and(new QueryColumn("product_id")
            .in(scope.productIds().isEmpty() ? List.of(-1L) : List.copyOf(scope.productIds())));
      }
    }
    QueryWrapper query = FilterPredicate.compile(rest, COLUMNS::get, specialOf(principal), injected);
    List<TestCaseView> items = repository.queryPage(query, rest.offset(), rest.limit()).stream()
        .map(TestCaseView::of)
        .toList();
    Filters countFilters = new Filters(rest.clauses(), List.of(), 1, 1, rest.q());
    QueryWrapper countQuery = FilterPredicate.compile(countFilters, COLUMNS::get, specialOf(principal), injected);
    return new TestCaseList(items, repository.countByQuery(countQuery));
  }

  /**
   * B-PRJ-17：test_case 表无 project_id/execution_id 列（V12 建表即无，用例与项目/执行只经测试单关联），
   * 项目/执行维度视图经 test_run_case → test_run 子查询联查（该执行测试单覆盖的用例），
   * 值形态仅支持等值/逗号多选（多值 OR）。
   */
  private static QueryCondition runDimensionCondition(Filters filters) {
    QueryCondition combined = null;
    for (Filters.FilterClause clause : filters.clauses()) {
      if (!RUN_DIMENSION_FIELDS.contains(clause.field())) {
        continue;
      }
      if (clause.op() != Filters.Op.EQ && clause.op() != Filters.Op.IN) {
        throw ApiException.badRequest(clause.field() + " 仅支持等值/多选过滤。");
      }
      QueryCondition condition = null;
      for (String value : clause.values()) {
        long id;
        try {
          id = Long.parseLong(value);
        } catch (NumberFormatException e) {
          throw ApiException.badRequest(clause.field() + " 必须是 id。");
        }
        QueryCondition matched = new QueryColumn("id").in(QueryWrapper.create()
            .select(new QueryColumn("test_case_id"))
            .from("test_run_case")
            .where(new QueryColumn("test_run_id").in(
                QueryWrapper.create().select(new QueryColumn("id")).from("test_run")
                    .where(new QueryColumn(runColumn(clause.field())).eq(id)
                        .and(new QueryColumn("deleted_at").isNull())))));
        condition = condition == null ? matched : condition.or(matched);
      }
      combined = combined == null ? condition : combined.and(condition);
    }
    return combined;
  }

  private static final Set<String> RUN_DIMENSION_FIELDS = Set.of("projectId", "executionId");

  private static String runColumn(String field) {
    return "projectId".equals(field) ? "project_id" : "execution_id";
  }

  private static Filters withoutRunDimensions(Filters filters) {
    return new Filters(filters.clauses().stream()
        .filter(clause -> !RUN_DIMENSION_FIELDS.contains(clause.field()))
        .toList(), filters.sortKeys(), filters.page(), filters.limit(), filters.q());
  }

  private static Function<String, Optional<String>> specialOf(SessionPrincipal principal) {
    return value -> "@me".equals(value) ? Optional.of(principal.account()) : Optional.empty();
  }

  private QueryCondition keywordCondition(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    String like = "%" + q + "%";
    return new QueryColumn("title").like(like).or(new QueryColumn("keywords").like(like));
  }
}
