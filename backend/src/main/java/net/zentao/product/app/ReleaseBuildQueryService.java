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
import net.zentao.product.api.BuildView;
import net.zentao.product.api.ProductApi;
import net.zentao.product.api.ReleaseView;
import net.zentao.product.domain.Build;
import net.zentao.product.domain.BuildRepository;
import net.zentao.product.domain.ProductRepository;
import net.zentao.product.domain.Release;
import net.zentao.product.domain.ReleaseRepository;
import net.zentao.quality.api.BugApi;
import net.zentao.quality.api.BugList;
import net.zentao.requirement.api.StoryApi;
import net.zentao.requirement.api.StoryList;
import org.springframework.stereotype.Component;

/** 发布/构建列表与关联列表查询（product 卡 §3.5/§3.6 DSL 白名单；跨域列表转发 StoryApi/BugApi）。 */
@Component
public class ReleaseBuildQueryService {

  private static final FieldRegistry RELEASE_REGISTRY = FieldRegistry.allowing(
      Set.of("status", "branchId", "buildId", "releaseDate", "createdBy", "createdAt", "id"),
      Set.of("id", "releaseDate", "createdAt"),
      Set.of("name"));

  private static final Map<String, String> RELEASE_COLUMNS = Map.ofEntries(
      Map.entry("status", "status"),
      Map.entry("branchId", "branch_id"),
      Map.entry("buildId", "build_id"),
      Map.entry("releaseDate", "release_date"),
      Map.entry("createdBy", "created_by"),
      Map.entry("createdAt", "created_at"),
      Map.entry("id", "id"),
      Map.entry("name", "name"));

  private static final FieldRegistry BUILD_REGISTRY = FieldRegistry.allowing(
      Set.of("branchId", "executionId", "projectId", "builder", "buildDate", "createdBy", "createdAt",
          "id"),
      Set.of("id", "buildDate", "createdAt"),
      Set.of("name"));

  private static final Map<String, String> BUILD_COLUMNS = Map.ofEntries(
      Map.entry("branchId", "branch_id"),
      Map.entry("executionId", "execution_id"),
      Map.entry("projectId", "project_id"),
      Map.entry("builder", "builder"),
      Map.entry("buildDate", "build_date"),
      Map.entry("createdBy", "created_by"),
      Map.entry("createdAt", "created_at"),
      Map.entry("id", "id"),
      Map.entry("name", "name"));

  private final ReleaseRepository releaseRepository;
  private final BuildRepository buildRepository;
  private final ProductRepository productRepository;
  private final ProductApi productApi;
  private final StoryApi storyApi;
  private final BugApi bugApi;

  public ReleaseBuildQueryService(ReleaseRepository releaseRepository, BuildRepository buildRepository,
      ProductRepository productRepository, ProductApi productApi, StoryApi storyApi, BugApi bugApi) {
    this.releaseRepository = releaseRepository;
    this.buildRepository = buildRepository;
    this.productRepository = productRepository;
    this.productApi = productApi;
    this.storyApi = storyApi;
    this.bugApi = bugApi;
  }

  /** ReleaseList 载荷（contract：items + total）。 */
  public record ReleaseList(List<ReleaseView> items, long total) {}

  /** BuildList 载荷（contract：items + total）。 */
  public record BuildList(List<BuildView> items, long total) {}

  public ReleaseList releases(long productId, SessionPrincipal principal, Map<String, String[]> params) {
    ProductGuard.requireVisible(productRepository, productApi, principal, productId);
    Filters filters = Filters.parse(params, RELEASE_REGISTRY);
    QueryCondition injected = new QueryColumn("deleted_at").isNull()
        .and(new QueryColumn("product_id").eq(productId));
    QueryCondition keyword = keyword(filters.q(), "name");
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    QueryWrapper query = FilterPredicate.compile(filters, RELEASE_COLUMNS::get, value -> java.util.Optional.empty(),
        injected);
    List<ReleaseView> items = releaseRepository.queryPage(query, filters.offset(), filters.limit()).stream()
        .map(ReleaseView::of)
        .toList();
    QueryWrapper countQuery = FilterPredicate.compile(filters.forCount(), RELEASE_COLUMNS::get,
        value -> java.util.Optional.empty(), injected);
    return new ReleaseList(items, releaseRepository.countByQuery(countQuery));
  }

  public BuildList builds(long productId, SessionPrincipal principal, Map<String, String[]> params) {
    ProductGuard.requireVisible(productRepository, productApi, principal, productId);
    Filters filters = Filters.parse(params, BUILD_REGISTRY);
    QueryCondition injected = new QueryColumn("deleted_at").isNull().and(new QueryColumn("product_id").eq(productId));
    QueryCondition keyword = keyword(filters.q(), "name");
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    QueryWrapper query = FilterPredicate.compile(filters, BUILD_COLUMNS::get, value -> java.util.Optional.empty(),
        injected);
    List<BuildView> items = buildRepository.queryPage(query, filters.offset(), filters.limit()).stream()
        .map(BuildView::of)
        .toList();
    QueryWrapper countQuery = FilterPredicate.compile(filters.forCount(), BUILD_COLUMNS::get,
        value -> java.util.Optional.empty(), injected);
    return new BuildList(items, buildRepository.countByQuery(countQuery));
  }

  /** 发布详情（不存在 → 40401；产品不可见 → 40302）。 */
  public ReleaseView releaseDetail(SessionPrincipal principal, long releaseId) {
    return ReleaseView.of(requireRelease(principal, releaseId));
  }

  /** 构建详情。 */
  public BuildView buildDetail(SessionPrincipal principal, long buildId) {
    return BuildView.of(requireBuild(principal, buildId));
  }

  public StoryList releaseStories(long releaseId, SessionPrincipal principal, Map<String, String[]> params) {
    return storyApi.pageByIds(requireRelease(principal, releaseId).storyIds(), principal, params);
  }

  public BugList releaseBugs(long releaseId, SessionPrincipal principal, Map<String, String[]> params) {
    return bugApi.pageByIds(requireRelease(principal, releaseId).bugIds(), principal, params);
  }

  public StoryList buildStories(long buildId, SessionPrincipal principal, Map<String, String[]> params) {
    return storyApi.pageByIds(requireBuild(principal, buildId).storyIds(), principal, params);
  }

  public BugList buildBugs(long buildId, SessionPrincipal principal, Map<String, String[]> params) {
    return bugApi.pageByIds(requireBuild(principal, buildId).bugIds(), principal, params);
  }

  Release requireRelease(SessionPrincipal principal, long releaseId) {
    Release release = releaseRepository.findActiveById(releaseId).orElseThrow(() -> ApiException.notFound("entity.release"));
    ProductGuard.requireVisible(productRepository, productApi, principal, release.productId());
    return release;
  }

  Build requireBuild(SessionPrincipal principal, long buildId) {
    Build build = buildRepository.findActiveById(buildId).orElseThrow(() -> ApiException.notFound("entity.build"));
    ProductGuard.requireVisible(productRepository, productApi, principal, build.productId());
    return build;
  }

  private static QueryCondition keyword(String q, String column) {
    return q == null || q.isBlank() ? null : new QueryColumn(column).likeRaw(LikePatterns.contains(q));
  }
}
