package net.zentao.doc.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import net.zentao.doc.domain.Doc;
import net.zentao.doc.domain.DocAcl;

/**
 * 文档视图（contract：DocView；doc 卡 §3.2）。
 * 列表响应不携带 content 与 files（doc.md §5 补充约定），仅详情/快照端点返回；digest 随当前正文。
 */
public record DocView(
    long id,
    long docSpaceId,
    long productId,
    long projectId,
    long executionId,
    long categoryId,
    long parentId,
    String path,
    String title,
    String keywords,
    @Schema(allowableValues = {"html", "markdown"}) String type,
    @Schema(allowableValues = {"draft", "published"}) String status,
    @Schema(allowableValues = {"open", "private"}) String acl,
    DocAcl editors,
    DocAcl readers,
    List<String> notifyAccounts,
    int views,
    int version,
    boolean hasDraft,
    List<Long> files,
    String content,
    String digest,
    int sort,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    int lockVersion) {

  /** 列表视图：正文/附件/摘要不随列表返回（doc.md §5）。 */
  public static DocView summary(Doc doc, boolean hasDraft) {
    return build(doc, hasDraft, null, null, null);
  }

  /** 详情视图：content/files/digest 为当前可见正文（可编辑者见 v0 工作副本，其余见最新发布快照）。 */
  public static DocView detail(Doc doc, boolean hasDraft, String content, List<Long> files, String digest) {
    return build(doc, hasDraft, content, files, digest);
  }

  private static DocView build(Doc doc, boolean hasDraft, String content, List<Long> files, String digest) {
    return new DocView(doc.id(), doc.docSpaceId(), doc.productId(), doc.projectId(), doc.executionId(),
        doc.categoryId(), doc.parentId(), doc.path(), doc.title(), doc.keywords(), doc.type(), doc.status(),
        doc.acl(), doc.editors(), doc.readers(), doc.notifyAccounts(), doc.views(), doc.version(), hasDraft,
        files == null ? null : List.copyOf(files), content, digest, doc.sort(), doc.createdBy(), doc.createdAt(),
        doc.updatedBy(), doc.updatedAt(), doc.lockVersion());
  }
}
