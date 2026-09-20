package net.zentao.doc.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.doc.api.DocCategoryNode;
import net.zentao.doc.api.DocCategoryTree;
import net.zentao.doc.domain.DocCategory;
import net.zentao.doc.domain.DocCategoryRepository;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/** 库内目录树查询（doc 卡 §5 GET /doc-spaces/{docSpaceId}/categories：嵌套 children）。 */
@Component
public class DocCategoryQueryService {

  private final DocCategoryRepository repository;
  private final DocAccess access;

  public DocCategoryQueryService(DocCategoryRepository repository, DocAccess access) {
    this.repository = repository;
    this.access = access;
  }

  public DocCategoryTree tree(SessionPrincipal principal, long docSpaceId) {
    access.requireSpace(principal, docSpaceId);
    Map<Long, List<DocCategory>> byParent = new LinkedHashMap<>();
    for (DocCategory category : repository.findBySpace(docSpaceId)) {
      byParent.computeIfAbsent(category.parentId(), key -> new ArrayList<>()).add(category);
    }
    return new DocCategoryTree(nodes(0, byParent));
  }

  private List<DocCategoryNode> nodes(long parentId, Map<Long, List<DocCategory>> byParent) {
    return byParent.getOrDefault(parentId, List.of()).stream()
        .map(category -> DocCategoryNode.leaf(category).withChildren(nodes(category.id(), byParent)))
        .toList();
  }
}
