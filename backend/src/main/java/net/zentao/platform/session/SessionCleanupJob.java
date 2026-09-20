package net.zentao.platform.session;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每日清理过期会话行（platform 卡 §4.1 惰性清理的兜底）。 */
@Component
public class SessionCleanupJob {

  private final SessionRepository repository;

  public SessionCleanupJob(SessionRepository repository) {
    this.repository = repository;
  }

  @Scheduled(cron = "${zentao.session.cleanup-cron:0 0 4 * * *}")
  public void cleanup() {
    repository.deleteExpiredBefore(Instant.now());
  }
}
