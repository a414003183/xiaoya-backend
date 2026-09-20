package net.zentao.product.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.activity.ActivityQueryService;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.product.api.LinkRequest;
import net.zentao.product.api.ReleaseView;
import net.zentao.product.app.ReleaseBuildQueryService;
import net.zentao.product.app.ReleaseHandlers;
import net.zentao.product.app.ReleaseLinkHandler;
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

/** 发布端点（product 卡 §5 releases 族 10 行）。 */
@RestController
@RequestMapping("/api/v1")
public class ReleaseController {

  private final ReleaseBuildQueryService queryService;
  private final ReleaseHandlers handlers;
  private final ReleaseLinkHandler linkHandler;
  private final ActivityQueryService activityQueryService;
  private final SessionResolver resolver;

  public ReleaseController(ReleaseBuildQueryService queryService, ReleaseHandlers handlers,
      ReleaseLinkHandler linkHandler, ActivityQueryService activityQueryService, SessionResolver resolver) {
    this.queryService = queryService;
    this.handlers = handlers;
    this.linkHandler = linkHandler;
    this.activityQueryService = activityQueryService;
    this.resolver = resolver;
  }

  @GetMapping("/products/{productId}/releases")
  @Operation(operationId = "listReleases")
  @RequirePrivilege("release-view")
  public DataEnvelope<ReleaseBuildQueryService.ReleaseList> list(@PathVariable long productId,
      HttpServletRequest request) {
    return DataEnvelope.of(queryService.releases(productId, resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/products/{productId}/releases")
  @Operation(operationId = "createRelease")
  @RequirePrivilege("release-create")
  public DataEnvelope<ReleaseView> create(@PathVariable long productId,
      @RequestBody ReleaseHandlers.ReleaseCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.create(resolver.resolve(request), productId, body));
  }

  @GetMapping("/releases/{releaseId}")
  @Operation(operationId = "getRelease")
  @RequirePrivilege("release-view")
  public DataEnvelope<ReleaseView> detail(@PathVariable long releaseId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.releaseDetail(resolver.resolve(request), releaseId));
  }

  @PatchMapping("/releases/{releaseId}")
  @Operation(operationId = "updateRelease")
  @RequirePrivilege("release-edit")
  public DataEnvelope<ReleaseView> update(@PathVariable long releaseId,
      @RequestBody ReleaseHandlers.ReleaseUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.update(resolver.resolve(request), releaseId, body));
  }

  @DeleteMapping("/releases/{releaseId}")
  @Operation(operationId = "deleteRelease")
  @RequirePrivilege("release-delete")
  public DataEnvelope<Void> delete(@PathVariable long releaseId, HttpServletRequest request) {
    handlers.delete(resolver.resolve(request), releaseId);
    return DataEnvelope.empty();
  }

  @PostMapping("/releases/{releaseId}/terminate")
  @Operation(operationId = "terminateRelease")
  @RequirePrivilege("release-terminate")
  public DataEnvelope<ReleaseView> terminate(@PathVariable long releaseId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.terminate(resolver.resolve(request), releaseId, comment(body)));
  }

  @GetMapping("/releases/{releaseId}/stories")
  @Operation(operationId = "listReleaseStories")
  @RequirePrivilege("release-view")
  public DataEnvelope<StoryList> stories(@PathVariable long releaseId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.releaseStories(releaseId, resolver.resolve(request), request.getParameterMap()));
  }

  @GetMapping("/releases/{releaseId}/bugs")
  @Operation(operationId = "listReleaseBugs")
  @RequirePrivilege("release-view")
  public DataEnvelope<BugList> bugs(@PathVariable long releaseId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.releaseBugs(releaseId, resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/releases/{releaseId}/link")
  @Operation(operationId = "linkRelease")
  @RequirePrivilege("release-link")
  public DataEnvelope<ReleaseView> link(@PathVariable long releaseId, @RequestBody LinkRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(linkHandler.link(resolver.resolve(request), releaseId, body));
  }

  @PostMapping("/releases/{releaseId}/unlink")
  @Operation(operationId = "unlinkRelease")
  @RequirePrivilege("release-link")
  public DataEnvelope<ReleaseView> unlink(@PathVariable long releaseId, @RequestBody LinkRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(linkHandler.unlink(resolver.resolve(request), releaseId, body));
  }

  @GetMapping("/releases/{releaseId}/activities")
  @Operation(operationId = "listReleaseActivities")
  @RequirePrivilege("release-view")
  public DataEnvelope<ActivityQueryService.ActivityList> activities(@PathVariable long releaseId,
      @RequestParam(required = false) Integer limit, @RequestParam(required = false) Long beforeId,
      HttpServletRequest request) {
    return DataEnvelope.of(activityQueryService.list("release", releaseId, null, limit, beforeId));
  }

  private static String comment(CommentRequest body) {
    return body == null ? null : body.comment();
  }
}
