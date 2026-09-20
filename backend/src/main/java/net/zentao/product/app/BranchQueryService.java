package net.zentao.product.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.BranchView;
import net.zentao.product.api.ProductApi;
import net.zentao.product.domain.BranchRepository;
import net.zentao.product.domain.ProductRepository;
import org.springframework.stereotype.Component;

/** 分支列表查询（product 卡 §3.2 DSL 白名单；子对象继承产品可见性）。 */
@Component
public class BranchQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("status", "id"),
      Set.of("id", "sort", "createdAt"),
      Set.of("name"));

  private static final Map<String, String> COLUMNS = Map.of(
      "status", "status",
      "id", "id",
      "name", "name",
      "sort", "sort",
      "createdAt", "created_at");

  private final BranchRepository repository;
  private final ProductRepository productRepository;
  private final ProductApi productApi;

  public BranchQueryService(BranchRepository repository, ProductRepository productRepository, ProductApi productApi) {
    this.repository = repository;
    this.productRepository = productRepository;
    this.productApi = productApi;
  }

  /** BranchList 载荷（contract：items + total）。 */
  public record BranchList(List<BranchView> items, long total) {}

  public BranchList page(long productId, SessionPrincipal principal, Map<String, String[]> params) {
    ProductGuard.requireVisible(productRepository, productApi, principal, productId);
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition injected = new QueryColumn("deleted_at").isNull().and(new QueryColumn("product_id").eq(productId));
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> java.util.Optional.empty(), injected);
    List<BranchView> items = repository.queryPage(query, filters.offset(), filters.limit()).stream()
        .map(BranchView::of)
        .toList();
    Filters countFilters = new Filters(filters.clauses(), List.of(), 1, 1, filters.q());
    QueryWrapper countQuery = FilterPredicate.compile(countFilters, COLUMNS::get, value -> java.util.Optional.empty(),
        injected);
    return new BranchList(items, repository.countByQuery(countQuery));
  }

  private QueryCondition keywordCondition(String q) {
    return q == null || q.isBlank() ? null : new QueryColumn("name").like("%" + q + "%");
  }
}
