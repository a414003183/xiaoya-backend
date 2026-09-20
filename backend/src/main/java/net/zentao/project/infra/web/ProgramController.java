package net.zentao.project.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.product.api.ProductList;
import net.zentao.project.api.ProjectApi;
import net.zentao.project.api.ProjectView;
import net.zentao.project.app.AddStakeholderHandler;
import net.zentao.project.app.CreateProjectHandler;
import net.zentao.project.app.CreateProjectHandler.ProjectCreateRequest;
import net.zentao.project.app.DeleteProjectHandler;
import net.zentao.project.app.ProjectActionHandler;
import net.zentao.project.app.ProjectActionHandler.ProjectActivateRequest;
import net.zentao.project.app.ProjectActionHandler.ProjectCloseRequest;
import net.zentao.project.app.ProjectActionHandler.ProjectStartRequest;
import net.zentao.project.app.ProjectQueryService;
import net.zentao.project.app.RemoveStakeholderHandler;
import net.zentao.project.app.ReplaceProjectProductsHandler;
import net.zentao.project.app.StakeholderQueryService;
import net.zentao.project.app.UpdateProjectHandler;
import net.zentao.project.app.UpdateProjectHandler.ProjectUpdateRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 项目集端点（project 卡 §5 programs 族 + 干系人/关联产品两族）。 */
@RestController
@RequestMapping("/api/v1")
public class ProgramController {

  private final ProjectQueryService queryService;
  private final CreateProjectHandler createHandler;
  private final UpdateProjectHandler updateHandler;
  private final DeleteProjectHandler deleteHandler;
  private final ProjectApi projectApi;
  private final ProjectActionHandler actionHandler;
  private final ReplaceProjectProductsHandler productsHandler;
  private final StakeholderQueryService stakeholderQueryService;
  private final AddStakeholderHandler addStakeholderHandler;
  private final RemoveStakeholderHandler removeStakeholderHandler;
  private final SessionResolver resolver;

  public ProgramController(ProjectQueryService queryService, CreateProjectHandler createHandler,
      UpdateProjectHandler updateHandler, DeleteProjectHandler deleteHandler, ProjectApi projectApi,
      ProjectActionHandler actionHandler, ReplaceProjectProductsHandler productsHandler,
      StakeholderQueryService stakeholderQueryService, AddStakeholderHandler addStakeholderHandler,
      RemoveStakeholderHandler removeStakeholderHandler, SessionResolver resolver) {
    this.queryService = queryService;
    this.createHandler = createHandler;
    this.updateHandler = updateHandler;
    this.deleteHandler = deleteHandler;
    this.projectApi = projectApi;
    this.actionHandler = actionHandler;
    this.productsHandler = productsHandler;
    this.stakeholderQueryService = stakeholderQueryService;
    this.addStakeholderHandler = addStakeholderHandler;
    this.removeStakeholderHandler = removeStakeholderHandler;
    this.resolver = resolver;
  }

