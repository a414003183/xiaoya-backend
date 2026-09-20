package net.zentao.doc.domain;

import java.time.Instant;
import java.util.List;

/**
 * 文档聚合根（doc 卡 §3.2；纯 Java，A1）。
 * {@code version} = 最新已发布版本号（0 = 从未发布）；{@code path} 为物化章节路径（,1,12,），由 parentId 链生成。
 */
public class Doc {

  private final long id;
  private long docSpaceId;
  private long productId;
  private long projectId;
  private long executionId;
  private long categoryId;
  private long parentId;
  private String path;
  private String title;
  private String keywords;
  private String type;
  private String status;
  private String acl;
  private DocAcl editors;
  private DocAcl readers;
  private List<String> notifyAccounts;
  private int views;
  private int version;
  private int sort;
  private final String createdBy;
  private final Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private final int lockVersion;

  public Doc(long id, long docSpaceId, long productId, long projectId, long executionId, long categoryId,
      long parentId, String path, String title, String keywords, String type, String status, String acl,
      DocAcl editors, DocAcl readers, List<String> notifyAccounts, int views, int version, int sort,
      String createdBy, Instant createdAt, String updatedBy, Instant updatedAt, int lockVersion) {
    this.id = id;
    this.docSpaceId = docSpaceId;
    this.productId = productId;
    this.projectId = projectId;
    this.executionId = executionId;
    this.categoryId = categoryId;
    this.parentId = parentId;
    this.path = path;
    this.title = title;
    this.keywords = keywords;
    this.type = type;
    this.status = status;
    this.acl = acl;
    this.editors = editors == null ? DocAcl.EMPTY : editors;
    this.readers = readers == null ? DocAcl.EMPTY : readers;
    this.notifyAccounts = notifyAccounts == null ? List.of() : List.copyOf(notifyAccounts);
    this.views = views;
    this.version = version;
    this.sort = sort;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** 基本信息 PATCH 白名单（doc 卡 §5：title/keywords/categoryId/parentId/acl/editors/readers/notifyAccounts/sort）。 */
  public void updateBasic(String title, String keywords, Long categoryId, Long parentId, String acl, DocAcl editors,
      DocAcl readers, List<String> notifyAccounts, Integer sort) {
    if (title != null) {
      this.title = title;
    }
    if (keywords != null) {
      this.keywords = keywords;
    }
    if (categoryId != null) {
      this.categoryId = categoryId;
    }
    if (parentId != null) {
      this.parentId = parentId;
    }
    if (acl != null) {
      this.acl = acl;
    }
    if (editors != null) {
      this.editors = editors;
    }
    if (readers != null) {
      this.readers = readers;
    }
    if (notifyAccounts != null) {
      this.notifyAccounts = List.copyOf(notifyAccounts);
    }
    if (sort != null) {
      this.sort = sort;
    }
    // acl=open 时 editors/readers 强制清空（doc 卡 §3.2）；此处统一收口，PATCH/create 共用。
    if ("open".equals(this.acl)) {
      this.editors = DocAcl.EMPTY;
      this.readers = DocAcl.EMPTY;
    }
  }

  /** 换库/换目录/换父章节：冗余归属列由所属库带出，path 由调用方按父链重建（doc 卡 §4 move）。 */
  public void moveTo(long docSpaceId, long categoryId, long parentId, long productId, long projectId,
      long executionId) {
    this.docSpaceId = docSpaceId;
    this.categoryId = categoryId;
    this.parentId = parentId;
    this.productId = productId;
    this.projectId = projectId;
    this.executionId = executionId;
  }

  public void applyPath(String path) {
    this.path = path;
  }

  /** 存量 html 型文档一经编辑保存即落为 markdown（doc 卡 §1/§3.2）。 */
  public void convertToMarkdown() {
    if ("html".equals(this.type)) {
      this.type = "markdown";
    }
  }

  /** 状态机落状态（workflow/doc.yml → WorkflowEngine.applyStatus）。 */
  public void applyStatus(String status) {
    this.status = status;
  }

  /** 发布快照落定：version = 最新发布版本号，标题同步为快照标题（doc 卡 §4 publish）。 */
  public void publishedAs(int version, String snapshotTitle) {
    this.version = version;
    this.title = snapshotTitle;
    this.status = "published";
  }

  public void countView() {
    this.views = this.views + 1;
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

  public long productId() {
    return productId;
  }

  public long projectId() {
    return projectId;
  }

  public long executionId() {
    return executionId;
  }

  public long categoryId() {
    return categoryId;
  }

  public long parentId() {
    return parentId;
  }

  public String path() {
    return path;
  }

  public String title() {
    return title;
  }

  public String keywords() {
    return keywords;
  }

  public String type() {
    return type;
  }

  public String status() {
    return status;
  }

  public String acl() {
    return acl;
  }

  public DocAcl editors() {
    return editors;
  }

  public DocAcl readers() {
    return readers;
  }

  public List<String> notifyAccounts() {
    return notifyAccounts;
  }

  public int views() {
    return views;
  }

  public int version() {
    return version;
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
