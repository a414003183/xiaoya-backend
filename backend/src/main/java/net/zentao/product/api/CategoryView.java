package net.zentao.product.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import net.zentao.product.domain.Category;

/** 分类节点视图（contract：CategoryView；product 卡 §3.3 读侧字段）。 */
public record CategoryView(
    long id,
    long productId,
    long branchId,
    long parentId,
    @Schema(allowableValues = {"bug", "case", "story"}) String type,
    String name,
    String owner,
    int sort,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    int lockVersion) {

  public static CategoryView of(Category category) {
    return new CategoryView(category.id(), category.productId(), category.branchId(), category.parentId(),
        category.type(), category.name(), category.owner(), category.sort(), category.createdBy(),
        category.createdAt(), category.updatedBy(), category.updatedAt(), category.lockVersion());
  }
}
