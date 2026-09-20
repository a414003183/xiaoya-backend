package net.zentao.project.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.project.api.ProjectApi;
import net.zentao.project.api.ProjectView;
import net.zentao.project.domain.Project;
import net.zentao.project.domain.ProjectRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 三型六动作（project 卡 §4/§5）：状态迁移与副作用（closedAt/closedBy/动态流/通知 pm）由 project.yml 声明，
 * 本处理器只做「取对象 → 写请求体驱动的字段 → fire → 落日期字段」。
 * realBeganDate/realEndDate 为 DATE 语义且请求体可覆写，故在 fire 前写入对象（同 P2「请求体字段先写入」约定）。
 */
@Component
public class ProjectActionHandler {

  private final ProjectRepository repository;
  private final ProjectApi projectApi;
  private final WorkflowEngine engine;

  public ProjectActionHandler(ProjectRepository repository, ProjectApi projectApi, WorkflowEngine engine) {
    this.repository = repository;
    this.projectApi = projectApi;
    this.engine = engine;
  }

  public record ProjectStartRequest(LocalDate realBeganDate, String comment) {}

  public record ProjectActivateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate beginDate,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate endDate, String comment) {}

  public record ProjectCloseRequest(LocalDate realEndDate, String comment) {}

  @Transactional
  public ProjectView start(SessionPrincipal actor, long id, String type, ProjectStartRequest body) {
    Project project = require(actor, id, type);
    project.setField("realBeganDate", body != null && body.realBeganDate() != null
        ? body.realBeganDate() : LocalDate.now());
    project.rememberFirstEndDate();
    return fire(actor, project, "start", body == null ? null : body.comment());
  }

  @Transactional
  public ProjectView suspend(SessionPrincipal actor, long id, String type, String comment) {
    return fire(actor, require(actor, id, type), "suspend", comment);
  }

  @Transactional
  public ProjectView resume(SessionPrincipal actor, long id, String type, String comment) {
    return fire(actor, require(actor, id, type), "resume", comment);
  }

  @Transactional
  public ProjectView delay(SessionPrincipal actor, long id, String type, String comment) {
    return fire(actor, require(actor, id, type), "delay", comment);
  }

  @Transactional
  public ProjectView close(SessionPrincipal actor, long id, String type, ProjectCloseRequest body) {
    Project project = require(actor, id, type);
    project.setField("realEndDate", body != null && body.realEndDate() != null
        ? body.realEndDate() : LocalDate.now());
    return fire(actor, project, "close", body == null ? null : body.comment());
  }

  @Transactional
  public ProjectView activate(SessionPrincipal actor, long id, String type, ProjectActivateRequest body) {
    if (body == null || body.beginDate() == null || body.endDate() == null) {
      throw ApiException.validation(java.util.Map.of("beginDate", "required"));
    }
    if (body.beginDate().isAfter(body.endDate())) {
      throw ApiException.guardNotSatisfied("守卫未满足：begin-before-end");
    }
    Project project = require(actor, id, type);
    project.update(null, null, null, body.beginDate(), body.endDate(), null, null, null, null, null, null, null,
        null, null, null, null);
    project.setField("realEndDate", null);
    return fire(actor, project, "activate", body.comment());
  }

  private ProjectView fire(SessionPrincipal actor, Project project, String action, String comment) {
    engine.fire(new ProjectWorkflowTargets.ProjectTarget(project, actor.account()), action, comment);
    project.markUpdatedBy(actor.account());
    Project saved = repository.update(project)
        .orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
    return ProjectView.of(saved);
  }

  private Project require(SessionPrincipal actor, long id, String type) {
    boolean execution = "execution".equals(type);
    Project project = repository.findActiveById(id).orElseThrow(() -> ApiException.notFound(label(type)));
    if (execution != project.isExecution() || (!execution && !type.equals(project.type()))) {
      throw ApiException.notFound(label(type));
    }
    if (!projectApi.canAccess(actor, id)) {
      throw ApiException.dataForbidden("无权访问该" + label(type) + "。");
    }
    return project;
  }

  private static String label(String type) {
    return "program".equals(type) ? "项目集" : "project".equals(type) ? "项目" : "执行";
  }
}
