package net.zentao.doc.api;

import java.util.List;
import net.zentao.doc.domain.DocCategory;

/** 目录树节点（contract：DocCategoryNode；嵌套 children）。 */
public record DocCategoryNode(long id, long docSpaceId, long parentId, String name, int sort,
    List<DocCategoryNode> children) {

  public static DocCategoryNode leaf(DocCategory category) {
    return new DocCategoryNode(category.id(), category.docSpaceId(), category.parentId(), category.name(),
        category.sort(), List.of());
  }

  public DocCategoryNode withChildren(List<DocCategoryNode> nodes) {
    return new DocCategoryNode(id, docSpaceId, parentId, name, sort, List.copyOf(nodes));
  }
}