  @GetMapping("/programs")
  @Operation(operationId = "listPrograms")
  @RequirePrivilege("program-view")
  public DataEnvelope<ProjectQueryService.ProjectList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(resolver.resolve(request), "program", request.getParameterMap()));
  }

  @PostMapping("/programs")
  @Operation(operationId = "createProgram")
  @RequirePrivilege("program-create")
  public DataEnvelope<ProjectView> create(@RequestBody ProjectCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(createHandler.handleProgram(resolver.resolve(request), body));
  }

  @GetMapping("/programs/{programId}")
  @Operation(operationId = "getProgram")
  @RequirePrivilege("program-view")
  public DataEnvelope<ProjectView> detail(@PathVariable long programId, HttpServletRequest request) {
    return DataEnvelope.of(projectApi.requireVisible(resolver.resolve(request), programId, "program"));
  }

  @PatchMapping("/programs/{programId}")
  @Operation(operationId = "updateProgram")
  @RequirePrivilege("program-edit")
  public DataEnvelope<ProjectView> update(@PathVariable long programId, @RequestBody ProjectUpdateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(updateHandler.handle(resolver.resolve(request), programId, body));
  }

  @DeleteMapping("/programs/{programId}")
  @Operation(operationId = "deleteProgram")
  @RequirePrivilege("program-delete")
  public DataEnvelope<Void> delete(@PathVariable long programId, HttpServletRequest request) {
    deleteHandler.handle(resolver.resolve(request), programId, "program");
    return DataEnvelope.empty();
  }

  @GetMapping("/programs/{programId}/programs")
  @Operation(operationId = "listSubPrograms")
  @RequirePrivilege("program-view")
  public DataEnvelope<ProjectQueryService.ProjectList> children(@PathVariable long programId,
      HttpServletRequest request) {
    projectApi.requireVisible(resolver.resolve(request), programId, "program");
    return DataEnvelope.of(queryService.children(resolver.resolve(request), "program", programId,
        request.getParameterMap()));
  }

  @GetMapping("/programs/{programId}/projects")
  @Operation(operationId = "listProgramProjects")
  @RequirePrivilege("program-view")
  public DataEnvelope<ProjectQueryService.ProjectList> projects(@PathVariable long programId,
      HttpServletRequest request) {
    projectApi.requireVisible(resolver.resolve(request), programId, "program");
    return DataEnvelope.of(queryService.children(resolver.resolve(request), "project", programId,
        request.getParameterMap()));
  }

  @GetMapping("/programs/{programId}/products")
  @Operation(operationId = "listProgramProducts")
  @RequirePrivilege("program-view")
  public DataEnvelope<ProductList> products(@PathVariable long programId, HttpServletRequest request) {
    return DataEnvelope.of(productsHandler.listProgram(resolver.resolve(request), programId,
        request.getParameterMap()));
  }

  @GetMapping("/programs/{programId}/stakeholders")
  @Operation(operationId = "listProgramStakeholders")
  @RequirePrivilege("stakeholder-view")
  public DataEnvelope<StakeholderQueryService.StakeholderList> stakeholders(@PathVariable long programId,
      HttpServletRequest request) {
    return DataEnvelope.of(stakeholderQueryService.page(resolver.resolve(request), "program", programId,
        request.getParameterMap()));
  }

  @PostMapping("/programs/{programId}/stakeholders")
  @Operation(operationId = "addProgramStakeholder")
  @RequirePrivilege("stakeholder-manage")
  public DataEnvelope<StakeholderQueryService.StakeholderView> addStakeholder(@PathVariable long programId,
      @RequestBody AddStakeholderHandler.StakeholderCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(addStakeholderHandler.handle(resolver.resolve(request), "program", programId, body));
  }

  @DeleteMapping("/programs/{programId}/stakeholders/{stakeholderId}")
  @Operation(operationId = "removeProgramStakeholder")
  @RequirePrivilege("stakeholder-manage")
  public DataEnvelope<Void> removeStakeholder(@PathVariable long programId, @PathVariable long stakeholderId,
      HttpServletRequest request) {
    removeStakeholderHandler.handle(resolver.resolve(request), "program", programId, stakeholderId);
    return DataEnvelope.empty();
  }

  @PostMapping("/programs/{programId}/start")
  @Operation(operationId = "startProgram")
  @RequirePrivilege("program-start")
  public DataEnvelope<ProjectView> start(@PathVariable long programId,
      @RequestBody(required = false) ProjectStartRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.start(resolver.resolve(request), programId, "program", body));
  }

  @PostMapping("/programs/{programId}/suspend")
  @Operation(operationId = "suspendProgram")
  @RequirePrivilege("program-suspend")
  public DataEnvelope<ProjectView> suspend(@PathVariable long programId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.suspend(resolver.resolve(request), programId, "program", comment(body)));
  }
  @PostMapping("/programs/{programId}/resume")
  @Operation(operationId = "resumeProgram")
  @RequirePrivilege("program-resume")
  public DataEnvelope<ProjectView> resume(@PathVariable long programId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.resume(resolver.resolve(request), programId, "program", comment(body)));
  }
  @PostMapping("/programs/{programId}/delay")
  @Operation(operationId = "delayProgram")
  @RequirePrivilege("program-delay")
  public DataEnvelope<ProjectView> delay(@PathVariable long programId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.delay(resolver.resolve(request), programId, "program", comment(body)));
  }
  @PostMapping("/programs/{programId}/close")
  @Operation(operationId = "closeProgram")
  @RequirePrivilege("program-close")
  public DataEnvelope<ProjectView> close(@PathVariable long programId,
      @RequestBody(required = false) ProjectCloseRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.close(resolver.resolve(request), programId, "program", body));
  }

  @PostMapping("/programs/{programId}/activate")
  @Operation(operationId = "activateProgram")
  @RequirePrivilege("program-activate")
  public DataEnvelope<ProjectView> activate(@PathVariable long programId,
      @RequestBody(required = false) ProjectActivateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(actionHandler.activate(resolver.resolve(request), programId, "program", body));
  }

  private static String comment(CommentRequest body) {
    return body == null ? null : body.comment();
  }
}
