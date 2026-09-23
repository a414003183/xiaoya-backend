package net.zentao.product.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.meta.FieldDefValidator;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.PlanView;
import net.zentao.product.api.ProductApi;
import net.zentao.product.domain.Plan;
import net.zentao.product.domain.PlanRepository;
import net.zentao.product.domain.PlanStatusRollup;
import net.zentao.product.domain.ProductRepository;
import net.zentao.requirement.api.StoryApi;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 计划命令（product 卡 §3.4/§4.3/§5）：创建/编辑（父子仅两级、endDate ≥ beginDate）、
 * start/finish/close/activate 走 workflow/plan.yml，动作后触发父计划状态聚合。
 */
@Component
public class PlanHandlers {

  private static final Set<String> CLOSE_REASONS = Set.of("done", "cancel");

  private final PlanRepository repository;
  private final ProductRepository productRepository;
  private final ProductApi productApi;
  private final WorkflowEngine engine;
  private final ActivityRecorder activityRecorder;
  private final StoryApi storyApi;
  private final FieldDefValidator fieldDefValidator;

  public PlanHandlers(PlanRepository repository, ProductRepository productRepository, ProductApi productApi,
      WorkflowEngine engine, ActivityRecorder activityRecorder, StoryApi storyApi,
      FieldDefValidator fieldDefValidator) {
    this.fieldDefValidator = fieldDefValidator;
    this.repository = repository;
    this.productRepository = productRepository;
    this.productApi = productApi;
    this.engine = engine;
    this.activityRecorder = activityRecorder;
    this.storyApi = storyApi;
  }

  public record PlanCreateRequest(Long branchId, Long parentId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title, LocalDate beginDate, LocalDate endDate,
      String description, Map<String, Object> customFields) {}

