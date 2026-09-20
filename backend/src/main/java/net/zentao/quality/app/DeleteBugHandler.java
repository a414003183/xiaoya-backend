package net.zentao.quality.app;

import java.time.Instant;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.domain.Bug;
import net.zentao.quality.domain.BugRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 软删 Bug（A-07，quality 卡 §5 deleteBug）：叶子对象直接软删；删后详情 40401。 */
@Component
public class DeleteBugHandler {

  private final BugRepository repository;
  private final ProductApi productApi;
  private final ActivityRecorder activityRecorder;

  public DeleteBugHandler(BugRepository repository, ProductApi productApi, ActivityRecorder activityRecorder) {
    this.repository = repository;
    this.productApi = productApi;
    this.activityRecorder = activityRecorder;
  }

  @Transactional
  public void handle(SessionPrincipal actor, long bugId) {
    Bug bug = BugActionSupport.require(actor, repository, productApi, bugId);
    repository.softDelete(bug.id(), actor.account(), Instant.now());
    activityRecorder.record(actor.account(), "bug", bug.id(), "deleted", null, null);
  }
}
