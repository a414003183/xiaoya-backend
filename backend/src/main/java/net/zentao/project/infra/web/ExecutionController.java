package net.zentao.project.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.activity.ActivityQueryService;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.project.api.ExecutionApi;
import net.zentao.project.api.ProjectView;
import net.zentao.project.app.ExecutionKanbanHandler;
import net.zentao.project.app.DeleteProjectHandler;
import net.zentao.project.app.LinkProjectStoriesHandler;
import net.zentao.project.app.ProjectActionHandler;
import net.zentao.project.app.ProjectActionHandler.ProjectActivateRequest;
import net.zentao.project.app.ProjectActionHandler.ProjectCloseRequest;
import net.zentao.project.app.ProjectActionHandler.ProjectStartRequest;
import net.zentao.project.app.ProjectQueryService;
import net.zentao.project.app.ProjectStoryQueryService;
import net.zentao.project.app.SubmitTeamMembersHandler;
import net.zentao.project.app.TeamMemberQueryService;
import net.zentao.project.app.UpdateProjectHandler;
import net.zentao.project.app.UpdateProjectHandler.ProjectUpdateRequest;
import net.zentao.requirement.api.StoryList;
import net.zentao.requirement.api.StoryView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 执行端点（project 卡 §5 executions 族 + 成员/关联需求/需求看板三族）。 */
@RestController
@RequestMapping("/api/v1")
public class ExecutionController {

  private final ProjectQueryService queryService;
  private final UpdateProjectHandler updateHandler;
  private final DeleteProjectHandler deleteHandler;
  private final ExecutionApi executionApi;
  private final ProjectActionHandler actionHandler;
  private final ActivityQueryService activityQueryService;
  private final TeamMemberQueryService teamMemberQueryService;
  private final SubmitTeamMembersHandler submitTeamMembersHandler;
  private final ProjectStoryQueryService storyQueryService;
  private final LinkProjectStoriesHandler linkStoriesHandler;
  private final ExecutionKanbanHandler kanbanHandler;
  private final SessionResolver resolver;

  public ExecutionController(ProjectQueryService queryService, UpdateProjectHandler updateHandler,
      DeleteProjectHandler deleteHandler, ExecutionApi executionApi, ProjectActionHandler actionHandler,
      ActivityQueryService activityQueryService, TeamMemberQueryService teamMemberQueryService,
      SubmitTeamMembersHandler submitTeamMembersHandler, ProjectStoryQueryService storyQueryService,
      LinkProjectStoriesHandler linkStoriesHandler, ExecutionKanbanHandler kanbanHandler,
      SessionResolver resolver) {
    this.queryService = queryService;
    this.updateHandler = updateHandler;
    this.deleteHandler = deleteHandler;
    this.executionApi = executionApi;
    this.actionHandler = actionHandler;
    this.activityQueryService = activityQueryService;
    this.teamMemberQueryService = teamMemberQueryService;
    this.submitTeamMembersHandler = submitTeamMembersHandler;
    this.storyQueryService = storyQueryService;
    this.linkStoriesHandler = linkStoriesHandler;
    this.kanbanHandler = kanbanHandler;
    this.resolver = resolver;
  }

