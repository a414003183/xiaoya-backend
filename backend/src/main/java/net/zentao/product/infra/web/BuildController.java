package net.zentao.product.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.activity.ActivityQueryService;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.product.api.BuildView;
import net.zentao.product.api.LinkRequest;
import net.zentao.product.app.BuildHandlers;
import net.zentao.product.app.BuildLinkHandler;
import net.zentao.product.app.ReleaseBuildQueryService;
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

/** 构建端点（product 卡 §5 builds 族 10 行）。 */
@RestController
@RequestMapping("/api/v1")
public class BuildController {

  private final ReleaseBuildQueryService queryService;
  private final BuildHandlers handlers;
  private final BuildLinkHandler linkHandler;
  private final ActivityQueryService activityQueryService;
  private final SessionResolver resolver;

  public BuildController(ReleaseBuildQueryService queryService, BuildHandlers handlers, BuildLinkHandler linkHandler,
      ActivityQueryService activityQueryService, SessionResolver resolver) {
    this.queryService = queryService;
    this.handlers = handlers;
    this.linkHandler = linkHandler;
    this.activityQueryService = activityQueryService;
    this.resolver = resolver;
  }

  @GetMapping("/products/{productId}/builds")
  @Operation(operationId = "listBuilds")
  @RequirePrivilege("build-view")
  public DataEnvelope<ReleaseBuildQueryService.BuildList> list(@PathVariable long productId,
      HttpServletRequest request) {
    return DataEnvelope.of(queryService.builds(productId, resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/products/{productId}/builds")
  @Operation(operationId = "createBuild")
  @RequirePrivilege("build-create")
  public DataEnvelope<BuildView> create(@PathVariable long productId,
      @RequestBody BuildHandlers.BuildCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.create(resolver.resolve(request), productId, body));
  }

  @GetMapping("/builds/{buildId}")
  @Operation(operationId = "getBuild")
  @RequirePrivilege("build-view")
  public DataEnvelope<BuildView> detail(@PathVariable long buildId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.buildDetail(resolver.resolve(request), buildId));
  }

  @PatchMapping("/builds/{buildId}")
  @Operation(operationId = "updateBuild")
  @RequirePrivilege("build-edit")
  public DataEnvelope<BuildView> update(@PathVariable long buildId,
      @RequestBody BuildHandlers.BuildUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.update(resolver.resolve(request), buildId, body));
  }

  @DeleteMapping("/builds/{buildId}")
  @Operation(operationId = "deleteBuild")
  @RequirePrivilege("build-delete")
  public DataEnvelope<Void> delete(@PathVariable long buildId, HttpServletRequest request) {
    handlers.delete(resolver.resolve(request), buildId);
    return DataEnvelope.empty();
  }

  @GetMapping("/builds/{buildId}/stories")
  @Operation(operationId = "listBuildStories")
  @RequirePrivilege("build-view")
  public DataEnvelope<StoryList> stories(@PathVariable long buildId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.buildStories(buildId, resolver.resolve(request), request.getParameterMap()));
  }

  @GetMapping("/builds/{buildId}/bugs")
  @Operation(operationId = "listBuildBugs")
  @RequirePrivilege("build-view")
  public DataEnvelope<BugList> bugs(@PathVariable long buildId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.buildBugs(buildId, resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/builds/{buildId}/link")
  @Operation(operationId = "linkBuild")
  @RequirePrivilege("build-link")
  public DataEnvelope<BuildView> link(@PathVariable long buildId, @RequestBody LinkRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(linkHandler.link(resolver.resolve(request), buildId, body));
  }

  @PostMapping("/builds/{buildId}/unlink")
  @Operation(operationId = "unlinkBuild")
  @RequirePrivilege("build-link")
  public DataEnvelope<BuildView> unlink(@PathVariable long buildId, @RequestBody LinkRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(linkHandler.unlink(resolver.resolve(request), buildId, body));
  }

  @GetMapping("/builds/{buildId}/activities")
  @Operation(operationId = "listBuildActivities")
  @RequirePrivilege("build-view")
  public DataEnvelope<ActivityQueryService.ActivityList> activities(@PathVariable long buildId,
      @RequestParam(required = false) Integer limit, @RequestParam(required = false) Long beforeId,
      HttpServletRequest request) {
    return DataEnvelope.of(activityQueryService.list("build", buildId, null, limit, beforeId));
  }
}
