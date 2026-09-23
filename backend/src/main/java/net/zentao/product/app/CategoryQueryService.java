package net.zentao.product.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.filters.LikePatterns;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.CategoryView;
import net.zentao.product.api.ProductApi;
import net.zentao.product.domain.Category;
import net.zentao.product.domain.CategoryRepository;
import net.zentao.product.domain.CategoryTree;
import net.zentao.product.domain.ProductRepository;
import org.springframework.stereotype.Component;

/**
 * 分类树查询（product 卡 §3.3）：`filters[type]` 必选（缺 → 40001），整树扁平全量返回不分页，
 * 服务端按父子深度优先排序（前端据 parentId 组树）。
 */
@Component
public class CategoryQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("branchId", "parentId", "type", "owner", "id"),
      Set.of("id", "sort"),
      Set.of("name"));

  private static final Map<String, String> COLUMNS = Map.ofEntries(
      Map.entry("branchId", "branch_id"),
      Map.entry("parentId", "parent_id"),
      Map.entry("type", "type"),
      Map.entry("owner", "owner"),
      Map.entry("id", "id"),
      Map.entry("name", "name"),
      Map.entry("sort", "sort"));

  private final CategoryRepository repository;
  private final ProductRepository productRepository;
  private final ProductApi productApi;

  public CategoryQueryService(CategoryRepository repository, ProductRepository productRepository,
      ProductApi productApi) {
    this.repository = repository;
    this.productRepository = productRepository;
    this.productApi = productApi;
  }

  /** CategoryList 载荷（contract：items + total）。 */
  public record CategoryList(List<CategoryView> items, long total) {}

  public CategoryList list(long productId, SessionPrincipal principal, Map<String, String[]> params) {
    ProductGuard.requireVisible(productRepository, productApi, principal, productId);
    Filters filters = Filters.parse(params, REGISTRY);
    boolean hasType = filters.clauses().stream().anyMatch(clause -> "type".equals(clause.field()));
    if (!hasType) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "category.query.typeRequired");
    }
    QueryCondition injected = new QueryColumn("deleted_at").isNull().and(new QueryColumn("product_id").eq(productId));
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> java.util.Optional.empty(), injected);
    List<Category> nodes = repository.queryList(query);
    List<CategoryView> items = CategoryTree.flattenDepthFirst(nodes).stream().map(CategoryView::of).toList();
    return new CategoryList(items, items.size());
  }

  private QueryCondition keywordCondition(String q) {
    return q == null || q.isBlank() ? null : new QueryColumn("name").likeRaw(LikePatterns.contains(q));
  }
}
