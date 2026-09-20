package net.zentao.requirement.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.platform.session.SessionPrincipal;

/**
 * 需求域对外接口（A2：product 域经此读写需求侧）。
 * 数据权限由 requirement 域内部按产品可见集注入，调用方只传上下文。
 */
public interface StoryApi {

  /** 同产品校验：返回存在的需求视图（product §4.3 link 的守卫依据；缺失 id 由调用方判 42203）。 */
  List<StoryView> findByIds(long productId, List<Long> ids);

  Optional<StoryView> findById(long storyId);

  /** 按 id 集合列出（不按产品收窄；task 域 storyTitle 展示联查用）。 */
  List<StoryView> findByIds(List<Long> ids);

  /** 计划关联批量落 story.planId（product §4.3；不产生动态流，动态流由计划侧 link 落）。 */
  void linkPlan(List<Long> ids, long planId);

  void unlinkPlan(List<Long> ids, long planId);

  /** 发布创建副作用（product §4.4）：stage → released + 需求侧 linked2release 动态流。 */
  void markReleased(List<Long> ids, String actor);

  /** 计划关联需求列表（product §5 GET /plans/{planId}/stories）。 */
  StoryList pageByPlan(long planId, SessionPrincipal principal, Map<String, String[]> params);

  /** 按 id 集合列出（发布/构建关联需求列表）。 */
  StoryList pageByIds(List<Long> ids, SessionPrincipal principal, Map<String, String[]> params);

  /** 删除守卫（A-07，2026-09-19）：产品下是否存在未删需求（product 删除前 42203 判定）。 */
  boolean hasActiveStoriesByProduct(long productId);

  /** 删除守卫（A-07）：该分支下是否存在未删需求（branch 删除前 42203 判定）。 */
  boolean hasActiveStoriesByBranch(long branchId);

  /** 删除守卫（A-07）：是否存在未删需求 planId 指向本计划（plan 删除前 42203 判定）。 */
  boolean hasActiveStoriesByPlan(long planId);

  /** 按角色字段分页（workspace 卡 §3.4 /my/stories）：等价于本域列表加 {@code filters[<roleField>]=@me}。 */
  StoryList pageByRole(SessionPrincipal principal, String roleField, Map<String, String[]> params);

  /** 产品需求统计计数（workspace 卡 §5 StorySummaryReport 取数；产品不可见 → 40302）。 */
  record StorySummaryCounts(
      long total,
      Map<String, Long> byStatus,
      Map<Integer, Long> byPriority,
      Map<String, Long> byStage,
      Map<String, Long> byType) {}

  StorySummaryCounts summaryCounts(long productId, SessionPrincipal principal);

  /**
   * 任务侧进展信号（task 卡 §4 需求联动）：任务侧事实由 task 域计算，stage 推进规则由本域持有。
   * {@code anyDoing}=存在进行中/暂停任务；{@code allDone}=存在未取消任务且全部已完成/关闭。
   */
  record TaskProgress(boolean anyDoing, boolean allDone) {}

  /** 按任务进展重算需求 stage（released 终态不覆盖，不产生动态流）。 */
  void advanceStage(long storyId, TaskProgress progress);

  /**
   * 按动作名发起需求动作（project 域执行需求看板拖拽）：{@code action} 取 requirement 卡 §4 动作名，
   * 守卫/副作用由本域状态机裁决——非法迁移 42202、守卫不满足 42203、字段缺失 42201 原样抛出。
   * 当前开放 submit-review/pass/reject/change/change-done/close/activate（assign 需 assignee 参数，不在其列）。
   */
  StoryView fireAction(SessionPrincipal actor, long storyId, String action, String comment);

  /**
   * Bug 转需求（quality 卡 §4.1 resolution=tostory 副作用）：source=bug/source/status=active/stage=wait，
   * 落「created」动态流；同事务由调用方（quality 域 ResolveBugHandler）回填 bug.storyId。
   */
  record BugSource(long productId, long branchId, String title, int priority, String steps) {}

  StoryView createFromBug(SessionPrincipal actor, BugSource source);
}
