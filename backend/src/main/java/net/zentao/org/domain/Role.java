package net.zentao.org.domain;

import java.time.Instant;

/**
 * 角色（T23 统一实体）：一个角色 = 一组权限码（{@code role_priv}）+ 一批成员（{@code user_role}）+
 * 一份数据权限（{@link RoleAcl}）。
 *
 * <p>统一前的两类角色已合并到这里：权限角色（旧 auth_group）与岗位角色（旧 account_role 字典）。
 * 账号与角色是成员关系（一个账号可有多个角色），账号上的「角色」列读的就是这张表。
 * {@code builtin} = 内置角色（超管角色 id=1 与迁移来的岗位角色）不可删除；
 * {@code code} 是可选稳定标识（旧岗位角色的研发/测试等码），新建角色可留空。
 */
public class Role {

  /** 角色名上限（与 role.name 列同宽）。 */
  public static final int NAME_MAX = 60;
  /** 角色码上限（与 role.code 列同宽）：`^[a-z][a-z0-9-]*$`。 */
  public static final int CODE_MAX = 32;

  private final long id;
  private final String code;
  private String name;
  private String description;
  private RoleAcl acl;
  private final boolean builtin;
  private int sort;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private int lockVersion;

  public Role(long id, String code, String name, String description, RoleAcl acl, boolean builtin, int sort,
      String createdBy, Instant createdAt, String updatedBy, Instant updatedAt, int lockVersion) {
    this.id = id;
    this.code = code;
    this.name = name;
    this.description = description;
    this.acl = acl == null ? RoleAcl.EMPTY : acl;
    this.builtin = builtin;
    this.sort = sort;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** 审计四件（T67/DB-18）：更新前打「谁在什么时候改的」，仓储落 updated_by/updated_at。 */
  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  public void rename(String newName) {
    this.name = newName;
  }

  public void changeDescription(String newDescription) {
    this.description = newDescription;
  }

  public void changeAcl(RoleAcl newAcl) {
    this.acl = newAcl == null ? RoleAcl.EMPTY : newAcl;
  }

  public void reorder(int newSort) {
    this.sort = newSort;
  }

  /** 角色码合法（小写字母开头，字母/数字/连字符）：旧岗位角色码的同一口径。 */
  public static boolean validCode(String code) {
    return code != null && code.matches("^[a-z][a-z0-9-]{1," + (CODE_MAX - 1) + "}$");
  }

  public long id() {
    return id;
  }

  public String code() {
    return code;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public RoleAcl acl() {
    return acl;
  }

  public boolean builtin() {
    return builtin;
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
