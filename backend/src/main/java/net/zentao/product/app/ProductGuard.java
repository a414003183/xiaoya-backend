package net.zentao.product.app;

import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.product.domain.Product;
import net.zentao.product.domain.ProductRepository;

/** 产品子对象（分支/分类/计划/发布/构建）继承产品可见性的统一守卫（product 卡 §7）。 */
final class ProductGuard {

  private ProductGuard() {}

  /** 子对象所属产品：不存在 → 40401；存在但不可见 → 40302。 */
  static Product requireVisible(ProductRepository repository, ProductApi productApi, SessionPrincipal actor,
      long productId) {
    Product product = repository.findActiveById(productId).orElseThrow(() -> ApiException.notFound("产品"));
    if (!productApi.canAccess(actor, productId)) {
      throw ApiException.dataForbidden("无权访问该产品。");
    }
    return product;
  }
}
