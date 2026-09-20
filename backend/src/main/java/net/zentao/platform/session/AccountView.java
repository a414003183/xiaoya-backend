package net.zentao.platform.session;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 账号响应视图（contract/openapi.yaml AccountView；字段真源 = org 卡 §3.1 读侧）。
 * 本类型归 platform.session（/me 的契约面），org 域构建后经网关回传——A3 禁止 platform 反向引用 org。
 * password 只写不读，任何端点不回显（org 卡 §3.1）；mustChangePassword 为只读标记（06 A7-5 首登强制改密）。
 * role 是角色码字符串（取值来自 account_role 字典，见 org 卡 §3.4）——不再是 Java 枚举：角色集可维护，
 * 枚举化会让自定义角色在会话层被判空（旧的 valueOf 口径已废）。
 */
public record AccountView(
    long id,
    String account,
    String realName,
    String nickname,
    String role,
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
    List<Long> groupIds,
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
    groupIds = groupIds == null ? List.of() : List.copyOf(groupIds);
  }
}
