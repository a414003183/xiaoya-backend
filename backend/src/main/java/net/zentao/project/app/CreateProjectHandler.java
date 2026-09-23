package net.zentao.project.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.meta.FieldDefValidator;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.project.api.ProjectApi;
import net.zentao.project.api.ProjectView;
import net.zentao.project.domain.AclEntryRepository;
import net.zentao.project.domain.Project;
import net.zentao.project.domain.ProjectHierarchy;
import net.zentao.project.domain.ProjectProductRepository;
import net.zentao.project.domain.ProjectRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 三型创建（project 卡 §2/§5/§8）：type 由端点定（执行型由请求体 type 定），parentId 挂载校验 + path/grade 生成；
 * 项目型必须关联产品（§1「无产品项目」不做），执行型必须挂项目下。
 */
@Component
public class CreateProjectHandler {

  private final ProjectRepository repository;
  private final AclEntryRepository aclEntryRepository;
  private final ProjectProductRepository projectProductRepository;
  private final ProjectApi projectApi;
  private final ProductApi productApi;
  private final AccountApi accountApi;
  private final ActivityRecorder activityRecorder;
  private final FieldDefValidator fieldDefValidator;

  public CreateProjectHandler(ProjectRepository repository, AclEntryRepository aclEntryRepository,
      ProjectProductRepository projectProductRepository, ProjectApi projectApi, ProductApi productApi,
      AccountApi accountApi, ActivityRecorder activityRecorder,
      FieldDefValidator fieldDefValidator) {
    this.fieldDefValidator = fieldDefValidator;
    this.repository = repository;
    this.aclEntryRepository = aclEntryRepository;
    this.projectProductRepository = projectProductRepository;
    this.projectApi = projectApi;
    this.productApi = productApi;
    this.accountApi = accountApi;
    this.activityRecorder = activityRecorder;
  }

