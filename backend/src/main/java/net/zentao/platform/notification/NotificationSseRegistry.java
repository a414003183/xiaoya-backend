package net.zentao.platform.notification;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.zentao.platform.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

/**
 * SSE 连接注册表（platform 卡 §5.1）：每账号 ≤5 连接（超出 42901）；心跳 ping 30s；
 * 首帧 retry:5000；Last-Event-ID 补发 ≤100 条（超出窗口由客户端重拉）。
 */
@Component
public class NotificationSseRegistry {

  static final String EVENT_CREATED = "notification.created";
  private static final int MAX_CONNECTIONS_PER_ACCOUNT = 5;
  private static final int REPLAY_LIMIT = 100;
  private static final long INIT_DELAY_MILLIS = 200;

  private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
  /** 完成首帧（retry + 补发）的连接，之前不收业务/心跳事件，保证 retry 首帧次序。 */
  private final Set<SseEmitter> initialized = ConcurrentHashMap.newKeySet();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final NotificationRepository repository;
  private final JsonMapper jsonMapper;
  private final long heartbeatMillis;

  public NotificationSseRegistry(
      NotificationRepository repository,
      JsonMapper jsonMapper,
      @Value("${zentao.notification.sse.heartbeat:30000}") long heartbeatMillis) {
    this.repository = repository;
    this.jsonMapper = jsonMapper;
    this.heartbeatMillis = heartbeatMillis;
  }

  /** 订阅：容器初始化 emitter 后再发首帧（retry:5000 + Last-Event-ID 补发）。 */
  public SseEmitter subscribe(String account, Long lastEventId) {
    CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(account, key -> new CopyOnWriteArrayList<>());
    SseEmitter emitter = new SseEmitter(0L);
    synchronized (list) {
      if (list.size() >= MAX_CONNECTIONS_PER_ACCOUNT) {
        throw ApiException.rateLimited("SSE 连接数超上限。");
      }
      list.add(emitter);
    }
    Runnable cleanup = () -> {
      list.remove(emitter);
      initialized.remove(emitter);
    };
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(e -> cleanup.run());

    scheduler.schedule(() -> {
      try {
        emitter.send(SseEmitter.event().reconnectTime(5000));
        if (lastEventId != null) {
          for (NotificationPO po : repository.findAfter(account, lastEventId, REPLAY_LIMIT)) {
            sendCreated(emitter, NotificationViews.toView(po));
          }
        }
        initialized.add(emitter);
      } catch (IOException | IllegalStateException e) {
        cleanup.run();
      }
    }, INIT_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    return emitter;
  }

  void sendCreated(NotificationView view) {
    CopyOnWriteArrayList<SseEmitter> list = emitters.get(view.recipient());
    if (list == null) {
      return;
    }
    for (SseEmitter emitter : list) {
      if (!initialized.contains(emitter)) {
        continue;
      }
      try {
        sendCreated(emitter, view);
      } catch (Exception e) {
        list.remove(emitter);
      }
    }
  }

  private void sendCreated(SseEmitter emitter, NotificationView view) throws IOException {
    emitter.send(SseEmitter.event()
        .id(String.valueOf(view.id()))
        .name(EVENT_CREATED)
        .data(jsonMapper.writeValueAsString(view)));
  }

  /** 心跳：事件名 ping（platform 卡 §5.1），防代理空闲断连。 */
  @Scheduled(fixedDelayString = "${zentao.notification.sse.heartbeat:30000}")
  public void ping() {
    for (CopyOnWriteArrayList<SseEmitter> list : emitters.values()) {
      for (SseEmitter emitter : list) {
        if (!initialized.contains(emitter)) {
          continue;
        }
        try {
          emitter.send(SseEmitter.event().name("ping"));
        } catch (Exception e) {
          list.remove(emitter);
        }
      }
    }
  }

  @PreDestroy
  void shutdown() {
    scheduler.shutdownNow();
  }
}
