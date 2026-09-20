package net.zentao.project.domain;

import java.time.Instant;
import java.util.List;

/**
 * 看板聚合根（project 卡 §3.4；纯 Java，A1）。spaceId 创建后不可改；acl=extend 继承空间可见性。
 * 白名单由 acl_entry 承载（§2 单源），仓储装配时读入 {@link #whitelist()}。
 */
public class Board {

  private final long id;
  private long spaceId;
  private String name;
  private String owner;
  private List<String> team;
  private String description;
  private String acl;
  private List<String> whitelist;
  private String status;
  private int sort;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private String closedBy;
  private Instant closedAt;
  private int lockVersion;

  public Board(long id, long spaceId, String name, String owner, List<String> team, String description, String acl,
      List<String> whitelist, String status, int sort, String createdBy, Instant createdAt, String updatedBy,
      Instant updatedAt, String closedBy, Instant closedAt, int lockVersion) {
    this.id = id;
    this.spaceId = spaceId;
    this.name = name;
    this.owner = owner;
    this.team = team == null ? List.of() : List.copyOf(team);
    this.description = description;
    this.acl = acl;
    this.whitelist = whitelist == null ? List.of() : List.copyOf(whitelist);
    this.status = status;
    this.sort = sort;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.closedBy = closedBy;
    this.closedAt = closedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（§5：name/owner/team/acl/whitelist/description/sort；null 不改）。 */
  public void update(String name, String owner, List<String> team, String acl, List<String> whitelist,
      String description, Integer sort) {
    if (name != null) {
      this.name = name;
    }
    if (owner != null) {
      this.owner = owner;
    }
    if (team != null) {
      this.team = List.copyOf(team);
    }
    if (acl != null) {
      this.acl = acl;
    }
    if (whitelist != null) {
      this.whitelist = List.copyOf(whitelist);
    }
    if (description != null) {
      this.description = description;
    }
    if (sort != null) {
      this.sort = sort;
    }
  }

  /** 状态机落状态（workflow/board.yml → WorkflowEngine.applyStatus）。 */
  public void applyStatus(String status) {
    this.status = status;
  }

  /** fieldSet 副作用入口（workflow/board.yml）：只接受本聚合声明的字段。 */
  public void setField(String field, Object value) {
    switch (field) {
      case "closedBy" -> this.closedBy = value == null ? null : String.valueOf(value);
      case "closedAt" -> this.closedAt = value == null ? null : toInstant(value);
      default -> throw new IllegalArgumentException("看板未声明的 fieldSet 字段：" + field);
    }
  }

  private static Instant toInstant(Object value) {
    return value instanceof Instant instant ? instant : Instant.parse(String.valueOf(value));
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  public long id() {
    return id;
  }

  public long spaceId() {
    return spaceId;
  }

  public String name() {
    return name;
  }

  public String owner() {
    return owner;
  }

  public List<String> team() {
    return team;
  }

  public String description() {
    return description;
  }

  public String acl() {
    return acl;
  }

  public List<String> whitelist() {
    return whitelist;
  }

  public String status() {
    return status;
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

  public String closedBy() {
    return closedBy;
  }

  public Instant closedAt() {
    return closedAt;
  }

  public int lockVersion() {
    return lockVersion;
  }
}
