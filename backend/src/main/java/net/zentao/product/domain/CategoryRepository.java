package net.zentao.product.domain;

import java.util.List;
import java.util.Optional;

/** 分类仓储（domain 接口，infra 实现）。 */
public interface CategoryRepository {

  Optional<Category> findActiveById(long id);

  /** 同产品同 type 全量节点（树响应不分页）。 */
  List<Category> findByProductAndType(long productId, String type);

  List<Category> queryList(Object whereWrapper);

  Category insert(Category category);

  Optional<Category> update(Category category);

  /** 级联软删子树。 */
  int softDeleteAll(List<Long> ids);
}
