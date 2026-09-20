package net.zentao.requirement.app;

import java.util.function.Consumer;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.platform.workflow.WorkflowTarget;
import net.zentao.product.api.ProductApi;
import net.zentao.requirement.api.StoryView;
import net.zentao.requirement.domain.Story;
import net.zentao.requirement.domain.StoryRepository;

/**
 * 需求动作公共骨架（各命令处理器共用）：取对象（40401/40302）→ 请求体字段落对象 → fire（守卫+副作用）→ 乐观锁落库。
 * 请求体字段先落对象、YAML 守卫兜底，同 platform 卡 §4.3 约定。
 */
final class StoryActionSupport {

  private StoryActionSupport() {}

  /** 详情/动作前置：不存在 → 40401；产品不可见 → 40302（requirement 卡 §7）。 */
  static Story require(SessionPrincipal actor, StoryRepository repository, ProductApi productApi, long storyId) {
    Story story = repository.findActiveById(storyId).orElseThrow(() -> ApiException.notFound("需求"));
    if (!productApi.canAccess(actor, story.productId())) {
      throw ApiException.dataForbidden("无权访问该需求。");
    }
    return story;
  }

  static StoryView fire(SessionPrincipal actor, StoryRepository repository, ProductApi productApi,
      WorkflowEngine engine, long storyId, String action, String comment) {
    return fire(actor, repository, productApi, engine, storyId, action, comment, story -> {});
  }

  static StoryView fire(SessionPrincipal actor, StoryRepository repository, ProductApi productApi,
      WorkflowEngine engine, long storyId, String action, String comment, Consumer<Story> beforeFire) {
    Story story = require(actor, repository, productApi, storyId);
    beforeFire.accept(story);
    engine.fire(new StoryTarget(story, actor.account()), action, comment);
    story.markUpdatedBy(actor.account());
    return StoryView.of(save(repository, story));
  }

  static Story save(StoryRepository repository, Story story) {
    return repository.update(story).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
  }

  /** workflow 作用对象适配（聚合不实现 platform 接口，actor 由 app 层注入）。 */
  record StoryTarget(Story story, String actor) implements WorkflowTarget {

    @Override
    public String objectType() {
      return "story";
    }

    @Override
    public long objectId() {
      return story.id();
    }

    @Override
    public String status() {
      return story.status();
    }

    @Override
    public void applyStatus(String status) {
      story.applyStatus(status);
    }

    @Override
    public Object field(String name) {
      return switch (name) {
        case "title" -> story.title();
        case "createdBy" -> story.createdBy();
        case "assignee" -> story.assignee();
        case "reviewers" -> story.reviewers();
        case "notifyAccounts" -> story.notifyAccounts();
        case "needNotReview" -> story.needNotReview();
        case "closedReason" -> story.closedReason();
        case "duplicateOfId" -> story.duplicateOfId();
        default -> null;
      };
    }

    @Override
    public void setField(String name, Object value) {
      story.setField(name, value);
    }
  }
}
