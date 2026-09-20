package net.zentao.doc.domain;

import java.util.List;
import java.util.Optional;

/** 库内目录仓储（doc 卡 §2：真实删除）。 */
public interface DocCategoryRepository {

  Optional<DocCategory> findById(long id);

  /** 库内目录全集（树由查询层组装）。 */
  List<DocCategory> findBySpace(long docSpaceId);

  DocCategory insert(DocCategory category);

  Optional<DocCategory> update(DocCategory category);

  void delete(long id);

  /** 直接子节点数（删除守卫 42203）。 */
  long countChildren(long parentId);
}
