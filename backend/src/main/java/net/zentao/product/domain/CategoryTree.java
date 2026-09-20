package net.zentao.product.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类树纯函数（product 卡 §3.3）：小集合按 parentId 内存建树，不落 path/grade。
 * 服务端只用它做「级联子树」与「树序输出」；前端拿扁平列表自行组树。
 */
public final class CategoryTree {

  private CategoryTree() {}

  /** 含自身的整棵子树（级联软删用）。 */
  public static List<Category> selfAndDescendants(List<Category> all, long rootId) {
    Map<Long, List<Category>> children = childrenOf(all);
    List<Category> collected = new ArrayList<>();
    collect(all, children, rootId, collected);
    return collected;
  }

  /** 深度优先（同层按 sort,id）输出，保证树序稳定。 */
  public static List<Category> flattenDepthFirst(List<Category> all) {
    Map<Long, List<Category>> children = childrenOf(all);
    List<Category> ordered = new ArrayList<>();
    for (Category root : children.getOrDefault(0L, List.of())) {
      collect(all, children, root.id(), ordered);
    }
    // 孤儿节点（父被删或跨产品脏数据）兜底输出，避免响应丢节点
    for (Category node : all) {
      if (!ordered.contains(node)) {
        ordered.add(node);
      }
    }
    return ordered;
  }

  private static void collect(List<Category> all, Map<Long, List<Category>> children, long id,
      List<Category> collected) {
    for (Category node : all) {
      if (node.id() == id && !collected.contains(node)) {
        collected.add(node);
      }
    }
    for (Category child : children.getOrDefault(id, List.of())) {
      collect(all, children, child.id(), collected);
    }
  }

  private static Map<Long, List<Category>> childrenOf(List<Category> all) {
    Map<Long, List<Category>> children = new LinkedHashMap<>();
    for (Category node : all) {
      children.computeIfAbsent(node.parentId(), key -> new ArrayList<>()).add(node);
    }
    for (List<Category> siblings : children.values()) {
      siblings.sort((left, right) -> left.sort() != right.sort()
          ? Integer.compare(left.sort(), right.sort())
          : Long.compare(left.id(), right.id()));
    }
    return children;
  }
}
