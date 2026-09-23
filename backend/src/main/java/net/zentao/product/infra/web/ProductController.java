package net.zentao.product.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.activity.ActivityQueryService;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditDiff;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.BatchActionRequest;
import net.zentao.platform.web.BatchActionResult;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.product.api.ProductApi;
import net.zentao.product.api.ProductList;
import net.zentao.product.api.ProductView;
import net.zentao.product.app.CreateProductHandler;
import net.zentao.product.app.DeleteProductHandler;
import net.zentao.product.app.ProductActionHandler;
import net.zentao.product.app.ProductBatchHandler;
import net.zentao.product.app.ProductQueryService;
import net.zentao.product.app.UpdateProductHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 产品端点（product 卡 §5 products 族 8 行）。 */
@RestController
@RequestMapping("/api/v1")
public class ProductController {

  private final ProductQueryService queryService;
  private final CreateProductHandler createHandler;
  private final UpdateProductHandler updateHandler;
  private final ProductActionHandler actionHandler;
  private final ProductBatchHandler batchHandler;
  private final DeleteProductHandler deleteHandler;
  private final ProductApi productApi;
  private final ActivityQueryService activityQueryService;
  private final SessionResolver resolver;

  public ProductController(ProductQueryService queryService, CreateProductHandler createHandler,
      UpdateProductHandler updateHandler, ProductActionHandler actionHandler, ProductBatchHandler batchHandler,
      DeleteProductHandler deleteHandler, ProductApi productApi, ActivityQueryService activityQueryService,
      SessionResolver resolver) {
    this.queryService = queryService;
    this.createHandler = createHandler;
    this.updateHandler = updateHandler;
    this.actionHandler = actionHandler;
    this.batchHandler = batchHandler;
    this.deleteHandler = deleteHandler;
    this.productApi = productApi;
    this.activityQueryService = activityQueryService;
    this.resolver = resolver;
  }

  @GetMapping("/products")
  @Operation(operationId = "listProducts")
  @RequirePrivilege("product-view")
  public DataEnvelope<ProductList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/products")
  @Operation(operationId = "createProduct")
  @RequirePrivilege("product-create")
  @Audit(action = "product-create", objectType = "product")
  public DataEnvelope<ProductView> create(@RequestBody CreateProductHandler.ProductCreateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(ProductView.of(createHandler.handle(resolver.resolve(request), body)));
  }

  @PostMapping("/products/batch")
  @Operation(operationId = "batchProducts")
  @Audit(action = "batch-operation", objectType = "product")
  public DataEnvelope<BatchActionResult> batch(@RequestBody BatchActionRequest body, HttpServletRequest request) {
    return DataEnvelope.of(batchHandler.handle(resolver.resolve(request), body));
  }

  @GetMapping("/products/{productId}")
  @Operation(operationId = "getProduct")
  @RequirePrivilege("product-view")
  public DataEnvelope<ProductView> detail(@PathVariable long productId, HttpServletRequest request) {
    SessionPrincipal principal = resolver.resolve(request);
    return DataEnvelope.of(productApi.requireVisible(principal, productId));
  }

  @PatchMapping("/products/{productId}")
  @Operation(operationId = "updateProduct")
  @RequirePrivilege("product-edit")
  @Audit(action = "product-update", objectType = "product")
  @AuditDiff(objectType = "product")
  public DataEnvelope<ProductView> update(@PathVariable long productId,
      @RequestBody UpdateProductHandler.ProductUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(ProductView.of(updateHandler.handle(resolver.resolve(request), productId, body)));
  }

  @PostMapping("/products/{productId}/close")
  @Operation(operationId = "closeProduct")
  @RequirePrivilege("product-close")
  @Audit(action = "product-close", objectType = "product")
  @AuditDiff(objectType = "product")
  public DataEnvelope<ProductView> close(@PathVariable long productId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.close(resolver.resolve(request), productId, comment(body)));
  }

  @PostMapping("/products/{productId}/activate")
  @Operation(operationId = "activateProduct")
  @RequirePrivilege("product-activate")
  @Audit(action = "product-activate", objectType = "product")
  @AuditDiff(objectType = "product")
  public DataEnvelope<ProductView> activate(@PathVariable long productId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.activate(resolver.resolve(request), productId, comment(body)));
  }

  @DeleteMapping("/products/{productId}")
  @Operation(operationId = "deleteProduct")
  @RequirePrivilege("product-delete")
  @Audit(action = "product-delete", objectType = "product")
  @AuditDiff(objectType = "product")
  public DataEnvelope<Void> delete(@PathVariable long productId, HttpServletRequest request) {
    deleteHandler.handle(resolver.resolve(request), productId);
    return DataEnvelope.empty();
  }

  @GetMapping("/products/{productId}/activities")
  @Operation(operationId = "listProductActivities")
  @RequirePrivilege("product-view")
  public DataEnvelope<ActivityQueryService.ActivityList> activities(@PathVariable long productId,
      @RequestParam(required = false) Integer limit, @RequestParam(required = false) Long beforeId,
      HttpServletRequest request) {
    return DataEnvelope.of(activityQueryService.list("product", productId, null, limit, beforeId));
  }

  private static String comment(CommentRequest body) {
    return body == null ? null : body.comment();
  }
}
