package net.zentao.product.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.filters.LikePatterns;
import net.zentao.platform.rbac.DataScope;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.product.api.ProductList;
import net.zentao.product.api.ProductView;
import net.zentao.product.domain.Product;
import net.zentao.product.domain.ProductRepository;
import net.zentao.product.domain.ProductVisibility;
import org.springframework.stereotype.Component;

/**
 * 产品列表查询（product 卡 §3.1 DSL 白名单 + §7 DataScope 注入 `id IN 可见集`，A6）。
 * 可见集在本层计算（{@code ProductApiImpl} 转调）——避开「api 实现 ↔ 查询服务」构造器互引。
 */
@Component
public class ProductQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("status", "type", "acl", "programId", "po", "qd", "rd", "createdBy", "createdAt", "id"),
      Set.of("id", "name", "sort", "createdAt"),
      Set.of("name", "code"));

  private static final Map<String, String> COLUMNS = Map.ofEntries(
      Map.entry("status", "status"),
      Map.entry("type", "type"),
      Map.entry("acl", "acl"),
      Map.entry("programId", "program_id"),
      Map.entry("po", "po"),
      Map.entry("qd", "qd"),
      Map.entry("rd", "rd"),
      Map.entry("createdBy", "created_by"),
      Map.entry("createdAt", "created_at"),
      Map.entry("id", "id"),
      Map.entry("name", "name"),
      Map.entry("sort", "sort"));

  private final ProductRepository repository;
  private final DataScope dataScope;

  public ProductQueryService(ProductRepository repository, DataScope dataScope) {
    this.repository = repository;
    this.dataScope = dataScope;
  }

  /** 产品可见集（product 卡 §7 + platform 卡 §7.2：自身 ACL ∪ 组 acl.products；超管不受限）。 */
  public ProductApi.ProductScope scopeOf(SessionPrincipal principal) {
    if (dataScope.isSuperAdmin(principal)) {
      return new ProductApi.ProductScope(true, Set.of());
    }
    Set<Long> ids = new LinkedHashSet<>();
    for (Product product : repository.findAllActive()) {
      if (ProductVisibility.isVisible(product, principal.account(), false)) {
        ids.add(product.id());
      }
    }
    ids.addAll(dataScope.aclUnion(principal).products());
    return new ProductApi.ProductScope(false, ids);
  }

  /** 产品列表（product 卡 §5 GET /products；载荷 contract：items + total）。 */
  public ProductList page(SessionPrincipal principal, Map<String, String[]> params) {
    return query(scopeOf(principal), params, null);
  }

  /** id 集合列表（project 域关联产品；空集合 → 恒假条件，避免 IN () 语法错误）。 */
  public ProductList pageByIds(List<Long> productIds, SessionPrincipal principal, Map<String, String[]> params) {
    QueryCondition scope = new QueryColumn("id").in(productIds.isEmpty() ? List.of(-1L) : List.copyOf(productIds));
    return query(scopeOf(principal), params, scope);
  }

  private ProductList query(ProductApi.ProductScope productScope, Map<String, String[]> params,
      QueryCondition scope) {
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition injected = new QueryColumn("deleted_at").isNull();
    if (scope != null) {
      injected = injected.and(scope);
    }
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    if (!productScope.visibleToAll()) {
      // 可见集为空 → 恒假条件，避免 IN () 语法错误
      injected = injected.and(new QueryColumn("id")
          .in(productScope.productIds().isEmpty() ? List.of(-1L) : List.copyOf(productScope.productIds())));
    }

    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> java.util.Optional.empty(), injected);
    List<ProductView> items = repository.queryPage(query, filters.offset(), filters.limit()).stream()
        .map(ProductView::of)
        .toList();
    QueryWrapper countQuery = FilterPredicate.compile(filters.forCount(), COLUMNS::get, value -> java.util.Optional.empty(), injected);
    return new ProductList(items, repository.countByQuery(countQuery));
  }

  private QueryCondition keywordCondition(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    String like = LikePatterns.contains(q);
    return new QueryColumn("name").likeRaw(like).or(new QueryColumn("code").likeRaw(like));
  }
}
