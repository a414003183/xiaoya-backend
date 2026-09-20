package net.zentao.project.domain;

import java.time.Instant;
import java.util.List;

/**
 * 看板空间聚合根（project 卡 §3.3；纯 Java，A1）。
 * 白名单不入聚合列，由 acl_entry 单独承载（§2 单源），仓储装配时读入 {@link #whitelist()}。
 */
public class BoardSpace {

  private final long id;
  private String name;
  private String type;
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

  public BoardSpace(long id, String name, String type, String owner, List<String> team, String description,
      String acl, List<String> whitelist, String status, int sort, String createdBy, Instant createdAt,
      String updatedBy, Instant updatedAt, String closedBy, Instant closedAt, int lockVersion) {
    this.id = id;
    this.name = name;
    this.type = type;
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

  /** PATCH 白名单（§5：name/type/owner/team/acl/whitelist/description/sort；null 不改）。 */
  public void update(String name, String type, String owner, List<String> team, String acl, List<String> whitelist,
      String description, Integer sort) {
    if (name != null) {
      this.name = name;
    }
    if (type != null) {
      this.type = type;
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
      default -> throw new IllegalArgumentException("看板空间未声明的 fieldSet 字段：" + field);
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

  public String name() {
    return name;
  }

  public String type() {
    return type;
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
