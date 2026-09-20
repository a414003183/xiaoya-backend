package net.zentao.requirement.infra;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.requirement.api.StoryApi;
import net.zentao.requirement.api.StoryList;
import net.zentao.requirement.api.StoryView;
import net.zentao.requirement.app.AdvanceStoryStageHandler;
import net.zentao.requirement.app.CreateStoryHandler;
import net.zentao.requirement.app.StoryActionDispatcher;
import net.zentao.requirement.app.StoryLinkHandler;
import net.zentao.requirement.app.StoryQueryService;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.stereotype.Component;

/** 需求域对外接口实现（requirement 卡 §5；事务在 app 层，本类只做装配与转发）。 */
@Component
public class StoryApiImpl implements StoryApi {

  private final StoryRepository repository;
  private final StoryQueryService queryService;
  private final StoryLinkHandler linkHandler;
  private final AdvanceStoryStageHandler stageHandler;
  private final StoryActionDispatcher actionDispatcher;
  private final CreateStoryHandler createHandler;

  public StoryApiImpl(StoryRepository repository, StoryQueryService queryService, StoryLinkHandler linkHandler,
      AdvanceStoryStageHandler stageHandler, StoryActionDispatcher actionDispatcher,
      CreateStoryHandler createHandler) {
    this.repository = repository;
    this.queryService = queryService;
    this.linkHandler = linkHandler;
    this.stageHandler = stageHandler;
    this.actionDispatcher = actionDispatcher;
    this.createHandler = createHandler;
  }

  @Override
  public List<StoryView> findByIds(long productId, List<Long> ids) {
    return repository.findActiveByIds(ids.stream().distinct().toList()).stream()
        .filter(story -> story.productId() == productId)
        .map(StoryView::of)
        .toList();
  }

  @Override
  public Optional<StoryView> findById(long storyId) {
    return repository.findActiveById(storyId).map(StoryView::of);
  }

  @Override
  public List<StoryView> findByIds(List<Long> ids) {
    return repository.findActiveByIds(ids.stream().distinct().toList()).stream().map(StoryView::of).toList();
  }

  @Override
  public StorySummaryCounts summaryCounts(long productId, SessionPrincipal principal) {
    return queryService.summaryCounts(productId, principal);
  }

  @Override
  public StoryList pageByRole(SessionPrincipal principal, String roleField, Map<String, String[]> params) {
    // requirement 卡无 reviewer 单列字段，评审人是 reviewers 列表 → 列表包含匹配（workspace §3.4 的 reviewer 角色）
    String field = "reviewedBy".equals(roleField) ? "reviewers" : roleField;
    return queryService.pageAll(principal, net.zentao.platform.filters.Filters.withFilter(params, field, "@me"));
  }

  @Override
  public void linkPlan(List<Long> ids, long planId) {
    linkHandler.linkPlan(ids, planId);
  }

  @Override
  public void unlinkPlan(List<Long> ids, long planId) {
    linkHandler.unlinkPlan(ids, planId);
  }

  @Override
  public void markReleased(List<Long> ids, String actor) {
    linkHandler.markReleased(ids, actor);
  }

  @Override
  public void advanceStage(long storyId, TaskProgress progress) {
    stageHandler.advance(storyId, progress);
  }

  @Override
  public StoryView fireAction(SessionPrincipal actor, long storyId, String action, String comment) {
    return actionDispatcher.fire(actor, storyId, action, comment);
  }

  @Override
  public StoryList pageByPlan(long planId, SessionPrincipal principal, Map<String, String[]> params) {
    return queryService.pageByPlan(planId, principal, params);
  }

  @Override
  public StoryList pageByIds(List<Long> ids, SessionPrincipal principal, Map<String, String[]> params) {
    return queryService.pageByIds(ids, principal, params);
  }

  @Override
  public boolean hasActiveStoriesByProduct(long productId) {
    return repository.existsActiveByProduct(productId);
  }

  @Override
  public boolean hasActiveStoriesByBranch(long branchId) {
    return repository.existsActiveByBranch(branchId);
  }

  @Override
  public boolean hasActiveStoriesByPlan(long planId) {
    return repository.existsActiveByPlan(planId);
  }

  @Override
  public StoryView createFromBug(SessionPrincipal actor, BugSource source) {
    return createHandler.createFromBug(actor, source);
  }
}
