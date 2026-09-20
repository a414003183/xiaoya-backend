package net.zentao.product.app;

import java.util.ArrayList;
import java.util.List;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.LinkRequest;
import net.zentao.product.api.ReleaseView;
import net.zentao.product.domain.Release;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发布关联/解除（product 卡 §4.4）：link/unlink 幂等，objectType ∈ story|bug 由 YAML 守卫兜底，
 * 关联存 storyIds/bugIds json 数组；同产品校验经 ReleaseHandlers 的跨域守卫。
 */
@Component
public class ReleaseLinkHandler {

  private final ReleaseHandlers releaseHandlers;
  private final WorkflowEngine engine;

  public ReleaseLinkHandler(ReleaseHandlers releaseHandlers, WorkflowEngine engine) {
    this.releaseHandlers = releaseHandlers;
    this.engine = engine;
  }

  @Transactional
  public ReleaseView link(SessionPrincipal actor, long releaseId, LinkRequest request) {
    return fire(actor, releaseId, request, true);
  }

  @Transactional
  public ReleaseView unlink(SessionPrincipal actor, long releaseId, LinkRequest request) {
    return fire(actor, releaseId, request, false);
  }

  private ReleaseView fire(SessionPrincipal actor, long releaseId, LinkRequest request, boolean link) {
    Release release = releaseHandlers.require(actor, releaseId);
    List<Long> ids = request.ids() == null ? List.of() : request.ids().stream().distinct().toList();
    release.linkRequest(request.objectType(), ids);
    engine.fire(new WorkflowTargets.ReleaseTarget(release, actor.account()), link ? "link" : "unlink", null);
    if ("story".equals(request.objectType())) {
      releaseHandlers.requireStoriesInProduct(release.productId(), ids);
      release.replaceStoryIds(merge(release.storyIds(), ids, link));
    } else {
      releaseHandlers.requireBugsInProduct(release.productId(), ids);
      release.replaceBugIds(merge(release.bugIds(), ids, link));
    }
    release.markUpdatedBy(actor.account());
    return ReleaseView.of(releaseHandlers.save(release));
  }

  static List<Long> merge(List<Long> current, List<Long> ids, boolean link) {
    List<Long> merged = new ArrayList<>(current);
    if (link) {
      for (Long id : ids) {
        if (!merged.contains(id)) {
          merged.add(id);
        }
      }
    } else {
      merged.removeAll(ids);
    }
    return merged;
  }
}
