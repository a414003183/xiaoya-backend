package net.zentao.quality.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Bug 仓储（queryPage/countByQuery 以 Object 承载 wrapper 防 A1 泄漏 ORM 类型）。 */
public interface BugRepository {

  Optional<Bug> findActiveById(long id);

  List<Bug> findActiveByIds(List<Long> ids);

  /** 产品下全部未删 Bug（Bug 分布报表取数，workspace 卡 §5）。 */
  List<Bug> findActiveByProduct(long productId);

  List<Bug> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  Bug insert(Bug bug);

  /** 全量覆盖（含 null，清空字段必须落库）；乐观锁不符返回空。 */
  Optional<Bug> update(Bug bug);

  /** 批量落 planId（product §4.3 经 BugApi.linkPlan；不产生动态流）。 */
  int updatePlanId(List<Long> ids, Long planId);

  /** 软删单行（deleted_at 置位，A-07；叶子对象无连带行）。 */
  void softDelete(long id, String actor, Instant at);
}
