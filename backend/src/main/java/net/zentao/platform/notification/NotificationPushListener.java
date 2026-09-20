package net.zentao.platform.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 通知推送监听（01 §2.4）：事务提交后（无事务则立即）把新通知推给在线 SSE 订阅者。 */
@Component
public class NotificationPushListener {

  private final NotificationSseRegistry registry;

  public NotificationPushListener(NotificationSseRegistry registry) {
    this.registry = registry;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onCreated(NotificationRecorder.NotificationCreatedEvent event) {
    for (NotificationView view : event.views()) {
      registry.sendCreated(view);
    }
  }
}
