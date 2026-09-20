package net.zentao.doc.api;

import java.time.Instant;
import java.util.List;
import net.zentao.doc.domain.DocVersion;

/** 正文快照视图（contract：DocVersionView；doc 卡 §3.3）。 */
public record DocVersionView(
    long id,
    long docId,
    int version,
    String title,
    String content,
    String digest,
    List<Long> files,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt) {

  public static DocVersionView of(DocVersion version) {
    return new DocVersionView(version.id(), version.docId(), version.version(), version.title(), version.content(),
        version.digest(), version.files(), version.createdBy(), version.createdAt(), version.updatedBy(),
        version.updatedAt());
  }
}
