package net.zentao.doc.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.doc.api.DocCategoryTree;
import net.zentao.doc.api.DocCategoryView;
import net.zentao.doc.app.DocCategoryHandlers;
import net.zentao.doc.app.DocCategoryQueryService;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.DataEnvelope;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库内目录端点（doc 卡 §5 目录 4 行）。
 * 写端点按库嵌套（/doc-spaces/{docSpaceId}/categories/{categoryId}）：/categories/{categoryId} 路径归 product 域。
 */
@RestController
@RequestMapping("/api/v1")
public class DocCategoryController {

  private final DocCategoryQueryService queryService;
  private final DocCategoryHandlers handlers;
  private final SessionResolver resolver;

  public DocCategoryController(DocCategoryQueryService queryService, DocCategoryHandlers handlers,
      SessionResolver resolver) {
    this.queryService = queryService;
    this.handlers = handlers;
    this.resolver = resolver;
  }

  @GetMapping("/doc-spaces/{docSpaceId}/categories")
  @Operation(operationId = "listDocCategories")
  @RequirePrivilege("doc-view")
  public DataEnvelope<DocCategoryTree> tree(@PathVariable long docSpaceId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.tree(resolver.resolve(request), docSpaceId));
  }

  @PostMapping("/doc-spaces/{docSpaceId}/categories")
  @Operation(operationId = "createDocCategory")
  @RequirePrivilege("doc-edit")
  public DataEnvelope<DocCategoryView> create(@PathVariable long docSpaceId,
      @RequestBody DocCategoryHandlers.DocCategoryCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(DocCategoryView.of(handlers.create(resolver.resolve(request), docSpaceId, body)));
  }

  @PatchMapping("/doc-spaces/{docSpaceId}/categories/{categoryId}")
  @Operation(operationId = "updateDocCategory")
  @RequirePrivilege("doc-edit")
  public DataEnvelope<DocCategoryView> update(@PathVariable long docSpaceId, @PathVariable long categoryId,
      @RequestBody DocCategoryHandlers.DocCategoryUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(
        DocCategoryView.of(handlers.update(resolver.resolve(request), docSpaceId, categoryId, body)));
  }

  @DeleteMapping("/doc-spaces/{docSpaceId}/categories/{categoryId}")
  @Operation(operationId = "deleteDocCategory")
  @RequirePrivilege("doc-edit")
  public DataEnvelope<Void> delete(@PathVariable long docSpaceId, @PathVariable long categoryId,
      HttpServletRequest request) {
    handlers.delete(resolver.resolve(request), docSpaceId, categoryId);
    return DataEnvelope.empty();
  }
}
