package net.zentao.product.domain;

import java.util.List;
import java.util.Optional;

/** 构建仓储（domain 接口，infra 实现）。 */
public interface BuildRepository {

  Optional<Build> findActiveById(long id);

  List<Build> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  Build insert(Build build);

  Optional<Build> update(Build build);

  Optional<Build> softDelete(long id);

  /** 产品删除守卫（A-07）：该产品下是否存在未删构建。 */
  boolean existsActiveByProduct(long productId);
}
