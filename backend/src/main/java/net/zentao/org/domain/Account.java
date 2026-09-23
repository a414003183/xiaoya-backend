package net.zentao.org.domain;

import java.time.Instant;
import java.time.LocalDate;

/** 账号聚合根（org 卡 §3.1；纯 Java，A1）。password 存哈希，任何端点不回显。 */
public class Account {

  private final long id;
  private String account;
  private String passwordHash;
  private String realName;
  private String nickname;
  private Long departmentId;
  private String email;
  private String mobile;
  private String phone;
  private String gender;
  private LocalDate birthday;
  private LocalDate joinedAt;
  private Long avatarFileId;
  private String status;
  private boolean mustChangePassword;
  private int fails;
  private Instant lockedAt;
  private Instant lastActiveAt;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private Instant deletedAt;
  private int lockVersion;

  public Account(long id, String account, String passwordHash, String realName, String nickname,
      Long departmentId, String email, String mobile, String phone, String gender, LocalDate birthday,
      LocalDate joinedAt, Long avatarFileId, String status, boolean mustChangePassword, int fails, Instant lockedAt,
      Instant lastActiveAt, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt,
      Instant deletedAt, int lockVersion) {
    this.id = id;
    this.account = account;
    this.passwordHash = passwordHash;
    this.realName = realName;
    this.nickname = nickname;
    this.departmentId = departmentId;
    this.email = email;
    this.mobile = mobile;
    this.phone = phone;
    this.gender = gender;
    this.birthday = birthday;
    this.joinedAt = joinedAt;
    this.avatarFileId = avatarFileId;
    this.status = status;
    this.mustChangePassword = mustChangePassword;
    this.fails = fails;
    this.lockedAt = lockedAt;
    this.lastActiveAt = lastActiveAt;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.deletedAt = deletedAt;
    this.lockVersion = lockVersion;
  }

  public void disable() {
    this.status = "disabled";
  }

  public void enable() {
    this.status = "active";
  }

  public void softDelete() {
    this.deletedAt = Instant.now();
  }

  public void unlock() {
    this.fails = 0;
    this.lockedAt = null;
  }

  public void registerLoginFailure(int maxFailures) {
    this.fails += 1;
    if (this.fails >= maxFailures) {
      this.lockedAt = Instant.now();
    }
  }

  public void registerLoginSuccess() {
    this.fails = 0;
    this.lockedAt = null;
    this.lastActiveAt = Instant.now();
  }

  public boolean isLockedWithin(java.time.Duration window, java.time.Clock clock) {
    return lockedAt != null && Instant.now(clock).isBefore(lockedAt.plus(window));
  }

  /** 更新资料（org 卡 §3.1 PATCH 白名单字段；null 不改）。 */
  public void updateProfile(String realName, String nickname, Long departmentId, String email,
      String mobile, String phone, String gender, LocalDate birthday, LocalDate joinedAt, Long avatarFileId) {
    if (realName != null) {
      this.realName = realName;
    }
    if (nickname != null) {
      this.nickname = nickname;
    }
    if (departmentId != null) {
      this.departmentId = departmentId;
    }
    if (email != null) {
      this.email = email;
    }
    if (mobile != null) {
      this.mobile = mobile;
    }
    if (phone != null) {
      this.phone = phone;
    }
    if (gender != null) {
      this.gender = gender;
    }
    if (birthday != null) {
      this.birthday = birthday;
    }
    if (joinedAt != null) {
      this.joinedAt = joinedAt;
    }
    if (avatarFileId != null) {
      this.avatarFileId = avatarFileId;
    }
  }

  /** 标记更新人（写路径统一调用）。 */
  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  /** 本人改密成功即清首登强制改密标记（06 A7-5）。 */
  public void changePassword(String passwordHash) {
    this.passwordHash = passwordHash;
    this.mustChangePassword = false;
  }

  /**
   * 管理员重置：换哈希并**置**首登强制改密标记（T62 SEC-11）——重置出来的是管理员已知的一次性口令，
   * 用户下次登录必须先改掉才算交接完成（旧实现走 {@link #changePassword} 顺手清了标记，
   * 等于管理员永远握着对方正在用的口令）。
   */
  public void resetPassword(String passwordHash) {
    this.passwordHash = passwordHash;
    this.mustChangePassword = true;
  }

  public long id() {
    return id;
  }

  public String account() {
    return account;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public String realName() {
    return realName;
  }

  public String nickname() {
    return nickname;
  }

  public Long departmentId() {
    return departmentId;
  }

  public String email() {
    return email;
  }

  public String mobile() {
    return mobile;
  }

  public String phone() {
    return phone;
  }

  public String gender() {
    return gender;
  }

  public LocalDate birthday() {
    return birthday;
  }

  public LocalDate joinedAt() {
    return joinedAt;
  }

  public Long avatarFileId() {
    return avatarFileId;
  }

  public String status() {
    return status;
  }

  /** 首登强制改密标记（只读；种子/迁移生成的初始口令置位，本人改密后清零）。 */
  public boolean mustChangePassword() {
    return mustChangePassword;
  }

  public int fails() {
    return fails;
  }

  public Instant lockedAt() {
    return lockedAt;
  }

  public Instant lastActiveAt() {
    return lastActiveAt;
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

  public Instant deletedAt() {
    return deletedAt;
  }

  public int lockVersion() {
    return lockVersion;
  }
}
