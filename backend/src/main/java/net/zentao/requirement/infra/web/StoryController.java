package net.zentao.requirement.infra.web;

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
import net.zentao.requirement.api.StoryView;
import net.zentao.requirement.app.ActivateStoryHandler;
import net.zentao.requirement.app.AssignStoryHandler;
import net.zentao.requirement.app.ChangeDoneStoryHandler;
import net.zentao.requirement.app.ChangeStoryHandler;
import net.zentao.requirement.app.CloseStoryHandler;
import net.zentao.requirement.app.CreateStoryHandler;
import net.zentao.requirement.app.DeleteStoryHandler;
import net.zentao.requirement.app.PassStoryHandler;
import net.zentao.requirement.app.RejectStoryHandler;
import net.zentao.requirement.app.StoryBatchCreateHandler;
import net.zentao.requirement.app.StoryBatchHandler;
import net.zentao.requirement.app.StoryQueryService;
import net.zentao.requirement.app.SubmitReviewStoryHandler;
import net.zentao.requirement.app.UpdateStoryHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 需求端点（requirement 卡 §5 全 15 行）。 */
@RestController
@RequestMapping("/api/v1")
public class StoryController {

  private final StoryQueryService queryService;
  private final CreateStoryHandler createHandler;
  private final UpdateStoryHandler updateHandler;
  private final SubmitReviewStoryHandler submitReviewHandler;
  private final PassStoryHandler passHandler;
  private final RejectStoryHandler rejectHandler;
  private final ChangeStoryHandler changeHandler;
  private final ChangeDoneStoryHandler changeDoneHandler;
  private final CloseStoryHandler closeHandler;
  private final ActivateStoryHandler activateHandler;
  private final AssignStoryHandler assignHandler;
  private final StoryBatchCreateHandler batchCreateHandler;
  private final StoryBatchHandler batchHandler;
  private final DeleteStoryHandler deleteHandler;
  private final ActivityQueryService activityQueryService;
  private final SessionResolver resolver;

  public StoryController(StoryQueryService queryService, CreateStoryHandler createHandler,
      UpdateStoryHandler updateHandler, SubmitReviewStoryHandler submitReviewHandler, PassStoryHandler passHandler,
      RejectStoryHandler rejectHandler, ChangeStoryHandler changeHandler, ChangeDoneStoryHandler changeDoneHandler,
      CloseStoryHandler closeHandler, ActivateStoryHandler activateHandler, AssignStoryHandler assignHandler,
      StoryBatchCreateHandler batchCreateHandler, StoryBatchHandler batchHandler,
      DeleteStoryHandler deleteHandler, ActivityQueryService activityQueryService, SessionResolver resolver) {
    this.queryService = queryService;
    this.createHandler = createHandler;
    this.updateHandler = updateHandler;
    this.submitReviewHandler = submitReviewHandler;
    this.passHandler = passHandler;
    this.rejectHandler = rejectHandler;
    this.changeHandler = changeHandler;
    this.changeDoneHandler = changeDoneHandler;
    this.closeHandler = closeHandler;
    this.activateHandler = activateHandler;
    this.assignHandler = assignHandler;
    this.batchCreateHandler = batchCreateHandler;
    this.batchHandler = batchHandler;
    this.deleteHandler = deleteHandler;
    this.activityQueryService = activityQueryService;
    this.resolver = resolver;
  }

