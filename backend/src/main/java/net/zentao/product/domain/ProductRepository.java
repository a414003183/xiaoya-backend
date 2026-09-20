package net.zentao.product.domain;

import java.util.List;
import java.util.Optional;

/**
 * 产品仓储（domain 接口，infra 实现）。
 * 查询以 {@code Object} 承载条件包装器，避免 ORM 类型泄漏进 domain（镜像 org 卡约定）。
 */
public interface ProductRepository {

  Optional<Product> findActiveById(long id);

  /** 未删除产品全量（ACL 可见集判定用；产品为小集合）。 */
  List<Product> findAllActive();

  /** 批量取（跨域校验用），只返回未删除。 */
  List<Product> findActiveByIds(List<Long> ids);

  List<Product> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  Product insert(Product product);

  Optional<Product> update(Product product);

  /** 软删（deleted_at 置位，A-07）。 */
  void softDelete(long id);
}
