package net.zentao.org.app;

import java.util.List;
import net.zentao.org.domain.Account;
import net.zentao.org.domain.AccountRepository;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.AccountView;
import net.zentao.platform.session.SessionApi;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号状态动作（org 卡 §4 状态机）：disable/enable/unlock/delete。
 * 守卫统一 42203：目标 ≠ 当前登录者且 ≠ 内置 admin 账号（disable/delete）；
 * 副作用：会话失效（platform SessionApi）+ 动态流。
 */
@Component
public class AccountActionHandler {

  private final AccountRepository repository;
  private final SessionApi sessionApi;
  private final ActivityRecorder activityRecorder;

  public AccountActionHandler(AccountRepository repository, SessionApi sessionApi, ActivityRecorder activityRecorder) {
    this.repository = repository;
    this.activityRecorder = activityRecorder;
    this.sessionApi = sessionApi;
  }

  public record AccountActionRequest(String comment) {}

  @Transactional
  public AccountView disable(SessionPrincipal actor, long accountId, AccountActionRequest command) {
    Account account = requireNotSelfNorAdmin(actor, accountId, "停用");
    if (!"active".equals(account.status())) {
      throw ApiException.stateActionNotAllowed("当前状态不允许停用。");
    }
    account.disable();
    account.markUpdatedBy(actor == null ? null : actor.account());
    Account saved = repository.update(account).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新。"));
    sessionApi.invalidateByAccount(account.account());
    activityRecorder.record(actor == null ? null : actor.account(), "account", accountId, "disabled", null, command.comment());
    return CreateAccountHandler.toView(saved, repository.groupIdsOf(accountId));
  }

  @Transactional
  public AccountView enable(SessionPrincipal actor, long accountId, AccountActionRequest command) {
    Account account = repository.findActiveById(accountId).orElseThrow(() -> ApiException.notFound("账号"));
    if (!"disabled".equals(account.status())) {
      throw ApiException.stateActionNotAllowed("当前状态不允许启用。");
    }
    account.enable();
    account.markUpdatedBy(actor == null ? null : actor.account());
    Account saved = repository.update(account).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新。"));
    activityRecorder.record(actor == null ? null : actor.account(), "account", accountId, "enabled", null, command.comment());
    return CreateAccountHandler.toView(saved, repository.groupIdsOf(accountId));
  }

  @Transactional
  public AccountView unlock(SessionPrincipal actor, long accountId) {
    Account account = repository.findActiveById(accountId).orElseThrow(() -> ApiException.notFound("账号"));
    account.unlock();
    account.markUpdatedBy(actor == null ? null : actor.account());
    Account saved = repository.update(account).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新。"));
    activityRecorder.record(actor == null ? null : actor.account(), "account", accountId, "unlocked", null, null);
    return CreateAccountHandler.toView(saved, repository.groupIdsOf(accountId));
  }

  @Transactional
  public AccountView delete(SessionPrincipal actor, long accountId, AccountActionRequest command) {
    Account account = requireNotSelfNorAdmin(actor, accountId, "删除");
    account.softDelete();
    account.markUpdatedBy(actor == null ? null : actor.account());
    Account saved = repository.update(account).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新。"));
    sessionApi.invalidateByAccount(account.account());
    activityRecorder.record(actor == null ? null : actor.account(), "account", accountId, "deleted", null, command.comment());
    return CreateAccountHandler.toView(saved, repository.groupIdsOf(accountId));
  }

  /** 内置 admin 账号（V3 种子 id=1）。 */
  public static final long BUILT_IN_ADMIN_ID = 1L;

  private Account requireNotSelfNorAdmin(SessionPrincipal actor, long accountId, String action) {
    if (actor != null && actor.accountId() == accountId) {
      throw ApiException.guardNotSatisfied("不能对自己执行" + action + "操作。");
    }
    if (accountId == BUILT_IN_ADMIN_ID) {
      throw ApiException.guardNotSatisfied("内置 admin 账号不可" + action + "。");
    }
    return repository.findActiveById(accountId).orElseThrow(() -> ApiException.notFound("账号"));
  }
}
