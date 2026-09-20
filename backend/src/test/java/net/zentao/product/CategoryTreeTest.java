package net.zentao.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import net.zentao.product.domain.Category;
import net.zentao.product.domain.CategoryTree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T-6 分类树纯函数（product 卡 §3.3）：级联子树收集、深度优先树序、孤儿兜底。 */
class CategoryTreeTest {

  private static Category node(long id, long parentId, int sort, String name) {
    return new Category(id, 1, 0, parentId, "story", name, null, sort, "admin", Instant.now(), null, null, 0);
  }

  @Test
  @DisplayName("selfAndDescendants：含自身与全部层级子孙，不含兄弟")
  void collectsSubtree() {
    List<Category> all = List.of(
        node(1, 0, 0, "根甲"),
        node(2, 1, 0, "子甲一"),
        node(3, 1, 1, "子甲二"),
        node(4, 2, 0, "孙甲"),
        node(5, 0, 1, "根乙"));
    List<Long> ids = CategoryTree.selfAndDescendants(all, 1).stream().map(Category::id).toList();
    assertEquals(List.of(1L, 2L, 4L, 3L), ids);
  }

  @Test
  @DisplayName("flattenDepthFirst：同层按 sort,id，父先于子")
  void flattensDepthFirst() {
    List<Category> all = List.of(
        node(3, 1, 1, "子二"),
        node(1, 0, 0, "根甲"),
        node(4, 2, 0, "孙"),
        node(2, 1, 0, "子一"),
        node(5, 0, 1, "根乙"));
    List<Long> ids = CategoryTree.flattenDepthFirst(all).stream().map(Category::id).toList();
    assertEquals(List.of(1L, 2L, 4L, 3L, 5L), ids);
  }

  @Test
  @DisplayName("孤儿节点（父缺失）兜底输出，不丢节点")
  void keepsOrphans() {
    List<Category> all = List.of(node(7, 99, 0, "孤儿"));
    List<Long> ids = CategoryTree.flattenDepthFirst(all).stream().map(Category::id).toList();
    assertTrue(ids.contains(7L), ids.toString());
  }
}
