package net.zentao.product.app;

import java.util.List;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.LinkRequest;
import net.zentao.product.api.PlanView;
import net.zentao.product.domain.Plan;
import net.zentao.quality.api.BugApi;
import net.zentao.requirement.api.StoryApi;
import net.zentao.requirement.api.StoryView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 计划关联/解除（product 卡 §4.3/§5）：link/unlink 幂等；closed 计划 → 42202（YAML）；
 * objectType ∈ story|bug 与 ids 非空由 YAML 守卫兜底；跨产品对象 → 42203。
 * plan-story 关联落 `story.planId`（§2 无关联表），经 StoryApi；bug 分支经 BugApi（P2 占位）。
 */
@Component
public class PlanLinkHandler {

  private final PlanHandlers planHandlers;
  private final StoryApi storyApi;
  private final BugApi bugApi;
  private final WorkflowEngine engine;

  public PlanLinkHandler(PlanHandlers planHandlers, StoryApi storyApi, BugApi bugApi, WorkflowEngine engine) {
    this.planHandlers = planHandlers;
    this.storyApi = storyApi;
    this.bugApi = bugApi;
    this.engine = engine;
  }

  @Transactional
  public PlanView link(SessionPrincipal actor, long planId, LinkRequest request) {
    return fire(actor, planId, request, true);
  }

  @Transactional
  public PlanView unlink(SessionPrincipal actor, long planId, LinkRequest request) {
    return fire(actor, planId, request, false);
  }

  private PlanView fire(SessionPrincipal actor, long planId, LinkRequest request, boolean link) {
    Plan plan = planHandlers.require(actor, planId);
    List<Long> ids = request.ids() == null ? List.of() : request.ids().stream().distinct().toList();
    plan.linkRequest(request.objectType(), ids);
    engine.fire(new WorkflowTargets.PlanTarget(plan, actor.account()), link ? "link" : "unlink", null);
    applyRelation(plan, request.objectType(), ids, link);
    plan.markUpdatedBy(actor.account());
    return PlanView.of(planHandlers.save(plan));
  }

  private void applyRelation(Plan plan, String objectType, List<Long> ids, boolean link) {
    if ("story".equals(objectType)) {
      List<StoryView> found = storyApi.findByIds(plan.productId(), ids);
      if (found.size() != ids.size()) {
        throw ApiException.guardNotSatisfied("需求不存在或不属于该产品。");
      }
      if (link) {
        storyApi.linkPlan(ids, plan.id());
      } else {
        storyApi.unlinkPlan(ids, plan.id());
      }
      return;
    }
    // bug 分支：关联语义落 bug.planId（quality 卡 §3.1；P4 T-1 起为真实现）
    bugApi.requireInProduct(plan.productId(), ids);
    if (link) {
      bugApi.linkPlan(ids, plan.id());
    } else {
      bugApi.unlinkPlan(ids, plan.id());
    }
  }
}
