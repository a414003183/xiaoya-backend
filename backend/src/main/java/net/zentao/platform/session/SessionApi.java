package net.zentao.platform.session;

import org.springframework.stereotype.Component;

/** 会话失效 API：供业务域在停用/软删账号时立即使其全部会话失效（org 卡 §4 副作用）。 */
@Component
public class SessionApi {

  private final SessionRepository repository;

  public SessionApi(SessionRepository repository) {
    this.repository = repository;
  }

  public void invalidateByAccount(String account) {
    repository.deleteByAccount(account);
  }
}
