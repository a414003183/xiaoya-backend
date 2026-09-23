package net.zentao.platform.notification;

import java.time.Instant;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/** 标记已读（platform 卡 §4.2）：幂等 200；他人通知 → 40302。 */
@Component
public class MarkNotificationReadHandler {

  private final NotificationRepository repository;

  public MarkNotificationReadHandler(NotificationRepository repository) {
    this.repository = repository;
  }

  public NotificationView markRead(SessionPrincipal principal, long notificationId) {
    NotificationPO po = repository.findById(notificationId).orElseThrow(() -> ApiException.notFound("entity.notification"));
    if (!principal.account().equals(po.getRecipient())) {
      throw ApiException.keyed(ErrorCode.DATA_FORBIDDEN, "notification.guard.notRecipient");
    }
    if (po.getReadAt() == null) {
      po.setReadAt(Instant.now());
      repository.update(po);
    }
    return NotificationViews.toView(po);
  }
}
