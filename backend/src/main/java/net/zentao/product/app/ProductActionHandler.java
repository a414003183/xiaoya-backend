package net.zentao.product.app;

import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.product.api.ProductView;
import net.zentao.product.domain.Product;
import net.zentao.product.domain.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 产品状态动作（product 卡 §4.1）：close/activate 走 workflow/product.yml，副作用（closedAt/动态流）由 YAML 落。 */
@Component
public class ProductActionHandler {

  private final ProductRepository repository;
  private final ProductApi productApi;
  private final WorkflowEngine engine;

  public ProductActionHandler(ProductRepository repository, ProductApi productApi, WorkflowEngine engine) {
    this.repository = repository;
    this.productApi = productApi;
    this.engine = engine;
  }

  @Transactional
  public ProductView close(SessionPrincipal actor, long productId, String comment) {
    return fire(actor, productId, "close", comment);
  }

  @Transactional
  public ProductView activate(SessionPrincipal actor, long productId, String comment) {
    return fire(actor, productId, "activate", comment);
  }

  private ProductView fire(SessionPrincipal actor, long productId, String action, String comment) {
    Product product = ProductGuard.requireVisible(repository, productApi, actor, productId);
    engine.fire(new WorkflowTargets.ProductTarget(product, actor.account()), action, comment);
    product.markUpdatedBy(actor.account());
    Product saved = repository.update(product)
        .orElseThrow(() -> ApiException.lockConflict());
    return ProductView.of(saved);
  }
}