  public record PlanUpdateRequest(String title, Long branchId, Long parentId, LocalDate beginDate, LocalDate endDate,
      String description, Map<String, Object> customFields,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  public record PlanCloseRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"cancel", "done"}) String closedReason,
      String comment) {}

  @Transactional
  public PlanView create(SessionPrincipal actor, long productId, PlanCreateRequest command) {
    ProductGuard.requireVisible(productRepository, productApi, actor, productId);
    if (command.title() == null || command.title().trim().isEmpty()) {
      throw ApiException.validation(Map.of("title", "required"));
    }
    if (command.title().trim().length() > 90) {
      throw ApiException.validation(Map.of("title", "maxLength"));
    }
    requireDateRange(command.beginDate(), command.endDate());
    long parentId = requireParent(productId, command.parentId(), 0);
    fieldDefValidator.validate("plan", command.customFields() == null ? Map.of() : command.customFields(), true);
    Instant now = Instant.now();
    Plan plan = repository.insert(new Plan(0, productId, command.branchId() == null ? 0 : command.branchId(),
        parentId, command.title().trim(), "wait", command.description(), command.beginDate(), command.endDate(),
        null, null, null, command.customFields(), actor.account(), now, null, null, 0));
    activityRecorder.record(actor.account(), "plan", plan.id(), "created", null, null);
    return PlanView.of(plan);
  }

  @Transactional
  public PlanView update(SessionPrincipal actor, long planId, PlanUpdateRequest command) {
    Plan plan = require(actor, planId);
    if (command.lockVersion() == null || command.lockVersion() != plan.lockVersion()) {
      throw ApiException.lockConflict();
    }
    if (command.title() != null) {
      if (command.title().trim().isEmpty()) {
        throw ApiException.validation(Map.of("title", "required"));
      }
      if (command.title().trim().length() > 90) {
        throw ApiException.validation(Map.of("title", "maxLength"));
      }
    }
    LocalDate begin = command.beginDate() == null ? plan.beginDate() : command.beginDate();
    LocalDate end = command.endDate() == null ? plan.endDate() : command.endDate();
    requireDateRange(begin, end);
    long parentId = requireParent(plan.productId(), command.parentId(), planId);
    fieldDefValidator.validate("plan", command.customFields() == null ? java.util.Map.of() : command.customFields(), false);
    plan.update(command.title() == null ? null : command.title().trim(), command.branchId(),
        command.parentId() == null ? null : parentId, command.beginDate(), command.endDate(), command.description(),
        command.customFields());
    plan.markUpdatedBy(actor.account());
    return PlanView.of(save(plan));
  }

  @Transactional
  public PlanView start(SessionPrincipal actor, long planId) {
    return fire(actor, planId, "start", null, null);
  }

  @Transactional
  public PlanView finish(SessionPrincipal actor, long planId, String comment) {
    return fire(actor, planId, "finish", comment, null);
  }

  @Transactional
  public PlanView close(SessionPrincipal actor, long planId, PlanCloseRequest command) {
    if (command == null || command.closedReason() == null || !CLOSE_REASONS.contains(command.closedReason())) {
      throw ApiException.validation(Map.of("closedReason", "invalid"));
    }
    return fire(actor, planId, "close", command.comment(), command.closedReason());
  }

  @Transactional
  public PlanView activate(SessionPrincipal actor, long planId, String comment) {
    return fire(actor, planId, "activate", comment, null);
  }

  /**
   * DELETE（§5，A-07）：软删；存在未删需求 planId 指向本计划 → 42203（经 StoryApi）。
   * 子计划脱离父子关系（§4.3 子计划全删语义，detachChildren）。
   */
  @Transactional
  public void delete(SessionPrincipal actor, long planId) {
    require(actor, planId);
    if (storyApi.hasActiveStoriesByPlan(planId)) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "plan.guard.hasStories");
    }
    repository.softDelete(planId);
    repository.detachChildren(planId);
  }

  /** 动作 + 父计划聚合（§4.3）：请求体驱动字段（closedReason）在 fire 前落对象，供 YAML 守卫分派。 */
  private PlanView fire(SessionPrincipal actor, long planId, String action, String comment, String closedReason) {
    Plan plan = require(actor, planId);
    if (closedReason != null) {
      plan.markClosedReason(closedReason);
    }
    engine.fire(new WorkflowTargets.PlanTarget(plan, actor.account()), action, comment);
    plan.markUpdatedBy(actor.account());
    Plan saved = save(plan);
    rollupParent(actor, saved);
    return PlanView.of(saved);
  }

  /** 子计划状态变更后重算父计划（纯函数判定，只在状态真变时落库与记动态流）。 */
  void rollupParent(SessionPrincipal actor, Plan child) {
    if (child.parentId() == 0) {
      return;
    }
    Plan parent = repository.findActiveById(child.parentId()).orElse(null);
    if (parent == null) {
      repository.detachChildren(child.parentId());
      return;
    }
    List<Plan> children = repository.findChildren(parent.id());
    PlanStatusRollup.compute(children)
        .filter(rollup -> PlanStatusRollup.changes(parent.status(), rollup))
        .ifPresent(rollup -> {
          parent.applyStatus(rollup.status());
          parent.markUpdatedBy(actor.account());
          repository.update(parent);
          if (rollup.action() != null) {
            activityRecorder.record(actor.account(), "plan", parent.id(), rollup.action(), null, null);
          }
        });
  }

  Plan require(SessionPrincipal actor, long planId) {
    Plan plan = repository.findActiveById(planId).orElseThrow(() -> ApiException.notFound("entity.plan"));
    ProductGuard.requireVisible(productRepository, productApi, actor, plan.productId());
    return plan;
  }

  Plan save(Plan plan) {
    return repository.update(plan).orElseThrow(() -> ApiException.lockConflict());
  }

  /** 跨字段规则，不注解化。 */
  private static void requireDateRange(LocalDate begin, LocalDate end) {
    if (begin != null && end != null && end.isBefore(begin)) {
      throw ApiException.validation(Map.of("endDate", "invalidRange"));
    }
  }

  /** 父子仅两级（§3.4）：父计划 parentId 恒 0，子计划的父必须是一级计划，且不能自成父。 */
  private long requireParent(long productId, Long parentId, long selfId) {
    if (parentId == null || parentId == 0) {
      return 0;
    }
    if (parentId == selfId) {
      throw ApiException.validation(Map.of("parentId", "invalid"));
    }
    Plan parent = repository.findActiveById(parentId)
        .orElseThrow(() -> ApiException.validation(Map.of("parentId", "notFound")));
    if (parent.productId() != productId) {
      throw ApiException.validation(Map.of("parentId", "crossProduct"));
    }
    if (parent.parentId() != 0) {
      throw ApiException.validation(Map.of("parentId", "nestedNotAllowed"));
    }
    return parentId;
  }
}