  /** 创建请求体（contract：ProjectCreateRequest）：三型并集；type 仅执行型端点必填。 */
  public record ProjectCreateRequest(
      @Schema(allowableValues = {"kanban", "program", "project", "sprint", "stage"}) String type, Long parentId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name, String code,
      @Schema(allowableValues = {"kanban", "scrum", "waterfall"}) String model, Integer priority, LocalDate beginDate,
      LocalDate endDate, Integer days, BigDecimal budget,
      @Schema(allowableValues = {"CNY", "USD"}) String budgetUnit, String description, String pm,
      String po, String qd, String rd,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"open", "private", "program"}) String acl,
      List<String> whitelist, Integer sort, Boolean isMilestone,
      Map<String, Object> customFields, List<Long> productIds) {}

  @Transactional
  public ProjectView handleProgram(SessionPrincipal actor, ProjectCreateRequest command) {
    Project parent = requireParent(actor, command.parentId(), "program");
    return ProjectView.of(create(actor, "program", parent, command, command.productIds()));
  }

  @Transactional
  public ProjectView handleProject(SessionPrincipal actor, ProjectCreateRequest command) {
    if (command.beginDate() == null) {
      throw ApiException.validation(Map.of("beginDate", "required"));
    }
    if (command.endDate() == null) {
      throw ApiException.validation(Map.of("endDate", "required"));
    }
    if (command.productIds() == null || command.productIds().isEmpty()) {
      throw ApiException.validation(Map.of("productIds", "required"));
    }
    for (Long productId : command.productIds()) {
      boolean usable = productApi.findById(productId).filter(product -> productApi.canAccess(actor, productId))
          .isPresent();
      if (!usable) {
        throw ApiException.validation(Map.of("productIds", "invalid"));
      }
    }
    Project parent = requireParent(actor, command.parentId(), "project");
    Project project = create(actor, "project", parent, command, command.productIds());
    return ProjectView.of(project);
  }

  @Transactional
  public ProjectView handleExecution(SessionPrincipal actor, long projectId, ProjectCreateRequest command) {
    String executionType = command.type();
    if (executionType == null || !ProjectFields.EXECUTION_TYPES.contains(executionType)) {
      throw ApiException.validation(Map.of("type", "invalid"));
    }
    ProjectView parent = projectApi.requireVisible(actor, projectId, "project");
    if (command.beginDate() == null) {
      throw ApiException.validation(Map.of("beginDate", "required"));
    }
    if (command.endDate() == null) {
      throw ApiException.validation(Map.of("endDate", "required"));
    }
    Project parentProject = repository.findActiveById(parent.id()).orElseThrow(() -> ApiException.notFound("entity.project"));
    return ProjectView.of(create(actor, executionType, parentProject, command, command.productIds()));
  }

  private Project create(SessionPrincipal actor, String type, Project parent, ProjectCreateRequest command,
      List<Long> productIds) {
    String name = ProjectFields.requireName(command.name());
    ProjectFields.validateCode(command.code());
    ProjectFields.validateModel("project".equals(type) ? command.model() : null);
    ProjectFields.validatePriority(command.priority());
    ProjectFields.validateDays(command.days());
    ProjectFields.validateBudget(command.budget());
    ProjectFields.validateBudgetUnit(command.budgetUnit());
    ProjectFields.validateDates(command.beginDate(), command.endDate());
    String acl = ProjectFields.validateAcl(type, command.acl());
    ProjectFields.validateAccounts(accountApi,
        Map.of("pm", nullSafe(command.pm()), "po", nullSafe(command.po()), "qd", nullSafe(command.qd()),
            "rd", nullSafe(command.rd())),
        command.whitelist());

    Instant now = Instant.now();
    List<String> whitelist = command.whitelist() == null ? List.of() : List.copyOf(command.whitelist());
    fieldDefValidator.validate("project", command.customFields() == null ? java.util.Map.of() : command.customFields(), true);
    Project project = repository.insert(new Project(
        0,
        type,
        parent == null ? 0 : parent.id(),
        ",0,",
        ProjectHierarchy.gradeOf(parent),
        name,
        command.code(),
        "project".equals(type) && command.model() != null ? command.model() : "scrum",
        "wait",
        command.priority() == null ? 1 : command.priority(),
        command.beginDate(),
        command.endDate(),
        null,
        null,
        null,
        command.days() == null ? 0 : command.days(),
        command.budget(),
        command.budgetUnit() == null ? "CNY" : command.budgetUnit(),
        command.description(),
        command.pm(),
        command.po(),
        command.qd(),
        command.rd(),
        0,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        Boolean.TRUE.equals(command.isMilestone()),
        acl,
        whitelist,
        command.sort() == null ? 0 : command.sort(),
        command.customFields(),
        actor.account(),
        now,
        null,
        null,
        null,
        null,
        0));
    repository.updatePath(project.id(), ProjectHierarchy.pathOf(parent, project.id()), project.grade());
    aclEntryRepository.replace(type, project.id(), whitelist);
    if (productIds != null && !productIds.isEmpty()) {
      projectProductRepository.replace(project.id(), List.copyOf(new LinkedHashSet<>(productIds)));
    }
    activityRecorder.record(actor.account(), type, project.id(), "created", null, null);
    return repository.findActiveById(project.id()).orElseThrow();
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value;
  }

  /** parentId=0 → 顶级；>0 → 必须存在、类型匹配且可见（跨型挂载 → 42201）。 */
  private Project requireParent(SessionPrincipal actor, Long parentId, String childType) {
    if (parentId == null || parentId == 0) {
      return null;
    }
    Project parent = repository.findActiveById(parentId)
        .orElseThrow(() -> ApiException.validation(Map.of("parentId", "notFound")));
    if (!ProjectHierarchy.allowsParent(childType, parent)) {
      throw ApiException.validation(Map.of("parentId", "invalid"));
    }
    if (!projectApi.canAccess(actor, parentId)) {
      throw ApiException.keyed(ErrorCode.DATA_FORBIDDEN, "project.guard.forbiddenParent");
    }
    return parent;
  }
}
