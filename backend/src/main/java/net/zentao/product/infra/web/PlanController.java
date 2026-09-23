package net.zentao.product.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.activity.ActivityQueryService;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditDiff;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.product.api.LinkRequest;
import net.zentao.product.api.PlanView;
import net.zentao.product.app.PlanHandlers;
import net.zentao.product.app.PlanLinkHandler;
import net.zentao.product.app.PlanQueryService;
import net.zentao.quality.api.BugList;
import net.zentao.requirement.api.StoryList;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 计划端点（product 卡 §5 plans 族 13 行；stories/bugs 跨域经 StoryApi/BugApi）。 */
@RestController
@RequestMapping("/api/v1")
public class PlanController {

  private final PlanQueryService queryService;
  private final PlanHandlers handlers;
  private final PlanLinkHandler linkHandler;
  private final ActivityQueryService activityQueryService;
  private final SessionResolver resolver;

  public PlanController(PlanQueryService queryService, PlanHandlers handlers, PlanLinkHandler linkHandler,
      ActivityQueryService activityQueryService, SessionResolver resolver) {
    this.queryService = queryService;
    this.handlers = handlers;
    this.linkHandler = linkHandler;
    this.activityQueryService = activityQueryService;
    this.resolver = resolver;
  }

  @GetMapping("/products/{productId}/plans")
  @Operation(operationId = "listPlans")
  @RequirePrivilege("plan-view")
  public DataEnvelope<PlanQueryService.PlanList> list(@PathVariable long productId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(productId, resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/products/{productId}/plans")
  @Operation(operationId = "createPlan")
  @RequirePrivilege("plan-create")
  @Audit(action = "plan-create", objectType = "plan")
  public DataEnvelope<PlanView> create(@PathVariable long productId,
      @RequestBody PlanHandlers.PlanCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.create(resolver.resolve(request), productId, body));
  }

  @GetMapping("/plans/{planId}")
  @Operation(operationId = "getPlan")
  @RequirePrivilege("plan-view")
  public DataEnvelope<PlanView> detail(@PathVariable long planId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.detail(resolver.resolve(request), planId));
  }

  @PatchMapping("/plans/{planId}")
  @Operation(operationId = "updatePlan")
  @RequirePrivilege("plan-edit")
  @Audit(action = "plan-update", objectType = "plan")
  @AuditDiff(objectType = "plan")
  public DataEnvelope<PlanView> update(@PathVariable long planId,
      @RequestBody PlanHandlers.PlanUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.update(resolver.resolve(request), planId, body));
  }

  @PostMapping("/plans/{planId}/start")
  @Operation(operationId = "startPlan")
  @RequirePrivilege("plan-start")
  @Audit(action = "plan-start", objectType = "plan")
  @AuditDiff(objectType = "plan")
  public DataEnvelope<PlanView> start(@PathVariable long planId, HttpServletRequest request) {
    return DataEnvelope.of(handlers.start(resolver.resolve(request), planId));
  }

  @PostMapping("/plans/{planId}/finish")
  @Operation(operationId = "finishPlan")
  @RequirePrivilege("plan-finish")
  @Audit(action = "plan-finish", objectType = "plan")
  @AuditDiff(objectType = "plan")
  public DataEnvelope<PlanView> finish(@PathVariable long planId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.finish(resolver.resolve(request), planId, comment(body)));
  }

  @PostMapping("/plans/{planId}/close")
  @Operation(operationId = "closePlan")
  @RequirePrivilege("plan-close")
  @Audit(action = "plan-close", objectType = "plan")
  @AuditDiff(objectType = "plan")
  public DataEnvelope<PlanView> close(@PathVariable long planId,
      @RequestBody PlanHandlers.PlanCloseRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.close(resolver.resolve(request), planId, body));
  }

  @DeleteMapping("/plans/{planId}")
  @Operation(operationId = "deletePlan")
  @RequirePrivilege("plan-delete")
  @Audit(action = "plan-delete", objectType = "plan")
  @AuditDiff(objectType = "plan")
  public DataEnvelope<Void> delete(@PathVariable long planId, HttpServletRequest request) {
    handlers.delete(resolver.resolve(request), planId);
    return DataEnvelope.empty();
  }

  @PostMapping("/plans/{planId}/activate")
  @Operation(operationId = "activatePlan")
  @RequirePrivilege("plan-activate")
  @Audit(action = "plan-activate", objectType = "plan")
  @AuditDiff(objectType = "plan")
  public DataEnvelope<PlanView> activate(@PathVariable long planId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.activate(resolver.resolve(request), planId, comment(body)));
  }

  @GetMapping("/plans/{planId}/stories")
  @Operation(operationId = "listPlanStories")
  @RequirePrivilege("plan-view")
  public DataEnvelope<StoryList> stories(@PathVariable long planId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.stories(planId, resolver.resolve(request), request.getParameterMap()));
  }

  @GetMapping("/plans/{planId}/bugs")
  @Operation(operationId = "listPlanBugs")
  @RequirePrivilege("plan-view")
  public DataEnvelope<BugList> bugs(@PathVariable long planId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.bugs(planId, resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/plans/{planId}/link")
  @Operation(operationId = "linkPlan")
  @RequirePrivilege("plan-link")
  @Audit(action = "plan-link", objectType = "plan")
  public DataEnvelope<PlanView> link(@PathVariable long planId, @RequestBody LinkRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(linkHandler.link(resolver.resolve(request), planId, body));
  }

  @PostMapping("/plans/{planId}/unlink")
  @Operation(operationId = "unlinkPlan")
  @RequirePrivilege("plan-link")
  @Audit(action = "plan-unlink", objectType = "plan")
  public DataEnvelope<PlanView> unlink(@PathVariable long planId, @RequestBody LinkRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(linkHandler.unlink(resolver.resolve(request), planId, body));
  }

  @GetMapping("/plans/{planId}/activities")
  @Operation(operationId = "listPlanActivities")
  @RequirePrivilege("plan-view")
  public DataEnvelope<ActivityQueryService.ActivityList> activities(@PathVariable long planId,
      @RequestParam(required = false) Integer limit, @RequestParam(required = false) Long beforeId,
      HttpServletRequest request) {
    return DataEnvelope.of(activityQueryService.list("plan", planId, null, limit, beforeId));
  }

  private static String comment(CommentRequest body) {
    return body == null ? null : body.comment();
  }
}
