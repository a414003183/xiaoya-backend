package net.zentao.product.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.filters.LikePatterns;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.PlanView;
import net.zentao.product.api.ProductApi;
import net.zentao.product.domain.Plan;
import net.zentao.product.domain.PlanRepository;
import net.zentao.product.domain.ProductRepository;
import net.zentao.quality.api.BugApi;
import net.zentao.quality.api.BugList;
import net.zentao.requirement.api.StoryApi;
import net.zentao.requirement.api.StoryList;
import org.springframework.stereotype.Component;

/** 计划列表与关联列表查询（product 卡 §3.4 DSL 白名单；跨域列表转发 StoryApi/BugApi）。 */
@Component
public class PlanQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("status", "branchId", "parentId", "closedReason", "createdBy", "beginDate", "endDate",
          "createdAt", "id"),
      Set.of("id", "beginDate", "endDate", "createdAt"),
      Set.of("title"));

  private static final Map<String, String> COLUMNS = Map.ofEntries(
      Map.entry("status", "status"),
      Map.entry("branchId", "branch_id"),
      Map.entry("parentId", "parent_id"),
      Map.entry("closedReason", "closed_reason"),
      Map.entry("createdBy", "created_by"),
      Map.entry("beginDate", "begin_date"),
      Map.entry("endDate", "end_date"),
      Map.entry("createdAt", "created_at"),
      Map.entry("id", "id"),
      Map.entry("title", "title"));

  private final PlanRepository repository;
  private final ProductRepository productRepository;
  private final ProductApi productApi;
  private final StoryApi storyApi;
  private final BugApi bugApi;

  public PlanQueryService(PlanRepository repository, ProductRepository productRepository, ProductApi productApi,
      StoryApi storyApi, BugApi bugApi) {
    this.repository = repository;
    this.productRepository = productRepository;
    this.productApi = productApi;
    this.storyApi = storyApi;
    this.bugApi = bugApi;
  }

  /** PlanList 载荷（contract：items + total）。 */
  public record PlanList(List<PlanView> items, long total) {}

  public PlanList page(long productId, SessionPrincipal principal, Map<String, String[]> params) {
    ProductGuard.requireVisible(productRepository, productApi, principal, productId);
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition injected = new QueryColumn("deleted_at").isNull().and(new QueryColumn("product_id").eq(productId));
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> java.util.Optional.empty(), injected);
    List<PlanView> items = repository.queryPage(query, filters.offset(), filters.limit()).stream()
        .map(PlanView::of)
        .toList();
    QueryWrapper countQuery = FilterPredicate.compile(filters.forCount(), COLUMNS::get, value -> java.util.Optional.empty(),
        injected);
    return new PlanList(items, repository.countByQuery(countQuery));
  }

  public StoryList stories(long planId, SessionPrincipal principal, Map<String, String[]> params) {
    detail(principal, planId);
    return storyApi.pageByPlan(planId, principal, params);
  }

  public BugList bugs(long planId, SessionPrincipal principal, Map<String, String[]> params) {
    detail(principal, planId);
    return bugApi.pageByPlan(planId, principal, params);
  }

  /** 计划详情（不存在 → 40401；产品不可见 → 40302）。 */
  public PlanView detail(SessionPrincipal principal, long planId) {
    Plan plan = repository.findActiveById(planId).orElseThrow(() -> ApiException.notFound("entity.plan"));
    ProductGuard.requireVisible(productRepository, productApi, principal, plan.productId());
    return PlanView.of(plan);
  }

  private QueryCondition keywordCondition(String q) {
    return q == null || q.isBlank() ? null : new QueryColumn("title").likeRaw(LikePatterns.contains(q));
  }
}
