package net.zentao.project.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.activity.ActivityQueryService;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.BatchActionResult;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.product.api.ProductList;
import net.zentao.project.api.ProjectApi;
import net.zentao.project.api.ProjectView;
import net.zentao.project.app.AddStakeholderHandler;
import net.zentao.project.app.CreateProjectHandler;
import net.zentao.project.app.CreateProjectHandler.ProjectCreateRequest;
import net.zentao.project.app.DeleteProjectHandler;
import net.zentao.project.app.LinkProjectStoriesHandler;
import net.zentao.project.app.ProjectActionHandler;
import net.zentao.project.app.ProjectActionHandler.ProjectActivateRequest;
import net.zentao.project.app.ProjectActionHandler.ProjectCloseRequest;
import net.zentao.project.app.ProjectActionHandler.ProjectStartRequest;
import net.zentao.project.app.ProjectQueryService;
import net.zentao.project.app.ProjectStoryQueryService;
import net.zentao.project.app.RemoveStakeholderHandler;
import net.zentao.project.app.ReplaceProjectProductsHandler;
import net.zentao.project.app.ReplaceWhitelistHandler;
import net.zentao.project.app.StakeholderQueryService;
import net.zentao.project.app.SubmitTeamMembersHandler;
import net.zentao.project.app.TeamMemberQueryService;
import net.zentao.project.app.UpdateProjectHandler;
import net.zentao.project.app.UpdateProjectHandler.ProjectUpdateRequest;
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

/** 项目端点（project 卡 §5 projects 族 + 成员/干系人/白名单/产品/需求关联各族）。 */
@RestController
@RequestMapping("/api/v1")
public class ProjectController {

  private final ProjectQueryService queryService;
  private final CreateProjectHandler createHandler;
  private final UpdateProjectHandler updateHandler;
  private final DeleteProjectHandler deleteHandler;
  private final ProjectApi projectApi;
  private final ProjectActionHandler actionHandler;
  private final ActivityQueryService activityQueryService;
  private final TeamMemberQueryService teamMemberQueryService;
  private final SubmitTeamMembersHandler submitTeamMembersHandler;
  private final StakeholderQueryService stakeholderQueryService;
  private final AddStakeholderHandler addStakeholderHandler;
  private final RemoveStakeholderHandler removeStakeholderHandler;
  private final ReplaceWhitelistHandler whitelistHandler;
  private final ReplaceProjectProductsHandler productsHandler;
  private final LinkProjectStoriesHandler linkStoriesHandler;
  private final ProjectStoryQueryService storyQueryService;
  private final SessionResolver resolver;

  public ProjectController(ProjectQueryService queryService, CreateProjectHandler createHandler,
      UpdateProjectHandler updateHandler, DeleteProjectHandler deleteHandler, ProjectApi projectApi,
      ProjectActionHandler actionHandler, ActivityQueryService activityQueryService,
      TeamMemberQueryService teamMemberQueryService, SubmitTeamMembersHandler submitTeamMembersHandler,
      StakeholderQueryService stakeholderQueryService, AddStakeholderHandler addStakeholderHandler,
      RemoveStakeholderHandler removeStakeholderHandler, ReplaceWhitelistHandler whitelistHandler,
      ReplaceProjectProductsHandler productsHandler, LinkProjectStoriesHandler linkStoriesHandler,
      ProjectStoryQueryService storyQueryService, SessionResolver resolver) {
    this.queryService = queryService;
    this.createHandler = createHandler;
    this.updateHandler = updateHandler;
    this.deleteHandler = deleteHandler;
    this.projectApi = projectApi;
    this.actionHandler = actionHandler;
    this.activityQueryService = activityQueryService;
    this.teamMemberQueryService = teamMemberQueryService;
    this.submitTeamMembersHandler = submitTeamMembersHandler;
    this.stakeholderQueryService = stakeholderQueryService;
    this.addStakeholderHandler = addStakeholderHandler;
    this.removeStakeholderHandler = removeStakeholderHandler;
    this.whitelistHandler = whitelistHandler;
    this.productsHandler = productsHandler;
    this.linkStoriesHandler = linkStoriesHandler;
    this.storyQueryService = storyQueryService;
    this.resolver = resolver;
  }

