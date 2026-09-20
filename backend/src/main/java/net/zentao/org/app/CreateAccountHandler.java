package net.zentao.org.app;

import java.time.Instant;
import java.util.List;
import net.zentao.org.domain.Account;
import net.zentao.org.domain.AccountRepository;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.AccountView;
import net.zentao.platform.session.SessionPrincipal;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建账号（org 卡 §4/§8）：account 全库唯一 → 42201；初始 groupIds 写 user_group；动态流 created。
 * AccountView 装配（platform 契约面，org 经网关方向构建）。
 */
@Component
public class CreateAccountHandler {

  private final AccountRepository repository;
  private final ActivityRecorder activityRecorder;
  private final AccountRoleValidator roleValidator;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  public CreateAccountHandler(AccountRepository repository, ActivityRecorder activityRecorder,
      AccountRoleValidator roleValidator) {
    this.repository = repository;
    this.activityRecorder = activityRecorder;
    this.roleValidator = roleValidator;
  }

  public record AccountCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String account,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String password,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String realName,
      String nickname,
      @Schema(maxLength = 16, description = "账号角色码，必须存在于角色字典（GET /roles）") String role,
      Long departmentId, String email, String mobile, String phone,
      @Schema(allowableValues = {"m", "f"}) String gender, java.time.LocalDate birthday,
      java.time.LocalDate joinedAt, Long avatarFileId, List<Long> groupIds) {}

  @Transactional
  public Account handle(SessionPrincipal actor, AccountCreateRequest command) {
    validate(command.account(), command.password(), command.realName());
    roleValidator.require(command.role());
    if (repository.existsByAccount(command.account())) {
      throw ApiException.validation(java.util.Map.of("account", "duplicate"));
    }
    if (command.groupIds() != null && !command.groupIds().isEmpty()) {
      List<Long> missing = repository.findMissingGroupIds(command.groupIds());
      if (!missing.isEmpty()) {
        throw ApiException.validation(java.util.Map.of("groupIds", "notFound"));
      }
    }
    Instant now = Instant.now();
    Account account = repository.insert(new Account(
        0, command.account(), passwordEncoder.encode(command.password()), command.realName(),
        command.nickname(), command.role(), command.departmentId(), command.email(), command.mobile(),
        command.phone(), command.gender() == null ? "m" : command.gender(), command.birthday(),
        command.joinedAt(), command.avatarFileId(), "active", false, 0, null, null,
        actor == null ? null : actor.account(), now, null, null, null, 0));
    if (command.groupIds() != null && !command.groupIds().isEmpty()) {
      repository.replaceGroups(account.id(), command.groupIds());
    }
    if (actor != null) {
      activityRecorder.record(actor.account(), "account", account.id(), "created", null, null);
    }
    return account;
  }

  static void validate(String account, String password, String realName) {
    if (account == null || !account.matches("^[a-zA-Z0-9._-]{3,30}$")) {
      throw ApiException.validation(java.util.Map.of("account", "pattern"));
    }
    if (password == null || password.length() < 6 || password.length() > 64) {
      throw ApiException.validation(java.util.Map.of("password", "size"));
    }
    if (realName == null || realName.isBlank() || realName.length() > 100) {
      throw ApiException.validation(java.util.Map.of("realName", "required"));
    }
  }

  public static AccountView toView(Account account, List<Long> groupIds) {
    return new AccountView(account.id(), account.account(), account.realName(), account.nickname(),
        account.role(),
        account.departmentId(), account.email(), account.mobile(), account.phone(),
        account.gender() == null ? null : net.zentao.platform.session.Gender.valueOf(account.gender()),
        account.birthday(), account.joinedAt(), account.avatarFileId(),
        net.zentao.platform.session.AccountStatus.valueOf(account.status()), account.mustChangePassword(), groupIds,
        account.fails(), account.lockedAt(), account.lastActiveAt(), account.createdBy(),
        account.createdAt(), account.updatedBy(), account.updatedAt(), account.deletedAt(), account.lockVersion());
  }
}
