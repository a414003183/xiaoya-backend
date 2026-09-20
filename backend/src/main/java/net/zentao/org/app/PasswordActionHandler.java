package net.zentao.org.app;

import java.time.Clock;
import java.util.List;
import java.time.Duration;
import java.util.Map;
import net.zentao.org.domain.Account;
import net.zentao.org.domain.AccountRepository;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.notification.NotificationRecorder;
import net.zentao.platform.session.AccountView;
import net.zentao.platform.session.SessionPrincipal;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 密码动作（org 卡 §4）：本人改密（oldPassword 校验）与管理员重置（account-reset-password 权限码在端点注解）。
 * 副作用：动态流 passwordChanged；重置通知账号本人。
 */
@Component
public class PasswordActionHandler {

  private final AccountRepository repository;
  private final ActivityRecorder activityRecorder;
  private final NotificationRecorder notificationRecorder;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  @Value("${zentao.login.lock-threshold:6}")
  int lockThreshold;

  @Value("${zentao.login.lock-window:10m}")
  Duration lockWindow;

  /** 密码策略（06 A7-4）：下限 8 位且需字母+数字，均可按部署配置；上限恒 64（BCrypt 与旧口径一致）。 */
  @Value("${zentao.security.password.min-length:8}")
  int passwordMinLength;

  @Value("${zentao.security.password.require-mixed:true}")
  boolean passwordRequireMixed;

  private final Clock clock = Clock.systemDefaultZone();

  public PasswordActionHandler(AccountRepository repository, ActivityRecorder activityRecorder,
      NotificationRecorder notificationRecorder) {
    this.repository = repository;
    this.activityRecorder = activityRecorder;
    this.notificationRecorder = notificationRecorder;
  }

  public record AccountPasswordRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String oldPassword,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String newPassword) {}

  public record AccountResetPasswordRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String newPassword) {}

  @Transactional
  public AccountView changePassword(SessionPrincipal actor, long accountId, AccountPasswordRequest command) {
    if (actor == null || actor.accountId() != accountId) {
      throw ApiException.dataForbidden("只能修改本人密码。");
    }
    Account account = repository.findActiveById(accountId).orElseThrow(() -> ApiException.notFound("账号"));
    if (command.oldPassword() == null || !passwordEncoder.matches(command.oldPassword(), account.passwordHash())) {
      throw ApiException.validation(Map.of("oldPassword", "mismatch"));
    }
    requireNewPassword(command.newPassword());
    account.changePassword(passwordEncoder.encode(command.newPassword()));
    account.markUpdatedBy(actor.account());
    Account saved = repository.update(account).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新。"));
    activityRecorder.record(actor.account(), "account", accountId, "passwordChanged", null, null);
    return CreateAccountHandler.toView(saved, repository.groupIdsOf(accountId));
  }

  @Transactional
  public AccountView resetPassword(SessionPrincipal actor, long accountId, AccountResetPasswordRequest command) {
    Account account = repository.findActiveById(accountId).orElseThrow(() -> ApiException.notFound("账号"));
    requireNewPassword(command.newPassword());
    account.changePassword(passwordEncoder.encode(command.newPassword()));
    account.markUpdatedBy(actor == null ? null : actor.account());
    Account saved = repository.update(account).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新。"));
    activityRecorder.record(actor == null ? null : actor.account(), "account", accountId, "passwordChanged", null, null);
    notificationRecorder.record(List.of(account.account()), "account-reset-password", "account", accountId,
        null, "您的密码已被管理员重置", null, actor == null ? null : actor.account());
    return CreateAccountHandler.toView(saved, repository.groupIdsOf(accountId));
  }

  /** 登录失败计数/锁定校验（org 卡 §4：连续失败 ≥6 置 lockedAt，10 分钟窗口内拒绝，超时自动解除不清列）。 */
  public void requireNotLocked(Account account) {
    if (account.isLockedWithin(lockWindow, clock)) {
      throw ApiException.unauthenticated("账号已锁定，请稍后再试。");
    }
  }

  public void registerFailure(Account account) {
    account.registerLoginFailure(lockThreshold);
    repository.update(account);
  }

  public void registerSuccess(Account account) {
    account.registerLoginSuccess();
    repository.update(account);
  }

  /** 密码策略（06 A7-4，06 §七 决策⑥）：长度区间 + 可选「字母+数字」混合要求；fields 下发 newPassword 供表单标红。 */
  void requireNewPassword(String newPassword) {
    if (newPassword == null || newPassword.length() < passwordMinLength || newPassword.length() > 64) {
      throw ApiException.validation(Map.of("newPassword", "size"));
    }
    if (passwordRequireMixed && !hasLetterAndDigit(newPassword)) {
      throw ApiException.validation(Map.of("newPassword", "weak"));
    }
  }

  private static boolean hasLetterAndDigit(String value) {
    boolean letter = false;
    boolean digit = false;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (Character.isLetter(character)) letter = true;
      else if (Character.isDigit(character)) digit = true;
      if (letter && digit) return true;
    }
    return false;
  }
}
