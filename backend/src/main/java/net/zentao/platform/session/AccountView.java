package net.zentao.platform.session;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 账号响应视图（contract/openapi.yaml AccountView；字段真源 = org 卡 §3.1 读侧）。
 * 本类型归 platform.session（/me 的契约面），org 域构建后经网关回传——A3 禁止 platform 反向引用 org。
 * password 只写不读，任何端点不回显（org 卡 §3.1）；mustChangePassword 为只读标记（06 A7-5 首登强制改密）。
 * roleIds 是账号所属角色的 id 集（T23 统一角色：账号 ↔ 角色是成员关系，权限码由角色决定，
 * 故账号自身不存角色名——名字在角色表里，改一次全站跟着变）。
 */
public record AccountView(
    long id,
    String account,
    String realName,
    String nickname,
    Long departmentId,
    String email,
    String mobile,
    String phone,
    Gender gender,
    LocalDate birthday,
    LocalDate joinedAt,
    Long avatarFileId,
    AccountStatus status,
    boolean mustChangePassword,
    List<Long> roleIds,
    int fails,
    Instant lockedAt,
    Instant lastActiveAt,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    Instant deletedAt,
    int lockVersion) {

  public AccountView {
    roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
  }
}