  @GetMapping("/products/{productId}/stories")
  @Operation(operationId = "listStories")
  @RequirePrivilege("story-view")
  public DataEnvelope<net.zentao.requirement.api.StoryList> list(@PathVariable long productId,
      HttpServletRequest request) {
    return DataEnvelope.of(
        queryService.pageByProduct(productId, resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/products/{productId}/stories")
  @Operation(operationId = "createStory")
  @RequirePrivilege("story-create")
  public DataEnvelope<StoryView> create(@PathVariable long productId,
      @RequestBody CreateStoryHandler.StoryCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(createHandler.handle(resolver.resolve(request), productId, body));
  }

  @PostMapping("/products/{productId}/stories/batch")
  @Operation(operationId = "batchCreateStories")
  @RequirePrivilege("story-create")
  public DataEnvelope<StoryBatchCreateHandler.StoryBatchCreateResult> batchCreate(@PathVariable long productId,
      @RequestBody StoryBatchCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(batchCreateHandler.handle(resolver.resolve(request), productId, body.items()));
  }

  @GetMapping("/stories/{storyId}")
  @Operation(operationId = "getStory")
  @RequirePrivilege("story-view")
  public DataEnvelope<StoryView> detail(@PathVariable long storyId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.detail(resolver.resolve(request), storyId));
  }

  @PatchMapping("/stories/{storyId}")
  @Operation(operationId = "updateStory")
  @RequirePrivilege("story-edit")
  public DataEnvelope<StoryView> update(@PathVariable long storyId,
      @RequestBody UpdateStoryHandler.StoryUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(updateHandler.handle(resolver.resolve(request), storyId, body));
  }

  @PostMapping("/stories/{storyId}/submit-review")
  @Operation(operationId = "submitStoryReview")
  @RequirePrivilege("story-submit-review")
  public DataEnvelope<StoryView> submitReview(@PathVariable long storyId,
      @RequestBody(required = false) SubmitReviewStoryHandler.StorySubmitReviewRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(submitReviewHandler.handle(resolver.resolve(request), storyId, body));
  }

  @PostMapping("/stories/{storyId}/pass")
  @Operation(operationId = "passStory")
  @RequirePrivilege("story-pass")
  public DataEnvelope<StoryView> pass(@PathVariable long storyId,
      @RequestBody(required = false) PassStoryHandler.StoryPassRequest body, HttpServletRequest request) {
    return DataEnvelope.of(passHandler.handle(resolver.resolve(request), storyId, body));
  }

  @PostMapping("/stories/{storyId}/reject")
  @Operation(operationId = "rejectStory")
  @RequirePrivilege("story-pass")
  public DataEnvelope<StoryView> reject(@PathVariable long storyId,
      @RequestBody RejectStoryHandler.StoryRejectRequest body, HttpServletRequest request) {
    return DataEnvelope.of(rejectHandler.handle(resolver.resolve(request), storyId, body));
  }

  @PostMapping("/stories/{storyId}/change")
  @Operation(operationId = "changeStory")
  @RequirePrivilege("story-change")
  public DataEnvelope<StoryView> change(@PathVariable long storyId, HttpServletRequest request) {
    return DataEnvelope.of(changeHandler.handle(resolver.resolve(request), storyId));
  }

  @PostMapping("/stories/{storyId}/change-done")
  @Operation(operationId = "changeDoneStory")
  @RequirePrivilege("story-change")
  public DataEnvelope<StoryView> changeDone(@PathVariable long storyId,
      @RequestBody UpdateStoryHandler.StoryUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(changeDoneHandler.handle(resolver.resolve(request), storyId, body));
  }

  @PostMapping("/stories/{storyId}/close")
  @Operation(operationId = "closeStory")
  @RequirePrivilege("story-close")
  public DataEnvelope<StoryView> close(@PathVariable long storyId,
      @RequestBody CloseStoryHandler.StoryCloseRequest body, HttpServletRequest request) {
    return DataEnvelope.of(closeHandler.handle(resolver.resolve(request), storyId, body));
  }

  @PostMapping("/stories/{storyId}/activate")
  @Operation(operationId = "activateStory")
  @RequirePrivilege("story-activate")
  public DataEnvelope<StoryView> activate(@PathVariable long storyId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(activateHandler.handle(resolver.resolve(request), storyId, body));
  }

  @PostMapping("/stories/{storyId}/assign")
  @Operation(operationId = "assignStory")
  @RequirePrivilege("story-assign")
  public DataEnvelope<StoryView> assign(@PathVariable long storyId,
      @RequestBody AssignStoryHandler.StoryAssignRequest body, HttpServletRequest request) {
    return DataEnvelope.of(assignHandler.handle(resolver.resolve(request), storyId, body));
  }

  @PostMapping("/stories/batch")
  @Operation(operationId = "batchStories")
  public DataEnvelope<BatchActionResult> batch(@RequestBody BatchActionRequest body, HttpServletRequest request) {
    return DataEnvelope.of(batchHandler.handle(resolver.resolve(request), body));
  }

  @DeleteMapping("/stories/{storyId}")
  @Operation(operationId = "deleteStory")
  @RequirePrivilege("story-delete")
  public DataEnvelope<Void> delete(@PathVariable long storyId, HttpServletRequest request) {
    deleteHandler.handle(resolver.resolve(request), storyId);
    return DataEnvelope.empty();
  }

  @GetMapping("/stories/{storyId}/activities")
  @Operation(operationId = "listStoryActivities")
  @RequirePrivilege("story-view")
  public DataEnvelope<ActivityQueryService.ActivityList> activities(@PathVariable long storyId,
      @RequestParam(required = false) Integer limit, @RequestParam(required = false) Long beforeId,
      HttpServletRequest request) {
    return DataEnvelope.of(activityQueryService.list("story", storyId, null, limit, beforeId));
  }

  public record StoryBatchCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CreateStoryHandler.StoryCreateRequest> items) {}
}
