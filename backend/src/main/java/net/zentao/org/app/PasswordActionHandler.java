package net.zentao.org.app;

import java.time.Clock;
import java.util.List;
import java.time.Duration;
import java.util.Map;
import net.zentao.org.domain.Account;
import net.zentao.org.domain.AccountRepository;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.notification.NotificationRecorder;
import net.zentao.platform.session.AccountView;
import net.zentao.platform.session.SessionApi;
import net.zentao.platform.session.SessionPrincipal;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 密码动作（org 卡 §4）：本人改密（oldPassword 校验）与管理员重置（account-reset-password 权限码在端点注解）。
 * 副作用：动态流 passwordChanged；重置通知账号本人；**会话收敛**（T51 SEC-04：本人改密留当前会话、
 * 其他设备全退；管理员重置退目标账号全部会话）——对齐 AccountActionHandler 停用/删号的既有口径。
 */
@Component
public class PasswordActionHandler {

  private final AccountRepository repository;
  private final ActivityRecorder activityRecorder;
  private final NotificationRecorder notificationRecorder;
  private final SessionApi sessionApi;
  private final BCryptPasswordEncoder passwordEncoder;

  @Value("${zentao.login.lock-threshold:6}")
  int lockThreshold;

  @Value("${zentao.login.lock-window:10m}")
  Duration lockWindow;

  /** 密码策略（06 A7-4）：下限 8 位且需字母+数字，均可按部署配置；上限恒 64（BCrypt 与旧口径一致）。 */
  @Value("${zentao.security.password.min-length:8}")
  int passwordMinLength;

  @Value("${zentao.security.password.require-mixed:true}")
  boolean passwordRequireMixed;

  /** 口令历史条数（T62 SEC-11）：新口令不得命中最近 N 个用过的；≤0 = 该半关闭（「新=当前」仍恒拦）。 */
  @Value("${zentao.security.password.history-count:5}")
  int passwordHistoryCount;

  private final Clock clock = Clock.systemDefaultZone();


  private final MessageResolver messages;

  public PasswordActionHandler(AccountRepository repository, ActivityRecorder activityRecorder,
      NotificationRecorder notificationRecorder, SessionApi sessionApi,
      BCryptPasswordEncoder passwordEncoder, MessageResolver messages) {
    this.repository = repository;
    this.activityRecorder = activityRecorder;
    this.notificationRecorder = notificationRecorder;
    this.sessionApi = sessionApi;
    this.passwordEncoder = passwordEncoder;
    this.messages = messages;
  }

  public record AccountPasswordRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String oldPassword,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String newPassword) {}

  public record AccountResetPasswordRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String newPassword) {}

  @Transactional
  public AccountView changePassword(SessionPrincipal actor, long accountId, AccountPasswordRequest command) {
    if (actor == null || actor.accountId() != accountId) {
      throw ApiException.keyed(ErrorCode.DATA_FORBIDDEN, "account.password.selfOnly");
    }
    Account account = repository.findActiveById(accountId).orElseThrow(() -> ApiException.notFound("entity.account"));
    if (command.oldPassword() == null || !passwordEncoder.matches(command.oldPassword(), account.passwordHash())) {
      throw ApiException.validation(Map.of("oldPassword", "mismatch"));
    }
    requireNewPassword(command.newPassword());
    requireNotReused(account, command.newPassword());
    String previousHash = account.passwordHash();
    account.changePassword(passwordEncoder.encode(command.newPassword()));
    account.markUpdatedBy(actor.account());
    Account saved = repository.update(account).orElseThrow(() -> ApiException.lockConflict());
    repository.appendPasswordHistory(accountId, previousHash, actor.account(), passwordHistoryCount);
    activityRecorder.record(actor.account(), "account", accountId, "passwordChanged", null, null);
    // T51 SEC-04：改密即失效其他设备的会话（当前会话保留，否则改完自己就被踢到登录页）
    sessionApi.invalidateOthers(account.account(), actor.sessionId());
    return CreateAccountHandler.toView(saved, repository.roleIdsOf(accountId));
  }

  @Transactional
  public AccountView resetPassword(SessionPrincipal actor, long accountId, AccountResetPasswordRequest command) {
    Account account = repository.findActiveById(accountId).orElseThrow(() -> ApiException.notFound("entity.account"));
    requireNewPassword(command.newPassword());
    requireNotReused(account, command.newPassword());
    String previousHash = account.passwordHash();
    // T62 SEC-11：重置出来的是管理员已知的一次性口令 → 置首登强制改密标记，用户下次登录须先改掉
    account.resetPassword(passwordEncoder.encode(command.newPassword()));
    account.markUpdatedBy(actor == null ? null : actor.account());
    Account saved = repository.update(account).orElseThrow(() -> ApiException.lockConflict());
    repository.appendPasswordHistory(accountId, previousHash, actor == null ? null : actor.account(), passwordHistoryCount);
    activityRecorder.record(actor == null ? null : actor.account(), "account", accountId, "passwordChanged", null, null);
    notificationRecorder.record(List.of(account.account()), "account-reset-password", "account", accountId,
        null, messages.plain("notification.title.passwordReset", null), null, actor == null ? null : actor.account());
    // T51 SEC-04：管理员重置 = 该账号全部会话退（手持旧口令者不该还能用旧会话继续操作）
    sessionApi.invalidateByAccount(account.account());
    return CreateAccountHandler.toView(saved, repository.roleIdsOf(accountId));
  }

  /** 登录失败计数/锁定校验（org 卡 §4：连续失败 ≥6 置 lockedAt，10 分钟窗口内拒绝，超时自动解除不清列）。 */
  public void requireNotLocked(Account account) {
    if (account.isLockedWithin(lockWindow, clock)) {
      throw ApiException.keyed(ErrorCode.UNAUTHENTICATED, "account.login.locked");
    }
  }

  /**
   * 登录失败计数（org 卡 §4：连续失败 ≥6 置 lockedAt，10 分钟窗口内拒绝，超时自动解除不清列）。
   *
   * <p>T58：**锁定期内不再累加**——判定顺序改成「口令正确才回锁定」（SEC-12）之后，锁定期内的错口令也会走到这里，
   * 不设这道闸则每次错口令都刷新 lockedAt = 攻击者用错口令把账号无限续锁（自助 DoS）。
   */
  public void registerFailure(Account account) {
    if (account.isLockedWithin(lockWindow, clock)) {
      return;
    }
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

  /**
   * 禁用复用（T62 SEC-11）：新口令**不得等于当前口令**（该半不受 history-count 影响——「改回原样」永远不该放行，
   * 否则重置出来的一次性口令可以原地改回自己、强制改密变成空转），也不得命中最近 N 个用过的（N = history-count，≤0 该半关闭）。
   *
   * <p>代价：每次改密付 N+1 次 BCrypt（默认 6 次，百毫秒级）——改密是低频操作，先按「哈希比对」这条唯一安全的路走；
   * 要更快只能在写入时另存可 O(1) 比较的摘要，归 T42 评估。
   */
  void requireNotReused(Account account, String newPassword) {
    if (passwordEncoder.matches(newPassword, account.passwordHash())) {
      throw ApiException.validation(Map.of("newPassword", "reused"));
    }
    for (String history : repository.recentPasswordHashes(account.id(), passwordHistoryCount)) {
      if (passwordEncoder.matches(newPassword, history)) {
        throw ApiException.validation(Map.of("newPassword", "reused"));
      }
    }
  }
}
