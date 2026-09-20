package net.zentao.product.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import net.zentao.product.domain.Release;

/** 发布视图（contract：ReleaseView；product 卡 §3.5 读侧字段）。 */
public record ReleaseView(
    long id,
    long productId,
    long branchId,
    Long buildId,
    long projectId,
    String name,
    @Schema(allowableValues = {"normal", "terminated"}) String status,
    LocalDate releaseDate,
    Instant publishedAt,
    boolean isMilestone,
    List<Long> storyIds,
    List<Long> bugIds,
    List<String> notifyAccounts,
    String description,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    int lockVersion) {

  public static ReleaseView of(Release release) {
    return new ReleaseView(release.id(), release.productId(), release.branchId(), release.buildId(),
        release.projectId(), release.name(), release.status(), release.releaseDate(), release.publishedAt(),
        release.isMilestone(), release.storyIds(), release.bugIds(), release.notifyAccounts(), release.description(),
        release.createdBy(), release.createdAt(), release.updatedBy(), release.updatedAt(), release.lockVersion());
  }
}
