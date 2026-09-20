package net.zentao.doc.api;

import net.zentao.doc.domain.DocCategory;

/** 库内目录节点视图（contract：DocCategoryView）。 */
public record DocCategoryView(long id, long docSpaceId, long parentId, String name, int sort) {

  public static DocCategoryView of(DocCategory category) {
    return new DocCategoryView(category.id(), category.docSpaceId(), category.parentId(), category.name(),
        category.sort());
  }
}
