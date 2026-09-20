package net.zentao.requirement.domain;

import java.util.List;
import java.util.Optional;

/** 需求仓储（domain 接口，infra 实现；查询以 Object 承载条件包装器，避免 ORM 类型泄漏进 domain）。 */
public interface StoryRepository {

  Optional<Story> findActiveById(long id);

  List<Story> findActiveByIds(List<Long> ids);

  /** 产品下全部未删需求（需求统计报表取数，workspace 卡 §5）。 */
  List<Story> findActiveByProduct(long productId);

  List<Story> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  Story insert(Story story);

  Optional<Story> update(Story story);

  /** 计划关联批量写（product §4.3 link/unlink；null = 解除关联）。 */
  int updatePlanId(List<Long> ids, Long planId);

  /** 删除守卫（A-07）：product/branch/plan 删除前 42203 判定。 */
  boolean existsActiveByProduct(long productId);

  boolean existsActiveByBranch(long branchId);

  boolean existsActiveByPlan(long planId);

  /** 删除守卫（A-07）：存在未删子需求 parentId 指向本需求（story 删除前 42203 判定）。 */
  boolean existsActiveByParent(long parentId);

  /** 软删（deleted_at 置位；邻例 BuildRepository.softDelete）。 */
  void softDelete(long id);
}
