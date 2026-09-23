package net.zentao.product.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditDiff;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.product.api.BranchView;
import net.zentao.product.app.BranchHandlers;
import net.zentao.product.app.BranchQueryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 分支端点（product 卡 §5 branches 族 6 行）。 */
@RestController
@RequestMapping("/api/v1")
public class BranchController {

  private final BranchQueryService queryService;
  private final BranchHandlers handlers;
  private final SessionResolver resolver;

  public BranchController(BranchQueryService queryService, BranchHandlers handlers, SessionResolver resolver) {
    this.queryService = queryService;
    this.handlers = handlers;
    this.resolver = resolver;
  }

  @GetMapping("/products/{productId}/branches")
  @Operation(operationId = "listBranches")
  @RequirePrivilege("product-view")
  public DataEnvelope<BranchQueryService.BranchList> list(@PathVariable long productId,
      HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(productId, resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/products/{productId}/branches")
  @Operation(operationId = "createBranch")
  @RequirePrivilege("branch-manage")
  @Audit(action = "branch-create", objectType = "branch")
  public DataEnvelope<BranchView> create(@PathVariable long productId,
      @RequestBody BranchHandlers.BranchCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.create(resolver.resolve(request), productId, body));
  }

  @PatchMapping("/branches/{branchId}")
  @Operation(operationId = "updateBranch")
  @RequirePrivilege("branch-manage")
  @Audit(action = "branch-update", objectType = "branch")
  @AuditDiff(objectType = "branch")
  public DataEnvelope<BranchView> update(@PathVariable long branchId,
      @jakarta.validation.Valid @RequestBody BranchHandlers.BranchUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.update(resolver.resolve(request), branchId, body));
  }

  @PostMapping("/branches/{branchId}/close")
  @Operation(operationId = "closeBranch")
  @RequirePrivilege("branch-manage")
  @Audit(action = "branch-close", objectType = "branch")
  @AuditDiff(objectType = "branch")
  public DataEnvelope<BranchView> close(@PathVariable long branchId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.close(resolver.resolve(request), branchId, comment(body)));
  }

  @PostMapping("/branches/{branchId}/activate")
  @Operation(operationId = "activateBranch")
  @RequirePrivilege("branch-manage")
  @Audit(action = "branch-activate", objectType = "branch")
  @AuditDiff(objectType = "branch")
  public DataEnvelope<BranchView> activate(@PathVariable long branchId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.activate(resolver.resolve(request), branchId, comment(body)));
  }

  @PostMapping("/branches/{branchId}/set-default")
  @Operation(operationId = "setDefaultBranch")
  @RequirePrivilege("branch-manage")
  @Audit(action = "branch-set-default", objectType = "branch")
  public DataEnvelope<BranchView> setDefault(@PathVariable long branchId, HttpServletRequest request) {
    return DataEnvelope.of(handlers.setDefault(resolver.resolve(request), branchId));
  }

  @DeleteMapping("/branches/{branchId}")
  @Operation(operationId = "deleteBranch")
  @RequirePrivilege("branch-delete")
  @Audit(action = "branch-delete", objectType = "branch")
  @AuditDiff(objectType = "branch")
  public DataEnvelope<Void> delete(@PathVariable long branchId, HttpServletRequest request) {
    handlers.delete(resolver.resolve(request), branchId);
    return DataEnvelope.empty();
  }

  private static String comment(CommentRequest body) {
    return body == null ? null : body.comment();
  }
}
