package net.zentao.platform.session;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 会话失效/收敛 API：供业务域在停用/软删账号、改密、登录时调整既有会话（org 卡 §4 副作用；T51 SEC-04/18）。
 */
@Component
public class SessionApi {

  private final SessionRepository repository;
  private final int maxPerAccount;

  public SessionApi(SessionRepository repository, @Value("${zentao.session.max-per-account:5}") int maxPerAccount) {
    this.repository = repository;
    this.maxPerAccount = maxPerAccount;
  }

  public void invalidateByAccount(String account) {
    repository.deleteByAccount(account);
  }

  /**
   * 该账号除本条会话外全部失效（T51 SEC-04：本人改密保留当前会话——否则改完自己就被踢到登录页，
   * 而首登强制改密流程会直接走不下去）。
   *
   * <p>{@code keepSessionId} 缺失时按"一条不留"处理（fail-closed）：拿不到当前会话就不假设它安全。
   */
  public void invalidateOthers(String account, String keepSessionId) {
    if (keepSessionId == null || keepSessionId.isBlank()) {
      invalidateByAccount(account);
      return;
    }
    repository.deleteByAccountExcept(account, keepSessionId);
  }

  /**
   * 并发会话上限（T51 SEC-18）：登录成功后调用（新行已插入），超出的按最后活动升序踢最旧——
   * 最久没动的先退，过期未清的行天然排在最前、顺带被删掉。{@code maxPerAccount} ≤ 0 = 不限。
   */
  public void enforceConcurrentLimit(long accountId) {
    if (maxPerAccount <= 0) {
      return;
    }
    List<SessionPO> sessions = repository.listByAccount(accountId);
    for (int index = 0; index < sessions.size() - maxPerAccount; index++) {
      repository.delete(sessions.get(index).getId());
    }
  }
}
