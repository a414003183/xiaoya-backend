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
import net.zentao.platform.filters.LikePatterns;
import net.zentao.platform.search.SearchResultView;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.api.BugApi;
import net.zentao.quality.api.BugList;
import net.zentao.quality.api.BugView;
import net.zentao.quality.domain.Bug;
import net.zentao.quality.domain.BugRepository;
import org.springframework.stereotype.Component;

/** Bug 列表查询（quality 卡 §3.1 DSL 白名单 + §7 DataScope 注入 `product_id IN 可见集`，A6）。 */
@Component
public class BugQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("status", "severity", "priority", "type", "confirmed", "resolution", "assignee", "executionId",
          "projectId", "categoryId", "createdBy", "createdAt", "resolvedBy", "closedBy", "id"),
      Set.of("id", "severity", "priority", "status", "createdAt"),
      Set.of("title", "keywords"));

  private static final Map<String, String> COLUMNS = Map.ofEntries(
      Map.entry("status", "status"),
      Map.entry("severity", "severity"),
      Map.entry("priority", "priority"),
      Map.entry("type", "type"),
      Map.entry("confirmed", "confirmed"),
      Map.entry("resolution", "resolution"),
      Map.entry("assignee", "assignee"),
      Map.entry("executionId", "execution_id"),
      Map.entry("projectId", "project_id"),
      Map.entry("categoryId", "category_id"),
      Map.entry("createdBy", "created_by"),
      Map.entry("createdAt", "created_at"),
      Map.entry("resolvedBy", "resolved_by"),
      Map.entry("closedBy", "closed_by"),
      Map.entry("id", "id"),
      Map.entry("title", "title"),
      Map.entry("keywords", "keywords"));

  private final BugRepository repository;
  private final ProductApi productApi;

  public BugQueryService(BugRepository repository, ProductApi productApi) {
    this.repository = repository;
    this.productApi = productApi;
  }

  /** 产品维度列表（quality 卡 §5 GET /products/{productId}/bugs）。 */
  public BugList pageByProduct(long productId, SessionPrincipal principal, Map<String, String[]> params) {
    return query(principal, params, new QueryColumn("product_id").eq(productId), null);
  }

  /** 详情（不存在 → 40401；产品不可见 → 40302）。 */
  public BugView detail(SessionPrincipal principal, long bugId) {
    return BugView.of(BugActionSupport.require(principal, repository, productApi, bugId));
  }

  /** 全局搜索（platform 卡 §5.2）：可见集先于 LIKE，命中 title/keywords。 */
  public List<SearchResultView> search(String q, int limit, SessionPrincipal principal) {
    String like = LikePatterns.contains(q);
    QueryCondition injected = new QueryColumn("deleted_at").isNull()
        .and(new QueryColumn("title").likeRaw(like).or(new QueryColumn("keywords").likeRaw(like)));
    injected = withScope(injected, principal);
    QueryWrapper query = QueryWrapper.create().where(injected)
        .orderBy(new QueryColumn("updated_at").desc(), new QueryColumn("id").desc()) // banned-words-ok：MyBatis-Flex 构造器方法名
        .limit(limit);
    return repository.queryPage(query, 0, limit).stream()
        .map(bug -> new SearchResultView("bug", bug.id(), bug.title(), excerpt(bug),
            bug.updatedAt() == null ? bug.createdAt() : bug.updatedAt()))
        .toList();
  }

  private static String excerpt(net.zentao.quality.domain.Bug bug) {
    String steps = bug.steps();
    if (steps == null || steps.isBlank()) {
      return bug.keywords();
    }
    return steps.length() > 100 ? steps.substring(0, 100) : steps;
  }

  /** 计划维度列表（product §5 GET /plans/{planId}/bugs，经 BugApi）。 */
  public BugList pageByPlan(long planId, SessionPrincipal principal, Map<String, String[]> params) {
    return query(principal, params, new QueryColumn("plan_id").eq(planId), null);
  }

  /** id 集合列表（发布/构建关联 Bug，保持调用方给的集合语义）。 */
  public BugList pageByIds(List<Long> ids, SessionPrincipal principal, Map<String, String[]> params) {
    return query(principal, params, null, ids);
  }

  /** 跨产品 Bug 列表（workspace 卡 §5 /my/bugs：DataScope 为可见产品集）。 */
  public BugList pageAll(SessionPrincipal principal, Map<String, String[]> params) {
    return query(principal, params, null, null);
  }

  /** Bug 分布计数（workspace 卡 §5：resolution 空计入 unresolved 桶；产品不可见 → 40302）。 */
  public BugApi.BugDistributionCounts distributionCounts(long productId, SessionPrincipal principal) {
    productApi.requireVisible(principal, productId);
    List<Bug> bugs = repository.findActiveByProduct(productId);
    return new BugApi.BugDistributionCounts(
        bugs.size(),
        group(bugs, Bug::severity),
        group(bugs, bug -> text(bug.status())),
        group(bugs, bug -> bug.resolution() == null || bug.resolution().isBlank()
            ? "unresolved"
            : bug.resolution()));
  }

  private static <K> Map<K, Long> group(List<Bug> bugs, java.util.function.Function<Bug, K> key) {
    return bugs.stream().collect(java.util.stream.Collectors.groupingBy(key,
        java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));
  }

  private static String text(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }

  private QueryCondition withScope(QueryCondition injected, SessionPrincipal principal) {
    ProductApi.ProductScope scope = productApi.visibleScope(principal);
    if (!scope.visibleToAll()) {
      return injected.and(new QueryColumn("product_id")
          .in(scope.productIds().isEmpty() ? List.of(-1L) : List.copyOf(scope.productIds())));
    }
    return injected;
  }

  private BugList query(SessionPrincipal principal, Map<String, String[]> params, QueryCondition scopeCondition,
      List<Long> ids) {
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition injected = new QueryColumn("deleted_at").isNull();
    if (scopeCondition != null) {
      injected = injected.and(scopeCondition);
    }
    if (ids != null) {
      injected = injected.and(new QueryColumn("id").in(ids.isEmpty() ? List.of(-1L) : ids));
    }
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    injected = withScope(injected, principal);

    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, specialOf(principal), injected);
    List<BugView> items = repository.queryPage(query, filters.offset(), filters.limit()).stream()
        .map(BugView::of)
        .toList();
    QueryWrapper countQuery = FilterPredicate.compile(filters.forCount(), COLUMNS::get, specialOf(principal), injected);
    return new BugList(items, repository.countByQuery(countQuery));
  }

  /** 特殊量：@me = 当前账号（@null/@notNull 已在解析层处理）。 */
  private static Function<String, Optional<String>> specialOf(SessionPrincipal principal) {
    return value -> "@me".equals(value) ? Optional.of(principal.account()) : Optional.empty();
  }

  private QueryCondition keywordCondition(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    String like = LikePatterns.contains(q);
    return new QueryColumn("title").likeRaw(like).or(new QueryColumn("keywords").likeRaw(like));
  }
}