  @GetMapping("/projects")
  @Operation(operationId = "listProjects")
  @RequirePrivilege("project-view")
  public DataEnvelope<ProjectQueryService.ProjectList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(resolver.resolve(request), "project", request.getParameterMap()));
  }

  @PostMapping("/projects")
  @Operation(operationId = "createProject")
  @RequirePrivilege("project-create")
  public DataEnvelope<ProjectView> create(@RequestBody ProjectCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(createHandler.handleProject(resolver.resolve(request), body));
  }

  @GetMapping("/projects/{projectId}")
  @Operation(operationId = "getProject")
  @RequirePrivilege("project-view")
  public DataEnvelope<ProjectView> detail(@PathVariable long projectId, HttpServletRequest request) {
    return DataEnvelope.of(projectApi.requireVisible(resolver.resolve(request), projectId, "project"));
  }

  @PatchMapping("/projects/{projectId}")
  @Operation(operationId = "updateProject")
  @RequirePrivilege("project-edit")
  public DataEnvelope<ProjectView> update(@PathVariable long projectId, @RequestBody ProjectUpdateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(updateHandler.handle(resolver.resolve(request), projectId, body));
  }

  @DeleteMapping("/projects/{projectId}")
  @Operation(operationId = "deleteProject")
  @RequirePrivilege("project-delete")
  public DataEnvelope<Void> delete(@PathVariable long projectId, HttpServletRequest request) {
    deleteHandler.handle(resolver.resolve(request), projectId, "project");
    return DataEnvelope.empty();
  }

  @GetMapping("/projects/{projectId}/products")
  @Operation(operationId = "listProjectProducts")
  @RequirePrivilege("project-view")
  public DataEnvelope<ProductList> products(@PathVariable long projectId, HttpServletRequest request) {
    return DataEnvelope.of(productsHandler.listProject(resolver.resolve(request), projectId,
        request.getParameterMap()));
  }

  @PostMapping("/projects/{projectId}/products")
  @Operation(operationId = "replaceProjectProducts")
  @RequirePrivilege("project-edit")
  public DataEnvelope<ReplaceProjectProductsHandler.ProjectProductRequest> replaceProducts(@PathVariable long projectId,
      @RequestBody ReplaceProjectProductsHandler.ProjectProductRequest body, HttpServletRequest request) {
    return DataEnvelope.of(productsHandler.replace(resolver.resolve(request), projectId, body));
  }

  @GetMapping("/projects/{projectId}/stories")
  @Operation(operationId = "listProjectStories")
  @RequirePrivilege("project-view")
  public DataEnvelope<StoryList> stories(@PathVariable long projectId, HttpServletRequest request) {
    return DataEnvelope.of(storyQueryService.page(resolver.resolve(request), "project", projectId,
        request.getParameterMap()));
  }

  @PostMapping("/projects/{projectId}/stories")
  @Operation(operationId = "linkProjectStories")
  @RequirePrivilege("project-link-story")
  public DataEnvelope<BatchActionResult> linkStories(@PathVariable long projectId,
      @RequestBody LinkProjectStoriesHandler.StoryLinkRequest body, HttpServletRequest request) {
    return DataEnvelope.of(linkStoriesHandler.handle(resolver.resolve(request), "project", projectId, body));
  }

  @DeleteMapping("/projects/{projectId}/stories/{storyId}")
  @Operation(operationId = "unlinkProjectStory")
  @RequirePrivilege("project-link-story")
  public DataEnvelope<Void> unlinkStory(@PathVariable long projectId, @PathVariable long storyId,
      HttpServletRequest request) {
    linkStoriesHandler.unlink(resolver.resolve(request), "project", projectId, storyId);
    return DataEnvelope.empty();
  }

  @GetMapping("/projects/{projectId}/members")
  @Operation(operationId = "listProjectMembers")
  @RequirePrivilege("project-view")
  public DataEnvelope<TeamMemberQueryService.TeamMemberList> members(@PathVariable long projectId,
      HttpServletRequest request) {
    return DataEnvelope.of(teamMemberQueryService.page(resolver.resolve(request), "project", projectId,
        request.getParameterMap()));
  }

  @PostMapping("/projects/{projectId}/members")
  @Operation(operationId = "submitProjectMembers")
  @RequirePrivilege("project-manage-members")
  public DataEnvelope<SubmitTeamMembersHandler.TeamMemberSubmitResult> submitMembers(@PathVariable long projectId,
      @RequestBody SubmitTeamMembersHandler.TeamMemberSubmitRequest body, HttpServletRequest request) {
    return DataEnvelope.of(submitTeamMembersHandler.handle(resolver.resolve(request), "project", projectId, body));
  }

  @GetMapping("/projects/{projectId}/stakeholders")
  @Operation(operationId = "listProjectStakeholders")
  @RequirePrivilege("stakeholder-view")
  public DataEnvelope<StakeholderQueryService.StakeholderList> stakeholders(@PathVariable long projectId,
      HttpServletRequest request) {
    return DataEnvelope.of(stakeholderQueryService.page(resolver.resolve(request), "project", projectId,
        request.getParameterMap()));
  }

  @PostMapping("/projects/{projectId}/stakeholders")
  @Operation(operationId = "addProjectStakeholder")
  @RequirePrivilege("stakeholder-manage")
  public DataEnvelope<StakeholderQueryService.StakeholderView> addStakeholder(@PathVariable long projectId,
      @RequestBody AddStakeholderHandler.StakeholderCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(addStakeholderHandler.handle(resolver.resolve(request), "project", projectId, body));
  }

  @DeleteMapping("/projects/{projectId}/stakeholders/{stakeholderId}")
  @Operation(operationId = "removeProjectStakeholder")
  @RequirePrivilege("stakeholder-manage")
  public DataEnvelope<Void> removeStakeholder(@PathVariable long projectId, @PathVariable long stakeholderId,
      HttpServletRequest request) {
    removeStakeholderHandler.handle(resolver.resolve(request), "project", projectId, stakeholderId);
    return DataEnvelope.empty();
  }

  @GetMapping("/projects/{projectId}/whitelist")
  @Operation(operationId = "getProjectWhitelist")
  @RequirePrivilege("project-view")
  public DataEnvelope<ReplaceWhitelistHandler.WhitelistRequest> whitelist(@PathVariable long projectId,
      HttpServletRequest request) {
    return DataEnvelope.of(whitelistHandler.list(resolver.resolve(request), "project", projectId));
  }

  @PostMapping("/projects/{projectId}/whitelist")
  @Operation(operationId = "replaceProjectWhitelist")
  @RequirePrivilege("project-whitelist")
  public DataEnvelope<ReplaceWhitelistHandler.WhitelistRequest> replaceWhitelist(@PathVariable long projectId,
      @RequestBody ReplaceWhitelistHandler.WhitelistRequest body, HttpServletRequest request) {
    return DataEnvelope.of(whitelistHandler.replace(resolver.resolve(request), "project", projectId, body));
  }

  @GetMapping("/projects/{projectId}/executions")
  @Operation(operationId = "listProjectExecutions")
  @RequirePrivilege("execution-view")
  public DataEnvelope<ProjectQueryService.ProjectList> executions(@PathVariable long projectId,
      HttpServletRequest request) {
    projectApi.requireVisible(resolver.resolve(request), projectId, "project");
    return DataEnvelope.of(queryService.children(resolver.resolve(request), "execution", projectId,
        request.getParameterMap()));
  }

  @PostMapping("/projects/{projectId}/executions")
  @Operation(operationId = "createExecution")
  @RequirePrivilege("execution-create")
  public DataEnvelope<ProjectView> createExecution(@PathVariable long projectId,
      @RequestBody ProjectCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(createHandler.handleExecution(resolver.resolve(request), projectId, body));
  }

  @GetMapping("/projects/{projectId}/activities")
  @Operation(operationId = "listProjectActivities")
  @RequirePrivilege("project-view")
  public DataEnvelope<ActivityQueryService.ActivityList> activities(@PathVariable long projectId,
      @RequestParam(required = false) Integer limit, @RequestParam(required = false) Long beforeId,
      HttpServletRequest request) {
    projectApi.requireVisible(resolver.resolve(request), projectId, "project");
    return DataEnvelope.of(activityQueryService.list("project", projectId, null, limit, beforeId));
  }

  @PostMapping("/projects/{projectId}/start")
  @Operation(operationId = "startProject")
  @RequirePrivilege("project-start")
  public DataEnvelope<ProjectView> start(@PathVariable long projectId,
      @RequestBody(required = false) ProjectStartRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.start(resolver.resolve(request), projectId, "project", body));
  }

  @PostMapping("/projects/{projectId}/suspend")
  @Operation(operationId = "suspendProject")
  @RequirePrivilege("project-suspend")
  public DataEnvelope<ProjectView> suspend(@PathVariable long projectId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.suspend(resolver.resolve(request), projectId, "project", comment(body)));
  }
  @PostMapping("/projects/{projectId}/resume")
  @Operation(operationId = "resumeProject")
  @RequirePrivilege("project-resume")
  public DataEnvelope<ProjectView> resume(@PathVariable long projectId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.resume(resolver.resolve(request), projectId, "project", comment(body)));
  }
  @PostMapping("/projects/{projectId}/delay")
  @Operation(operationId = "delayProject")
  @RequirePrivilege("project-delay")
  public DataEnvelope<ProjectView> delay(@PathVariable long projectId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.delay(resolver.resolve(request), projectId, "project", comment(body)));
  }
  @PostMapping("/projects/{projectId}/close")
  @Operation(operationId = "closeProject")
  @RequirePrivilege("project-close")
  public DataEnvelope<ProjectView> close(@PathVariable long projectId,
      @RequestBody(required = false) ProjectCloseRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.close(resolver.resolve(request), projectId, "project", body));
  }

  @PostMapping("/projects/{projectId}/activate")
  @Operation(operationId = "activateProject")
  @RequirePrivilege("project-activate")
  public DataEnvelope<ProjectView> activate(@PathVariable long projectId,
      @RequestBody(required = false) ProjectActivateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.activate(resolver.resolve(request), projectId, "project", body));
  }

  private static String comment(CommentRequest body) {
    return body == null ? null : body.comment();
  }
}
