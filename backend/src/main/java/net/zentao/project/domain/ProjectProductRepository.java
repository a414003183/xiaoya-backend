package net.zentao.project.domain;

import java.util.List;

/** 项目↔产品关联仓储（project 卡 §2：project_product 唯一索引，全量替换走 diff）。 */
public interface ProjectProductRepository {

  List<Long> productIds(long projectId);

  /** 多个项目的产品 id 并集（项目集关联产品：其下项目产品聚合，避免逐项目 N+1）。 */
  List<Long> productIdsOf(List<Long> projectIds);

  /** 关联某产品的项目 id 集（项目列表 filters[productId] 反查，B-PRD-01）。 */
  List<Long> projectIdsOfProduct(long productId);

  /** 全量替换（diff：新增缺的、删掉多的）。 */
  void replace(long projectId, List<Long> productIds);
}
