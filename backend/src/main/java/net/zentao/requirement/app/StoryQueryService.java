package net.zentao.requirement.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.search.SearchResultView;
import net.zentao.product.api.ProductApi;
import net.zentao.requirement.api.StoryApi;
import net.zentao.requirement.api.StoryList;
import net.zentao.requirement.api.StoryView;
import net.zentao.requirement.domain.Story;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.stereotype.Component;

/** 需求列表查询（requirement 卡 §3 DSL 白名单 + §7 DataScope 注入 `product_id IN 可见集`，A6）。 */
@Component
public class StoryQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("status", "type", "priority", "source", "stage", "assignee", "categoryId", "planId", "closedReason",
          "createdBy", "createdAt", "closedBy", "reviewers", "id"),
      Set.of("id", "priority", "status", "createdAt"),
      Set.of("title", "keywords"));

  private static final Map<String, String> COLUMNS = Map.ofEntries(
      Map.entry("status", "status"),
      Map.entry("type", "type"),
      Map.entry("priority", "priority"),
      Map.entry("source", "source"),
      Map.entry("stage", "stage"),
      Map.entry("assignee", "assignee"),
      Map.entry("categoryId", "category_id"),
      Map.entry("planId", "plan_id"),
      Map.entry("closedReason", "closed_reason"),
      Map.entry("createdBy", "created_by"),
      Map.entry("createdAt", "created_at"),
      Map.entry("closedBy", "closed_by"),
      Map.entry("reviewers", "reviewers"),
      Map.entry("id", "id"),
      Map.entry("title", "title"),
      Map.entry("keywords", "keywords"));

  private final StoryRepository repository;
  private final ProductApi productApi;

  public StoryQueryService(StoryRepository repository, ProductApi productApi) {
    this.repository = repository;
    this.productApi = productApi;
  }

  /** 产品维度列表（requirement 卡 §5 GET /products/{productId}/stories）。 */
  public StoryList pageByProduct(long productId, SessionPrincipal principal, Map<String, String[]> params) {
    return query(principal, params, new QueryColumn("product_id").eq(productId), null);
  }

  /** 详情（不存在 → 40401；产品不可见 → 40302）。 */
  public StoryView detail(SessionPrincipal principal, long storyId) {
    return StoryView.of(StoryActionSupport.require(principal, repository, productApi, storyId));
  }

  /**
   * 全局搜索（platform 卡 §5.2）：DataScope 先于 LIKE——可见产品集作为前置条件，
   * 命中 title/keywords，按 updatedAt 倒序取 limit 条。
   */
  public List<SearchResultView> search(String q, int limit, SessionPrincipal principal, String type) {
    String like = "%" + q + "%";
    QueryCondition injected = new QueryColumn("deleted_at").isNull()
        .and(new QueryColumn("title").like(like).or(new QueryColumn("keywords").like(like)));
    if (type != null) {
      injected = injected.and(new QueryColumn("type").eq(type));
    }
    ProductApi.ProductScope scope = productApi.visibleScope(principal);
    if (!scope.visibleToAll()) {
      injected = injected.and(new QueryColumn("product_id")
          .in(scope.productIds().isEmpty() ? List.of(-1L) : List.copyOf(scope.productIds())));
    }
    QueryWrapper query = QueryWrapper.create().where(injected)
        .orderBy(new QueryColumn("updated_at").desc(), new QueryColumn("id").desc()) // banned-words-ok：MyBatis-Flex 构造器方法名
        .limit(limit);
    return repository.queryPage(query, 0, limit).stream()
        .map(story -> new SearchResultView("story", story.id(), story.title(), excerpt(story),
            story.updatedAt() == null ? story.createdAt() : story.updatedAt()))
        .toList();
  }

  private static String excerpt(net.zentao.requirement.domain.Story story) {
    String description = story.description();
    if (description == null || description.isBlank()) {
      return story.keywords();
    }
    return description.length() > 100 ? description.substring(0, 100) : description;
  }

  /** 计划维度列表（product §5 GET /plans/{planId}/stories，经 StoryApi）。 */
  public StoryList pageByPlan(long planId, SessionPrincipal principal, Map<String, String[]> params) {
    return query(principal, params, new QueryColumn("plan_id").eq(planId), null);
  }

  /** id 集合列表（发布/构建关联需求，保持调用方给的集合语义）。 */
  public StoryList pageByIds(List<Long> ids, SessionPrincipal principal, Map<String, String[]> params) {
    return query(principal, params, null, ids);
  }

  /** 跨产品需求列表（workspace 卡 §5 /my/stories：DataScope 为可见产品集）。 */
  public StoryList pageAll(SessionPrincipal principal, Map<String, String[]> params) {
    return query(principal, params, null, null);
  }

  /** 产品需求统计计数（workspace 卡 §5：排除已删；产品不可见 → 40302）。 */
  public StoryApi.StorySummaryCounts summaryCounts(long productId, SessionPrincipal principal) {
    productApi.requireVisible(principal, productId);
    List<Story> stories = repository.findActiveByProduct(productId);
    return new StoryApi.StorySummaryCounts(
        stories.size(),
        group(stories, story -> text(story.status())),
        group(stories, story -> story.priority()),
        group(stories, story -> text(story.stage())),
        group(stories, story -> text(story.type())));
  }

  private static <K> Map<K, Long> group(List<Story> stories, java.util.function.Function<Story, K> key) {
    return stories.stream().collect(java.util.stream.Collectors.groupingBy(key,
        java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));
  }

  private static String text(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }

  private StoryList query(SessionPrincipal principal, Map<String, String[]> params, QueryCondition scopeCondition,
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
    // filters[reviewers]=@me：需求评审人为 JSON 账号列表（requirement 卡 §3.1），按成员包含匹配
    QueryCondition reviewers = reviewersCondition(filters, principal);
    if (reviewers != null) {
      injected = injected.and(reviewers);
    }
    ProductApi.ProductScope scope = productApi.visibleScope(principal);
    if (!scope.visibleToAll()) {
      injected = injected.and(new QueryColumn("product_id")
          .in(scope.productIds().isEmpty() ? List.of(-1L) : List.copyOf(scope.productIds())));
    }
    // reviewers 已在上面单独成条件，编译时剔除（列形态非等值可比）
    Filters comparable = new Filters(
        filters.clauses().stream().filter(clause -> !"reviewers".equals(clause.field())).toList(),
        filters.sortKeys(), filters.page(), filters.limit(), filters.q());

    QueryWrapper query = FilterPredicate.compile(comparable, COLUMNS::get, specialOf(principal), injected);
    List<StoryView> items = repository.queryPage(query, filters.offset(), filters.limit()).stream()
        .map(StoryView::of)
        .toList();
    Filters countFilters = new Filters(comparable.clauses(), List.of(), 1, 1, filters.q());
    QueryWrapper countQuery = FilterPredicate.compile(countFilters, COLUMNS::get, specialOf(principal), injected);
    return new StoryList(items, repository.countByQuery(countQuery));
  }

  /** filters[reviewers]=账号：JSON 列表包含匹配（@me 已解析为当前账号）。 */
  private static QueryCondition reviewersCondition(Filters filters, SessionPrincipal principal) {
    QueryCondition condition = null;
    for (Filters.FilterClause clause : filters.clauses()) {
      if (!"reviewers".equals(clause.field()) || clause.op() != Filters.Op.EQ) {
        continue;
      }
      String account = clause.values().getFirst();
      if ("@me".equals(account)) {
        account = principal.account();
      }
      QueryCondition match = new QueryColumn("reviewers").like("%\"" + account + "\"%");
      condition = condition == null ? match : condition.or(match);
    }
    return condition;
  }

  /** 特殊量：@me = 当前账号（@null/@notNull 已在解析层处理）。 */
  private static java.util.function.Function<String, Optional<String>> specialOf(SessionPrincipal principal) {
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
