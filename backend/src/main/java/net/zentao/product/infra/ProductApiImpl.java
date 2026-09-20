package net.zentao.product.infra;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.DataScope;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.product.api.ProductList;
import net.zentao.product.api.ProductView;
import net.zentao.product.app.ProductQueryService;
import net.zentao.product.domain.Product;
import net.zentao.product.domain.ProductRepository;
import net.zentao.product.domain.ProductVisibility;
import org.springframework.stereotype.Component;

/**
 * 产品可见性实现（product 卡 §7 + platform 卡 §7.2）：
 * 可见集 = 产品自身 ACL 规则 ∪ 所属各组 acl.products 追加；超管不受限。
 * 列表路径的可见集由 {@code ProductQueryService.scopeOf} 计算后回传本类，避免构造器互引。
 *
 * ponytail: 可见集在内存过滤（产品为小集合，全表扫描可接受）；升级路径 = 维护 acl 关联表或 SQL 侧 JSON 过滤。
 */
@Component
public class ProductApiImpl implements ProductApi {

  private final ProductRepository repository;
  private final DataScope dataScope;
  private final ProductQueryService queryService;

  public ProductApiImpl(ProductRepository repository, DataScope dataScope, ProductQueryService queryService) {
    this.repository = repository;
    this.dataScope = dataScope;
    this.queryService = queryService;
  }

  @Override
  public ProductScope visibleScope(SessionPrincipal principal) {
    return queryService.scopeOf(principal);
  }

  @Override
  public Optional<ProductView> findById(long productId) {
    return repository.findActiveById(productId).map(ProductView::of);
  }

  @Override
  public ProductList pageByIds(List<Long> productIds, SessionPrincipal principal, Map<String, String[]> params) {
    return queryService.pageByIds(productIds, principal, params);
  }

  @Override
  public ProductView requireVisible(SessionPrincipal principal, long productId) {
    Product product = repository.findActiveById(productId).orElseThrow(() -> ApiException.notFound("产品"));
    boolean visible = ProductVisibility.isVisible(product, principal.account(), dataScope.isSuperAdmin(principal))
        || dataScope.aclUnion(principal).products().contains(productId);
    if (!visible) {
      throw ApiException.dataForbidden("无权访问该产品。");
    }
    return ProductView.of(product);
  }
}
