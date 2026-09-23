package net.zentao.product.app;

import java.util.List;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.BuildView;
import net.zentao.product.api.LinkRequest;
import net.zentao.product.domain.Build;
import net.zentao.quality.api.BugApi;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 构建关联/解除（product 卡 §5 builds 族 link/unlink）：幂等，objectType ∈ story|bug，同产品对象。 */
@Component
public class BuildLinkHandler {

  private final BuildHandlers buildHandlers;
  private final BugApi bugApi;

  public BuildLinkHandler(BuildHandlers buildHandlers, BugApi bugApi) {
    this.buildHandlers = buildHandlers;
    this.bugApi = bugApi;
  }

  @Transactional
  public BuildView link(SessionPrincipal actor, long buildId, LinkRequest request) {
    return fire(actor, buildId, request, true);
  }

  @Transactional
  public BuildView unlink(SessionPrincipal actor, long buildId, LinkRequest request) {
    return fire(actor, buildId, request, false);
  }

  private BuildView fire(SessionPrincipal actor, long buildId, LinkRequest request, boolean link) {
    if (request.objectType() == null
        || !(List.of("story", "bug").contains(request.objectType()))
        || request.ids() == null
        || request.ids().isEmpty()) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "build.guard.linkObjectInvalid");
    }
    Build build = buildHandlers.require(actor, buildId);
    List<Long> ids = request.ids().stream().distinct().toList();
    if ("story".equals(request.objectType())) {
      buildHandlers.requireStoriesInProduct(build.productId(), ids);
      build.replaceStoryIds(ReleaseLinkHandler.merge(build.storyIds(), ids, link));
    } else {
      bugApi.requireInProduct(build.productId(), ids);
      build.replaceBugIds(ReleaseLinkHandler.merge(build.bugIds(), ids, link));
    }
    build.markUpdatedBy(actor.account());
    return BuildView.of(buildHandlers.save(build));
  }
}
