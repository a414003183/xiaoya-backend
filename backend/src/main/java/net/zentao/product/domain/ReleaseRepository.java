package net.zentao.product.domain;

import java.util.List;
import java.util.Optional;

/** 发布仓储（domain 接口，infra 实现）。 */
public interface ReleaseRepository {

  Optional<Release> findActiveById(long id);

  List<Release> queryPage(Object whereWrapper, int offset, int limit);

  long countByQuery(Object whereWrapper);

  Release insert(Release release);

  Optional<Release> update(Release release);

  /** 构建是否被任一发布引用（Build DELETE 守卫，product §4.5）。 */
  boolean existsByBuildId(long buildId);

  /** 产品删除守卫（A-07）：该产品下是否存在未删发布。 */
  boolean existsActiveByProduct(long productId);

  /** 软删（deleted_at 置位，A-07）。 */
  void softDelete(long id);
}
