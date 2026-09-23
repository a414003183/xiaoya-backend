package net.zentao.product.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditDiff;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.product.api.CategoryView;
import net.zentao.product.app.CategoryHandlers;
import net.zentao.product.app.CategoryQueryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 分类端点（product 卡 §5 categories 族 4 行）。 */
@RestController
@RequestMapping("/api/v1")
public class CategoryController {

  private final CategoryQueryService queryService;
  private final CategoryHandlers handlers;
  private final SessionResolver resolver;

  public CategoryController(CategoryQueryService queryService, CategoryHandlers handlers, SessionResolver resolver) {
    this.queryService = queryService;
    this.handlers = handlers;
    this.resolver = resolver;
  }

  @GetMapping("/products/{productId}/categories")
  @Operation(operationId = "listCategories")
  @RequirePrivilege("product-view")
  public DataEnvelope<CategoryQueryService.CategoryList> list(@PathVariable long productId,
      HttpServletRequest request) {
    return DataEnvelope.of(queryService.list(productId, resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/products/{productId}/categories")
  @Operation(operationId = "createCategory")
  @RequirePrivilege("category-manage")
  @Audit(action = "category-create", objectType = "category")
  public DataEnvelope<CategoryView> create(@PathVariable long productId,
      @RequestBody CategoryHandlers.CategoryCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.create(resolver.resolve(request), productId, body));
  }

  @PatchMapping("/categories/{categoryId}")
  @Operation(operationId = "updateCategory")
  @RequirePrivilege("category-manage")
  @Audit(action = "category-update", objectType = "category")
  @AuditDiff(objectType = "category")
  public DataEnvelope<CategoryView> update(@PathVariable long categoryId,
      @RequestBody CategoryHandlers.CategoryUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.update(resolver.resolve(request), categoryId, body));
  }

  @DeleteMapping("/categories/{categoryId}")
  @Operation(operationId = "deleteCategory")
  @RequirePrivilege("category-manage")
  @Audit(action = "category-delete", objectType = "category")
  @AuditDiff(objectType = "category")
  public DataEnvelope<Void> delete(@PathVariable long categoryId, HttpServletRequest request) {
    handlers.delete(resolver.resolve(request), categoryId);
    return DataEnvelope.empty();
  }
}
