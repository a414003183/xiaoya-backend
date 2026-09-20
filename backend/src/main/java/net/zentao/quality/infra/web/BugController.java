package net.zentao.quality.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import net.zentao.platform.activity.ActivityQueryService;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.BatchActionRequest;
import net.zentao.platform.web.BatchActionResult;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.quality.api.BatchCreateResult;
import net.zentao.quality.api.BugList;
import net.zentao.quality.api.BugView;
import net.zentao.quality.app.ActivateBugHandler;
import net.zentao.quality.app.AssignBugHandler;
import net.zentao.quality.app.BatchBugActionHandler;
import net.zentao.quality.app.BatchCreateBugHandler;
import net.zentao.quality.app.CloseBugHandler;
import net.zentao.quality.app.ConfirmBugHandler;
import net.zentao.quality.app.CreateBugHandler;
import net.zentao.quality.app.BugQueryService;
import net.zentao.quality.app.DeleteBugHandler;
import net.zentao.quality.app.ResolveBugHandler;
import net.zentao.quality.app.UpdateBugHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bug 端点（quality 卡 §5 Bug 12 行）。 */
@RestController
@RequestMapping("/api/v1")
public class BugController {

  private final BugQueryService queryService;
  private final CreateBugHandler createHandler;
  private final UpdateBugHandler updateHandler;
  private final DeleteBugHandler deleteHandler;
  private final ConfirmBugHandler confirmHandler;
  private final ResolveBugHandler resolveHandler;
  private final ActivateBugHandler activateHandler;
  private final CloseBugHandler closeHandler;
  private final AssignBugHandler assignHandler;
  private final BatchCreateBugHandler batchCreateHandler;
  private final BatchBugActionHandler batchHandler;
  private final ActivityQueryService activityQueryService;
  private final SessionResolver resolver;

  public BugController(BugQueryService queryService, CreateBugHandler createHandler,
      UpdateBugHandler updateHandler, DeleteBugHandler deleteHandler, ConfirmBugHandler confirmHandler,
      ResolveBugHandler resolveHandler, ActivateBugHandler activateHandler, CloseBugHandler closeHandler,
      AssignBugHandler assignHandler, BatchCreateBugHandler batchCreateHandler, BatchBugActionHandler batchHandler,
      ActivityQueryService activityQueryService, SessionResolver resolver) {
    this.queryService = queryService;
    this.createHandler = createHandler;
    this.updateHandler = updateHandler;
    this.deleteHandler = deleteHandler;
    this.confirmHandler = confirmHandler;
    this.resolveHandler = resolveHandler;
    this.activateHandler = activateHandler;
    this.closeHandler = closeHandler;
    this.assignHandler = assignHandler;
    this.batchCreateHandler = batchCreateHandler;
    this.batchHandler = batchHandler;
    this.activityQueryService = activityQueryService;
    this.resolver = resolver;
  }

  @GetMapping("/products/{productId}/bugs")
  @Operation(operationId = "listBugs")
  @RequirePrivilege("bug-view")
  public DataEnvelope<BugList> list(@PathVariable long productId, HttpServletRequest request) {
    return DataEnvelope.of(
        queryService.pageByProduct(productId, resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/products/{productId}/bugs")
  @Operation(operationId = "createBug")
  @RequirePrivilege("bug-create")
  public DataEnvelope<BugView> create(@PathVariable long productId,
      @RequestBody CreateBugHandler.BugCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(createHandler.handle(resolver.resolve(request), productId, body));
  }

  @PostMapping("/products/{productId}/bugs/batch")
  @Operation(operationId = "batchCreateBugs")
  @RequirePrivilege("bug-create")
  public DataEnvelope<BatchCreateResult> batchCreate(@PathVariable long productId,
      @RequestBody BugBatchCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(batchCreateHandler.handle(resolver.resolve(request), productId, body.items()));
  }

  @GetMapping("/bugs/{bugId}")
  @Operation(operationId = "getBug")
  @RequirePrivilege("bug-view")
  public DataEnvelope<BugView> detail(@PathVariable long bugId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.detail(resolver.resolve(request), bugId));
  }

  @PatchMapping("/bugs/{bugId}")
  @Operation(operationId = "updateBug")
  @RequirePrivilege("bug-edit")
  public DataEnvelope<BugView> update(@PathVariable long bugId,
      @RequestBody UpdateBugHandler.BugUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(updateHandler.handle(resolver.resolve(request), bugId, body));
  }

  @DeleteMapping("/bugs/{bugId}")
  @Operation(operationId = "deleteBug")
  @RequirePrivilege("bug-delete")
  public DataEnvelope<Void> delete(@PathVariable long bugId, HttpServletRequest request) {
    deleteHandler.handle(resolver.resolve(request), bugId);
    return DataEnvelope.empty();
  }

  @PostMapping("/bugs/{bugId}/confirm")
  @Operation(operationId = "confirmBug")
  @RequirePrivilege("bug-confirm")
  public DataEnvelope<BugView> confirm(@PathVariable long bugId,
      @RequestBody ConfirmBugHandler.BugConfirmRequest body, HttpServletRequest request) {
    return DataEnvelope.of(confirmHandler.handle(resolver.resolve(request), bugId, body));
  }

  @PostMapping("/bugs/{bugId}/resolve")
  @Operation(operationId = "resolveBug")
  @RequirePrivilege("bug-resolve")
  public DataEnvelope<BugView> resolve(@PathVariable long bugId,
      @RequestBody ResolveBugHandler.BugResolveRequest body, HttpServletRequest request) {
    return DataEnvelope.of(resolveHandler.handle(resolver.resolve(request), bugId, body));
  }

  @PostMapping("/bugs/{bugId}/activate")
  @Operation(operationId = "activateBug")
  @RequirePrivilege("bug-activate")
  public DataEnvelope<BugView> activate(@PathVariable long bugId,
      @RequestBody ActivateBugHandler.BugActivateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(activateHandler.handle(resolver.resolve(request), bugId, body));
  }

  @PostMapping("/bugs/{bugId}/close")
  @Operation(operationId = "closeBug")
  @RequirePrivilege("bug-close")
  public DataEnvelope<BugView> close(@PathVariable long bugId, @RequestBody(required = false) CommentRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(closeHandler.handle(resolver.resolve(request), bugId, body));
  }

  @PostMapping("/bugs/{bugId}/assign")
  @Operation(operationId = "assignBug")
  @RequirePrivilege("bug-assign")
  public DataEnvelope<BugView> assign(@PathVariable long bugId,
      @RequestBody AssignBugHandler.BugAssignRequest body, HttpServletRequest request) {
    return DataEnvelope.of(assignHandler.handle(resolver.resolve(request), bugId, body));
  }

  @PostMapping("/bugs/batch")
  @Operation(operationId = "batchOperateBugs")
  public DataEnvelope<BatchActionResult> batch(@RequestBody BatchActionRequest body, HttpServletRequest request) {
    return DataEnvelope.of(batchHandler.handle(resolver.resolve(request), body));
  }

  @GetMapping("/bugs/{bugId}/activities")
  @Operation(operationId = "listBugActivities")
  @RequirePrivilege("bug-view")
  public DataEnvelope<ActivityQueryService.ActivityList> activities(@PathVariable long bugId,
      @RequestParam(required = false) Integer limit, @RequestParam(required = false) Long beforeId,
      HttpServletRequest request) {
    return DataEnvelope.of(activityQueryService.list("bug", bugId, null, limit, beforeId));
  }

  public record BugBatchCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CreateBugHandler.BugCreateRequest> items) {}
}
