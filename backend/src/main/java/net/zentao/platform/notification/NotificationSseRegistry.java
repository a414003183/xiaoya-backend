package net.zentao.platform.notification;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

/**
 * SSE 连接注册表（platform 卡 §5.1）：每账号 ≤5 连接（超出 42901）；心跳 ping 30s；
 * 首帧 retry:5000；Last-Event-ID 补发 ≤100 条（超出窗口由客户端重拉）。
 *
 * <p>移除路径只有 {@link #detach} 一个出口（T57/BE-11）：列表、`initialized`、空账号键必须一起清——
 * 旧代码在推送/心跳的失败分支只 `list.remove`，留下的 `initialized` 强引用即连接对象的慢泄漏。
 * 心跳的配置键与缺省值只有一处定义（T57/BE-12）：`@Scheduled` 从常量拼出 `fixedDelayString`，yml 给可调值。
 */
@Component
public class NotificationSseRegistry {

  static final String EVENT_CREATED = "notification.created";
  /** 心跳间隔配置键；yml 里给可调值，测试用 properties 覆盖加速。 */
  static final String HEARTBEAT_PROPERTY = "zentao.notification.sse.heartbeat";
  /** 缺省心跳（ms）：只在代码里定义一次（BE-12），注解里用常量拼接。 */
  static final String HEARTBEAT_DEFAULT_MILLIS = "30000";
  private static final int MAX_CONNECTIONS_PER_ACCOUNT = 5;
  private static final int REPLAY_LIMIT = 100;
  private static final long INIT_DELAY_MILLIS = 200;

  final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
  /** 完成首帧（retry + 补发）的连接，之前不收业务/心跳事件，保证 retry 首帧次序。 */
  final Set<SseEmitter> initialized = ConcurrentHashMap.newKeySet();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final NotificationRepository repository;
  private final JsonMapper jsonMapper;

  public NotificationSseRegistry(NotificationRepository repository, JsonMapper jsonMapper) {
    this.repository = repository;
    this.jsonMapper = jsonMapper;
  }

  /** 订阅：容器初始化 emitter 后再发首帧（retry:5000 + Last-Event-ID 补发）。 */
  public SseEmitter subscribe(String account, Long lastEventId) {
    CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(account, key -> new CopyOnWriteArrayList<>());
    SseEmitter emitter = new SseEmitter(0L);
    synchronized (list) {
      if (list.size() >= MAX_CONNECTIONS_PER_ACCOUNT) {
        throw ApiException.keyed(ErrorCode.RATE_LIMITED, "notification.sse.tooManyConnections");
      }
      list.add(emitter);
    }
    emitter.onCompletion(() -> detach(account, list, emitter));
    emitter.onTimeout(() -> detach(account, list, emitter));
    emitter.onError(e -> detach(account, list, emitter));

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
        detach(account, list, emitter);
      }
    }, INIT_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    return emitter;
  }

  /** 移除的唯一出口：列表 + initialized + 空账号键（键用带值 remove，避免误删并发新建的同名列表）。 */
  private void detach(String account, CopyOnWriteArrayList<SseEmitter> list, SseEmitter emitter) {
    list.remove(emitter);
    initialized.remove(emitter);
    if (list.isEmpty()) {
      emitters.remove(account, list);
    }
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
        detach(view.recipient(), list, emitter);
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
  @Scheduled(fixedDelayString = "${" + HEARTBEAT_PROPERTY + ":" + HEARTBEAT_DEFAULT_MILLIS + "}")
  public void ping() {
    // 迭代 entrySet：失败连接要连着账号键一起清（BE-11）
    for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : emitters.entrySet()) {
      for (SseEmitter emitter : entry.getValue()) {
        if (!initialized.contains(emitter)) {
          continue;
        }
        try {
          emitter.send(SseEmitter.event().name("ping"));
        } catch (Exception e) {
          detach(entry.getKey(), entry.getValue(), emitter);
        }
      }
    }
  }

  @PreDestroy
  void shutdown() {
    scheduler.shutdownNow();
  }
}
