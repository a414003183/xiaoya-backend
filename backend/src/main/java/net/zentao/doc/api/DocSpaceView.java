package net.zentao.doc.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import net.zentao.doc.domain.DocAcl;
import net.zentao.doc.domain.DocSpace;

/** 文档库视图（contract：DocSpaceView；doc 卡 §3.1，docCount 为派生只读）。 */
public record DocSpaceView(
    long id,
    String name,
    @Schema(allowableValues = {"custom", "execution", "mine", "product", "project"}) String type,
    long productId,
    long projectId,
    long executionId,
    @Schema(allowableValues = {"default", "open", "private"}) String acl,
    DocAcl whitelist,
    String description,
    @Schema(allowableValues = {"id_asc", "id_desc"}) String docSort,
    boolean isDefault,
    long docCount,
    int sort,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    int lockVersion) {

  public static DocSpaceView of(DocSpace space, long docCount) {
    return new DocSpaceView(space.id(), space.name(), space.type(), space.productId(), space.projectId(),
        space.executionId(), space.acl(), space.whitelist(), space.description(), space.docSort(), space.isDefault(),
        docCount, space.sort(), space.createdBy(), space.createdAt(), space.updatedBy(), space.updatedAt(),
        space.lockVersion());
  }
}
