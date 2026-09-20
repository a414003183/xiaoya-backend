package net.zentao.product.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import net.zentao.product.domain.Branch;

/** 分支视图（contract：BranchView；product 卡 §3.2 读侧字段）。 */
public record BranchView(
    long id,
    long productId,
    String name,
    boolean isDefault,
    @Schema(allowableValues = {"active", "closed"}) String status,
    String description,
    int sort,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    Instant closedAt,
    int lockVersion) {

  public static BranchView of(Branch branch) {
    return new BranchView(branch.id(), branch.productId(), branch.name(), branch.isDefault(), branch.status(),
        branch.description(), branch.sort(), branch.createdBy(), branch.createdAt(), branch.updatedBy(),
        branch.updatedAt(), branch.closedAt(), branch.lockVersion());
  }
}
