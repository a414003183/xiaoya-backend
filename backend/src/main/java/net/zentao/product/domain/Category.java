package net.zentao.product.domain;

import java.time.Instant;

/** 产品分类节点聚合根（product 卡 §3.3；同产品同 type 一棵树，path/grade 不落库）。 */
public class Category {

  private final long id;
  private long productId;
  private long branchId;
  private long parentId;
  private String type;
  private String name;
  private String owner;
  private int sort;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private int lockVersion;

  public Category(long id, long productId, long branchId, long parentId, String type, String name, String owner,
      int sort, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt, int lockVersion) {
    this.id = id;
    this.productId = productId;
    this.branchId = branchId;
    this.parentId = parentId;
    this.type = type;
    this.name = name;
    this.owner = owner;
    this.sort = sort;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（product 卡 §5：name/owner/sort/parentId）；null 不改；productId/type 创建后不可改。 */
  public void update(String name, String owner, Integer sort, Long parentId) {
    if (name != null) {
      this.name = name;
    }
    if (owner != null) {
      this.owner = owner;
    }
    if (sort != null) {
      this.sort = sort;
    }
    if (parentId != null) {
      this.parentId = parentId;
    }
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  public long id() {
    return id;
  }

  public long productId() {
    return productId;
  }

  public long branchId() {
    return branchId;
  }

  public long parentId() {
    return parentId;
  }

  public String type() {
    return type;
  }

  public String name() {
    return name;
  }

  public String owner() {
    return owner;
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
