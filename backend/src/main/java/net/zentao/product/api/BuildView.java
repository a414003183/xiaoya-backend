package net.zentao.product.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import net.zentao.product.domain.Build;

/** 构建视图（contract：BuildView；product 卡 §3.6 读侧字段）。 */
public record BuildView(
    long id,
    long productId,
    long branchId,
    long executionId,
    long projectId,
    String name,
    String scmPath,
    String filePath,
    LocalDate buildDate,
    String builder,
    List<Long> storyIds,
    List<Long> bugIds,
    String description,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    int lockVersion) {

  public static BuildView of(Build build) {
    return new BuildView(build.id(), build.productId(), build.branchId(), build.executionId(), build.projectId(),
        build.name(), build.scmPath(), build.filePath(), build.buildDate(), build.builder(), build.storyIds(),
        build.bugIds(), build.description(), build.createdBy(), build.createdAt(), build.updatedBy(),
        build.updatedAt(), build.lockVersion());
  }
}
