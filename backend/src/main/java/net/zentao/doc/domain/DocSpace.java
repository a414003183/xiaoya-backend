package net.zentao.doc.domain;

import java.time.Instant;

/**
 * 文档库聚合根（doc 卡 §3.1；纯 Java，A1）。
 * 归属列按 type 取一（productId/projectId/executionId），type 创建后不可改；mine 库恒 private。
 */
public class DocSpace {

  private final long id;
  private String name;
  private final String type;
  private long productId;
  private long projectId;
  private long executionId;
  private String acl;
  private DocAcl whitelist;
  private String description;
  private String docSort;
  private boolean isDefault;
  private int sort;
  private final String createdBy;
  private final Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private final int lockVersion;

  public DocSpace(long id, String name, String type, long productId, long projectId, long executionId, String acl,
      DocAcl whitelist, String description, String docSort, boolean isDefault, int sort, String createdBy,
      Instant createdAt, String updatedBy, Instant updatedAt, int lockVersion) {
    this.id = id;
    this.name = name;
    this.type = type;
    this.productId = productId;
    this.projectId = projectId;
    this.executionId = executionId;
    this.acl = acl;
    this.whitelist = whitelist == null ? DocAcl.EMPTY : whitelist;
    this.description = description;
    this.docSort = docSort;
    this.isDefault = isDefault;
    this.sort = sort;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（doc 卡 §5：name/description/acl/whitelist/docSort/isDefault/sort）；null = 不修改。 */
  public void update(String name, String description, String acl, DocAcl whitelist, String docSort,
      Boolean defaultFlag, Integer sort) {
    if (name != null) {
      this.name = name;
    }
    if (description != null) {
      this.description = description;
    }
    if (acl != null) {
      this.acl = acl;
    }
    if (whitelist != null) {
      this.whitelist = whitelist;
    }
    if (docSort != null) {
      this.docSort = docSort;
    }
    if (defaultFlag != null) {
      this.isDefault = defaultFlag;
    }
    if (sort != null) {
      this.sort = sort;
    }
  }

  /** mine 库恒 private 且白名单无意义（doc 卡 §3.1）。 */
  public void forceMineAcl() {
    this.acl = "private";
    this.whitelist = DocAcl.EMPTY;
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  public long id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String type() {
    return type;
  }

  public long productId() {
    return productId;
  }

  public long projectId() {
    return projectId;
  }

  public long executionId() {
    return executionId;
  }

  public String acl() {
    return acl;
  }

  public DocAcl whitelist() {
    return whitelist;
  }

  public String description() {
    return description;
  }

  public String docSort() {
    return docSort;
  }

  public boolean isDefault() {
    return isDefault;
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
