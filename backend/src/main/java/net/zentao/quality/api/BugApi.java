package net.zentao.quality.api;

import java.util.List;
import java.util.Map;
import net.zentao.platform.session.SessionPrincipal;

/** Bug 域对外接口（A2：product 域经此读写 Bug 关联；P4 真实现见 quality/infra/BugApiImpl）。 */
public interface BugApi {

  /** 计划关联 Bug 列表（product §5 GET /plans/{planId}/bugs）。 */
  BugList pageByPlan(long planId, SessionPrincipal principal, Map<String, String[]> params);

  /** 按 id 集合列出（发布/构建关联 Bug 列表）。 */
  BugList pageByIds(List<Long> ids, SessionPrincipal principal, Map<String, String[]> params);

  /** 同产品校验（link 守卫）：不存在/已删/跨产品 → 42201。 */
  void requireInProduct(long productId, List<Long> ids);

  /** 标题联查（workspace 卡 §3.1 objectTitle 现算）：id → 标题，缺失/已删的 id 不出现在结果中。 */
  Map<Long, String> titlesByIds(List<Long> ids);

  /** 按角色字段分页（workspace 卡 §3.4 /my/bugs）：等价于本域列表加 {@code filters[<roleField>]=@me}。 */
  BugList pageByRole(SessionPrincipal principal, String roleField, Map<String, String[]> params);

  /** Bug 分布计数（workspace 卡 §5 BugDistributionReport 取数；产品不可见 → 40302）。 */
  record BugDistributionCounts(
      long total,
      Map<Integer, Long> bySeverity,
      Map<String, Long> byStatus,
      Map<String, Long> byResolution) {}

  BugDistributionCounts distributionCounts(long productId, SessionPrincipal principal);

  /** 未解决 Bug 数（org 卡 §5 Personnel 节：status=active 且 assignee=本人，按账号计数）。 */
  Map<String, Long> unresolvedCounts(List<String> accounts);

  /** 计划关联批量落 bug.planId（product §4.3；不产生动态流，动态流由计划侧 link 落）。 */
  void linkPlan(List<Long> ids, long planId);

  void unlinkPlan(List<Long> ids, long planId);
}
