package net.zentao.product.domain;

import java.util.List;
import java.util.Optional;

/** 分支仓储（domain 接口，infra 实现）。 */
public interface BranchRepository {

  Optional<Branch> findActiveById(long id);

  List<Branch> findByProductId(long productId);

  List<Branch> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  Branch insert(Branch branch);

  Optional<Branch> update(Branch branch);

  boolean existsByNameInProduct(long productId, String name, long excludeId);

  /** set-default 排他：同产品其余分支置 isDefault=false。 */
  int clearDefaultExcept(long productId, long keepId);

  /** 默认分支（无默认则取第一条激活分支；无分支返回空）。 */
  Optional<Branch> findDefault(long productId);

  /** 软删（deleted_at 置位，A-07）。 */
  void softDelete(long id);
}
