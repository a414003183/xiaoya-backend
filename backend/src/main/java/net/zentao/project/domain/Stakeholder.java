package net.zentao.project.domain;

import java.time.Instant;

/**
 * 干系人聚合根（project 卡 §3.8；纯 Java，A1）：(objectType, objectId, account) 唯一，重复添加 → 42201；
 * 移除为软删，同键再次添加由仓储复活同键行（unique 索引不允许插入第二行）。
 */
public class Stakeholder {

  private final long id;
  private final String objectType;
  private final long objectId;
  private final String account;
  private String type;
  private boolean isKey;
  private String source;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;

  public Stakeholder(long id, String objectType, long objectId, String account, String type, boolean isKey,
      String source, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
    this.id = id;
    this.objectType = objectType;
    this.objectId = objectId;
    this.account = account;
    this.type = type;
    this.isKey = isKey;
    this.source = source;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
  }

  /** 软删行复活（再添加同 account）：提交值覆盖，id 与 createdBy/createdAt 保留。 */
  public void revive(String type, boolean isKey, String source, String actor) {
    this.type = type;
    this.isKey = isKey;
    this.source = source;
    markUpdatedBy(actor);
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  public long id() {
    return id;
  }

  public String objectType() {
    return objectType;
  }

  public long objectId() {
    return objectId;
  }

  public String account() {
    return account;
  }

  public String type() {
    return type;
  }

  public boolean isKey() {
    return isKey;
  }

  public String source() {
    return source;
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
}
