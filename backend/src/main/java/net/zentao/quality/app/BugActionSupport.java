package net.zentao.quality.app;

import java.util.function.Consumer;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.platform.workflow.WorkflowTarget;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.api.BugView;
import net.zentao.quality.domain.Bug;
import net.zentao.quality.domain.BugRepository;

/**
 * Bug 动作公共骨架（各命令处理器共用）：取对象（40401/40302）→ 请求体字段落对象 → fire（守卫+副作用）→ 乐观锁落库。
 * 请求体字段先落对象、YAML 守卫兜底，同 platform 卡 §4.3 约定。
 */
final class BugActionSupport {

  private BugActionSupport() {}

  /** 详情/动作前置：不存在 → 40401；产品不可见 → 40302（quality 卡 §7）。 */
  static Bug require(SessionPrincipal actor, BugRepository repository, ProductApi productApi, long bugId) {
    Bug bug = repository.findActiveById(bugId).orElseThrow(() -> ApiException.notFound("Bug"));
    if (!productApi.canAccess(actor, bug.productId())) {
      throw ApiException.dataForbidden("无权访问该 Bug。");
    }
    return bug;
  }

  static BugView fire(SessionPrincipal actor, BugRepository repository, ProductApi productApi,
      WorkflowEngine engine, long bugId, String action, String comment) {
    return fire(actor, repository, productApi, engine, bugId, action, comment, bug -> {});
  }

  static BugView fire(SessionPrincipal actor, BugRepository repository, ProductApi productApi,
      WorkflowEngine engine, long bugId, String action, String comment, Consumer<Bug> beforeFire) {
    Bug bug = require(actor, repository, productApi, bugId);
    beforeFire.accept(bug);
    engine.fire(new BugTarget(bug, actor.account()), action, comment);
    bug.markUpdatedBy(actor.account());
    return BugView.of(save(repository, bug));
  }

  static Bug save(BugRepository repository, Bug bug) {
    return repository.update(bug).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
  }

  /** workflow 作用对象适配（聚合不实现 platform 接口，actor 由 app 层注入）。 */
  record BugTarget(Bug bug, String actor) implements WorkflowTarget {

    @Override
    public String objectType() {
      return "bug";
    }

    @Override
    public long objectId() {
      return bug.id();
    }

    @Override
    public String status() {
      return bug.status();
    }

    @Override
    public void applyStatus(String status) {
      bug.applyStatus(status);
    }

    @Override
    public Object field(String name) {
      return switch (name) {
        case "title" -> bug.title();
        case "createdBy" -> bug.createdBy();
        case "assignee" -> bug.assignee();
        case "notifyAccounts" -> bug.notifyAccounts();
        case "resolution" -> bug.resolution();
        case "resolvedBuild" -> bug.resolvedBuild();
        case "duplicateOfId" -> bug.duplicateOfId();
        case "openedBuilds" -> bug.openedBuilds();
        case "confirmed" -> bug.confirmed();
        default -> null;
      };
    }

    @Override
    public void setField(String name, Object value) {
      bug.setField(name, value);
    }
  }
}
