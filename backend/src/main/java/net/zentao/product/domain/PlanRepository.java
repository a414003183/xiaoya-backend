package net.zentao.product.domain;

import java.util.List;
import java.util.Optional;

/** 计划仓储（domain 接口，infra 实现）。 */
public interface PlanRepository {

  Optional<Plan> findActiveById(long id);

  List<Plan> findActiveByIds(List<Long> ids);

  /** 父计划状态聚合取数（直接子计划，未删除）。 */
  List<Plan> findChildren(long parentId);

  List<Plan> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  Plan insert(Plan plan);

  Optional<Plan> update(Plan plan);

  /** 子计划脱离父子（父被删或父计划清理时使用）。 */
  int detachChildren(long parentId);

  /** 产品删除守卫（A-07）：该产品下是否存在未删计划。 */
  boolean existsActiveByProduct(long productId);

  /** 软删（deleted_at 置位，A-07）。 */
  void softDelete(long id);
}