  @GetMapping("/executions")
  @Operation(operationId = "listExecutions")
  @RequirePrivilege("execution-view")
  public DataEnvelope<ProjectQueryService.ProjectList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(resolver.resolve(request), "execution", request.getParameterMap()));
  }

  @GetMapping("/executions/{executionId}")
  @Operation(operationId = "getExecution")
  @RequirePrivilege("execution-view")
  public DataEnvelope<ProjectView> detail(@PathVariable long executionId, HttpServletRequest request) {
    return DataEnvelope.of(executionApi.requireExecution(resolver.resolve(request), executionId));
  }

  @PatchMapping("/executions/{executionId}")
  @Operation(operationId = "updateExecution")
  @RequirePrivilege("execution-edit")
  public DataEnvelope<ProjectView> update(@PathVariable long executionId, @RequestBody ProjectUpdateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(updateHandler.handle(resolver.resolve(request), executionId, body));
  }

  @DeleteMapping("/executions/{executionId}")
  @Operation(operationId = "deleteExecution")
  @RequirePrivilege("execution-delete")
  public DataEnvelope<Void> delete(@PathVariable long executionId, HttpServletRequest request) {
    deleteHandler.handle(resolver.resolve(request), executionId, "execution");
    return DataEnvelope.empty();
  }

  @GetMapping("/executions/{executionId}/members")
  @Operation(operationId = "listExecutionMembers")
  @RequirePrivilege("execution-view")
  public DataEnvelope<TeamMemberQueryService.TeamMemberList> members(@PathVariable long executionId,
      HttpServletRequest request) {
    return DataEnvelope.of(teamMemberQueryService.page(resolver.resolve(request), "execution", executionId,
        request.getParameterMap()));
  }

  @PostMapping("/executions/{executionId}/members")
  @Operation(operationId = "submitExecutionMembers")
  @RequirePrivilege("execution-manage-members")
  public DataEnvelope<SubmitTeamMembersHandler.TeamMemberSubmitResult> submitMembers(@PathVariable long executionId,
      @RequestBody SubmitTeamMembersHandler.TeamMemberSubmitRequest body, HttpServletRequest request) {
    return DataEnvelope.of(submitTeamMembersHandler.handle(resolver.resolve(request), "execution", executionId, body));
  }

  @GetMapping("/executions/{executionId}/stories")
  @Operation(operationId = "listExecutionStories")
  @RequirePrivilege("execution-view")
  public DataEnvelope<StoryList> stories(@PathVariable long executionId, HttpServletRequest request) {
    return DataEnvelope.of(storyQueryService.page(resolver.resolve(request), "execution", executionId,
        request.getParameterMap()));
  }

  @DeleteMapping("/executions/{executionId}/stories/{storyId}")
  @Operation(operationId = "unlinkExecutionStory")
  @RequirePrivilege("execution-edit")
  public DataEnvelope<Void> unlinkStory(@PathVariable long executionId, @PathVariable long storyId,
      HttpServletRequest request) {
    linkStoriesHandler.unlink(resolver.resolve(request), "execution", executionId, storyId);
    return DataEnvelope.empty();
  }

  @GetMapping("/executions/{executionId}/activities")
  @Operation(operationId = "listExecutionActivities")
  @RequirePrivilege("execution-view")
  public DataEnvelope<ActivityQueryService.ActivityList> activities(@PathVariable long executionId,
      @RequestParam(required = false) Integer limit, @RequestParam(required = false) Long beforeId,
      HttpServletRequest request) {
    executionApi.requireExecution(resolver.resolve(request), executionId);
    return DataEnvelope.of(activityQueryService.list("execution", executionId, null, limit, beforeId));
  }

  @GetMapping("/executions/{executionId}/kanban")
  @Operation(operationId = "getExecutionKanban")
  @RequirePrivilege("execution-view")
  public DataEnvelope<ExecutionKanbanHandler.ExecutionKanbanView> kanban(@PathVariable long executionId,
      HttpServletRequest request) {
    return DataEnvelope.of(kanbanHandler.board(resolver.resolve(request), executionId));
  }

  @PostMapping("/executions/{executionId}/kanban/cards/{cardId}/move")
  @Operation(operationId = "moveExecutionKanbanCard")
  @RequirePrivilege("execution-edit")
  public DataEnvelope<StoryView> moveCard(@PathVariable long executionId, @PathVariable long cardId,
      @RequestBody ExecutionKanbanHandler.ExecutionKanbanMoveRequest body, HttpServletRequest request) {
    return DataEnvelope.of(kanbanHandler.move(resolver.resolve(request), executionId, cardId, body));
  }

  @PostMapping("/executions/{executionId}/start")
  @Operation(operationId = "startExecution")
  @RequirePrivilege("execution-start")
  public DataEnvelope<ProjectView> start(@PathVariable long executionId,
      @RequestBody(required = false) ProjectStartRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.start(resolver.resolve(request), executionId, "execution", body));
  }

  @PostMapping("/executions/{executionId}/suspend")
  @Operation(operationId = "suspendExecution")
  @RequirePrivilege("execution-suspend")
  public DataEnvelope<ProjectView> suspend(@PathVariable long executionId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.suspend(resolver.resolve(request), executionId, "execution", comment(body)));
  }
  @PostMapping("/executions/{executionId}/resume")
  @Operation(operationId = "resumeExecution")
  @RequirePrivilege("execution-resume")
  public DataEnvelope<ProjectView> resume(@PathVariable long executionId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.resume(resolver.resolve(request), executionId, "execution", comment(body)));
  }
  @PostMapping("/executions/{executionId}/delay")
  @Operation(operationId = "delayExecution")
  @RequirePrivilege("execution-delay")
  public DataEnvelope<ProjectView> delay(@PathVariable long executionId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.delay(resolver.resolve(request), executionId, "execution", comment(body)));
  }
  @PostMapping("/executions/{executionId}/close")
  @Operation(operationId = "closeExecution")
  @RequirePrivilege("execution-close")
  public DataEnvelope<ProjectView> close(@PathVariable long executionId,
      @RequestBody(required = false) ProjectCloseRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.close(resolver.resolve(request), executionId, "execution", body));
  }

  @PostMapping("/executions/{executionId}/activate")
  @Operation(operationId = "activateExecution")
  @RequirePrivilege("execution-activate")
  public DataEnvelope<ProjectView> activate(@PathVariable long executionId,
      @RequestBody(required = false) ProjectActivateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.activate(resolver.resolve(request), executionId, "execution", body));
  }

  private static String comment(CommentRequest body) {
    return body == null ? null : body.comment();
  }
}
