package net.zentao.project.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.api.ProjectApi;
import net.zentao.project.api.ProjectView;
import net.zentao.project.domain.AclEntryRepository;
import net.zentao.project.domain.Project;
import net.zentao.project.domain.ProjectRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 部分更新（project 卡 §5 三型 PATCH 白名单；lockVersion 不符 → 40901）。
 * 白名单按型收窄：给不属于该型的字段传值 → 42201（null 视为未传，符合 03 §1 的 PATCH 语义）。
 */
@Component
public class UpdateProjectHandler {

  private final ProjectRepository repository;
  private final AclEntryRepository aclEntryRepository;
  private final ProjectApi projectApi;
  private final AccountApi accountApi;

  public UpdateProjectHandler(ProjectRepository repository, AclEntryRepository aclEntryRepository,
      ProjectApi projectApi, AccountApi accountApi) {
    this.repository = repository;
    this.aclEntryRepository = aclEntryRepository;
    this.projectApi = projectApi;
    this.accountApi = accountApi;
  }

  /** 更新请求体（contract：ProjectUpdateRequest）：三型 PATCH 白名单并集 + lockVersion。 */
  public record ProjectUpdateRequest(
      String name, String code,
      @Schema(allowableValues = {"kanban", "scrum", "waterfall"}) String model,
      LocalDate beginDate, LocalDate endDate, Integer days,
      BigDecimal budget, String pm, String po, String qd, String rd,
      @Schema(allowableValues = {"open", "private", "program"}) String acl, List<String> whitelist,
      String description, Integer sort, Boolean isMilestone,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  @Transactional
  public ProjectView handle(SessionPrincipal actor, long projectId, ProjectUpdateRequest command) {
    Project project = repository.findActiveById(projectId).orElseThrow(() -> ApiException.notFound("项目"));
    if (!projectApi.canAccess(actor, projectId)) {
      throw ApiException.dataForbidden("无权访问该项目。");
    }
    if (command.lockVersion() == null || command.lockVersion() != project.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新后重试。");
    }
    requireWritableFields(project.type(), command);
    ProjectFields.validateCode(command.code());
    ProjectFields.validateModel(command.model());
    ProjectFields.validateDays(command.days());
    ProjectFields.validateBudget(command.budget());
    ProjectFields.validateDates(command.beginDate(), command.endDate());
    String acl = command.acl() == null ? null : ProjectFields.validateAcl(project.type(), command.acl());
    ProjectFields.validateAccounts(accountApi,
        Map.of("pm", nullSafe(command.pm()), "po", nullSafe(command.po()), "qd", nullSafe(command.qd()),
            "rd", nullSafe(command.rd())),
        command.whitelist());

    project.update(command.name() == null ? null : command.name().trim(), command.code(), command.model(),
        command.beginDate(), command.endDate(), command.days(), command.budget(), command.pm(), command.po(),
        command.qd(), command.rd(), acl, command.whitelist(), command.description(), command.sort(),
        command.isMilestone());
    ProjectFields.validateDates(project.beginDate(), project.endDate());
    project.markUpdatedBy(actor.account());
    Project saved = repository.update(project)
        .orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
    if (command.whitelist() != null) {
      aclEntryRepository.replace(saved.type(), saved.id(), saved.whitelist());
    }
    return ProjectView.of(saved);
  }

  /** 型别可写字段（§5 PATCH 列）：非本型字段被赋值 → 42201。 */
  private static void requireWritableFields(String type, ProjectUpdateRequest command) {
    List<String> writable = switch (type) {
      case "program" -> List.of("name", "code", "beginDate", "endDate", "budget", "pm", "acl", "whitelist",
          "description", "sort");
      case "project" -> List.of("name", "code", "model", "beginDate", "endDate", "days", "budget", "pm", "po",
          "qd", "rd", "acl", "whitelist", "description", "sort");
      default -> List.of("name", "code", "model", "beginDate", "endDate", "days", "budget", "pm", "po", "qd",
          "rd", "acl", "whitelist", "description", "sort", "isMilestone");
    };
    Map<String, Object> provided = new java.util.LinkedHashMap<>();
    provided.put("model", command.model());
    provided.put("days", command.days());
    provided.put("po", command.po());
    provided.put("qd", command.qd());
    provided.put("rd", command.rd());
    provided.put("isMilestone", command.isMilestone());
    for (Map.Entry<String, Object> entry : provided.entrySet()) {
      if (entry.getValue() != null && !writable.contains(entry.getKey())) {
        throw ApiException.validation(Map.of(entry.getKey(), "notWritable"));
      }
    }
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value;
  }
}
