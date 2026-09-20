package net.zentao.platform.session;

import java.time.Instant;
import org.springframework.stereotype.Component;

/** 登出处理器：物理删会话行（platform 卡 §4.1，登出后旧 cookie 立即失效）。 */
@Component
public class LogoutHandler {

  private final SessionRepository repository;

  public LogoutHandler(SessionRepository repository) {
    this.repository = repository;
  }

  public void logout(String token) {
    repository.delete(token);
  }

  public boolean isExpired(SessionPO session, Instant now) {
    return now.isAfter(session.getExpiresAt());
  }
}
