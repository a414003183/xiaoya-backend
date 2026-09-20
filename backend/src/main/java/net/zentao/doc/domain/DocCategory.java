package net.zentao.doc.domain;

import java.time.Instant;

/** 库内目录节点（doc 卡 §2 doc_category；真实删除，无软删语义）。 */
public class DocCategory {

  private final long id;
  private final long docSpaceId;
  private long parentId;
  private String name;
  private int sort;
  private final String createdBy;
  private final Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private final int lockVersion;

  public DocCategory(long id, long docSpaceId, long parentId, String name, int sort, String createdBy,
      Instant createdAt, String updatedBy, Instant updatedAt, int lockVersion) {
    this.id = id;
    this.docSpaceId = docSpaceId;
    this.parentId = parentId;
    this.name = name;
    this.sort = sort;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** 改名/移动/排序（doc 卡 §5 PATCH /doc-spaces/{id}/categories/{categoryId}）；null = 不修改。 */
  public void update(String name, Long parentId, Integer sort) {
    if (name != null) {
      this.name = name;
    }
    if (parentId != null) {
      this.parentId = parentId;
    }
    if (sort != null) {
      this.sort = sort;
    }
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  public long id() {
    return id;
  }

  public long docSpaceId() {
    return docSpaceId;
  }

  public long parentId() {
    return parentId;
  }

  public String name() {
    return name;
  }

  public int sort() {
    return sort;
  }

  public String createdBy() {
    return createdBy;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public String updatedBy() {
    return updatedBy;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public int lockVersion() {
    return lockVersion;
  }
}
