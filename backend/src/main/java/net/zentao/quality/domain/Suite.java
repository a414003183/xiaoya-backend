package net.zentao.quality.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 套件聚合根（quality 卡 §3.3；纯 Java，A1）：一表两义，Library = type=library 且 productId=0。 */
public class Suite {

  public static final String TYPE_LIBRARY = "library";

  private final long id;
  private long productId;
  private String name;
  private String description;
  private String type;
  private int sort;
  private List<Long> caseIds;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private int lockVersion;

  public Suite(long id, long productId, String name, String description, String type, int sort,
      List<Long> caseIds, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt,
      int lockVersion) {
    this.id = id;
    this.productId = productId;
    this.name = name;
    this.description = description;
    this.type = type;
    this.sort = sort;
    this.caseIds = caseIds == null ? List.of() : List.copyOf(caseIds);
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（quality 卡 §5）；null 不改。 */
  public void update(String name, String description, String type, Integer sort) {
    if (name != null) {
      this.name = name;
    }
    if (description != null) {
      this.description = description;
    }
    if (type != null) {
      this.type = type;
    }
    if (sort != null) {
      this.sort = sort;
    }
  }

  /** 关联用例（幂等：已在集合内的不重复）。 */
  public List<Long> linkCases(List<Long> ids) {
    List<Long> merged = new ArrayList<>(caseIds);
    for (Long id : ids) {
      if (!merged.contains(id)) {
        merged.add(id);
      }
    }
    this.caseIds = List.copyOf(merged);
    return caseIds;
  }

  /** 解除关联（不存在的 id 静默忽略）。 */
  public List<Long> unlinkCases(List<Long> ids) {
    this.caseIds = caseIds.stream().filter(id -> !ids.contains(id)).toList();
    return caseIds;
  }

  public boolean isLibrary() {
    return TYPE_LIBRARY.equals(type);
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

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public String type() {
    return type;
  }

  public int sort() {
    return sort;
  }

  public List<Long> caseIds() {
    return caseIds;
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
