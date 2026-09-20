package net.zentao.quality.infra;

import java.util.List;
import java.util.Map;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.quality.api.BugApi;
import net.zentao.quality.api.BugList;
import net.zentao.quality.app.BugQueryService;
import net.zentao.quality.domain.Bug;
import net.zentao.quality.domain.BugRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** BugApi 真实现（P4 T-1，替换 P2 的 EmptyBugApi；product 域消费面不变）。 */
@Component
public class BugApiImpl implements BugApi {

  private final BugQueryService queryService;
  private final BugRepository repository;

  public BugApiImpl(BugQueryService queryService, BugRepository repository) {
    this.queryService = queryService;
    this.repository = repository;
  }

  @Override
  public BugList pageByPlan(long planId, SessionPrincipal principal, Map<String, String[]> params) {
    return queryService.pageByPlan(planId, principal, params);
  }

  @Override
  public BugList pageByIds(List<Long> ids, SessionPrincipal principal, Map<String, String[]> params) {
    return queryService.pageByIds(ids == null ? List.of() : ids, principal, params);
  }

  @Override
  public BugDistributionCounts distributionCounts(long productId, SessionPrincipal principal) {
    return queryService.distributionCounts(productId, principal);
  }

  @Override
  public BugList pageByRole(SessionPrincipal principal, String roleField, Map<String, String[]> params) {
    return queryService.pageAll(principal, net.zentao.platform.filters.Filters.withFilter(params, roleField, "@me"));
  }

  @Override
  public Map<String, Long> unresolvedCounts(List<String> accounts) {
    if (accounts == null || accounts.isEmpty()) {
      return Map.of();
    }
    Map<String, Long> counts = new java.util.LinkedHashMap<>();
    var condition = new com.mybatisflex.core.query.QueryColumn("deleted_at").isNull()
        .and(new com.mybatisflex.core.query.QueryColumn("status").eq("active"))
        .and(new com.mybatisflex.core.query.QueryColumn("assignee")
            .in(new java.util.ArrayList<Object>(accounts)));
    // ponytail: 账号集有界，内存聚合；升级路径 = SQL GROUP BY assignee
    for (Bug bug : repository.queryPage(com.mybatisflex.core.query.QueryWrapper.create().where(condition), 0,
        10_000)) {
      counts.merge(bug.assignee(), 1L, Long::sum);
    }
    return counts;
  }

  @Override
  public Map<Long, String> titlesByIds(List<Long> ids) {    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    Map<Long, String> titles = new java.util.LinkedHashMap<>();
    for (Bug bug : repository.findActiveByIds(ids.stream().distinct().toList())) {
      titles.put(bug.id(), bug.title());
    }
    return titles;
  }

  @Override
  public void requireInProduct(long productId, List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return;
    }
    List<Long> distinct = ids.stream().distinct().toList();
    List<Bug> found = repository.findActiveByIds(distinct);
    if (found.size() != distinct.size() || found.stream().anyMatch(bug -> bug.productId() != productId)) {
      // 与 story 分支同语义（product §4.3 link 守卫）：缺失/跨产品由守卫判 42203
      throw net.zentao.platform.error.ApiException.guardNotSatisfied("Bug 不存在或不属于该产品。");
    }
  }

  @Override
  @Transactional
  public void linkPlan(List<Long> ids, long planId) {
    repository.updatePlanId(ids, planId);
  }
  @Override
  @Transactional
  public void unlinkPlan(List<Long> ids, long planId) {
    // ponytail: 同 unlink 语义=置空 plan_id；全量扫描仅在传入 id 集内，升级路径=按 plan_id 批量清列
    repository.updatePlanId(ids, null);
  }
}
