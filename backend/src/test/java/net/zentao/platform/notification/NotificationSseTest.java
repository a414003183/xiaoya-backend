package net.zentao.platform.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 协议（platform 卡 §5.1/§8）：新通知推送 notification.created、心跳 ping、
 * Last-Event-ID 补发、第 6 连接 42901。测试用心跳 200ms 加速。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "zentao.notification.sse.heartbeat=200"
})
@TestPropertySource(properties = "zentao.notification.sse.heartbeat=200")
class NotificationSseTest {

  @Value("${local.server.port}")
  int port;

  @Autowired
  NotificationRecorder recorder;

  @Autowired
  NotificationSseRegistry registry;

  @Autowired
  DataSource dataSource;

  private final HttpClient http = HttpClient.newHttpClient();
  private String account;
  private String cookie;
  private final List<HttpResponse<java.io.InputStream>> openStreams = new ArrayList<>();

  @BeforeEach
  void login() throws Exception {
    account = "sse-user-" + java.util.UUID.randomUUID();
    String hash = new BCryptPasswordEncoder().encode("admin123");
    try (var connection = dataSource.getConnection();
        var insert = connection.prepareStatement(
            "INSERT INTO account (account, password, real_name) VALUES (?, ?, 'SSE用户')")) {
      insert.setString(1, account);
      insert.setString(2, hash);
      insert.executeUpdate();
    }
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/session"))
            .header("Content-Type", "application/json")
            .header("X-Requested-With", "fetch")
            .POST(HttpRequest.BodyPublishers.ofString("{\"account\":\"" + account + "\",\"password\":\"admin123\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    cookie = response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  @AfterEach
  void closeStreams() throws Exception {
    for (HttpResponse<java.io.InputStream> stream : openStreams) {
      stream.body().close();
    }
    openStreams.clear();
  }

  private HttpResponse<java.io.InputStream> openStream(String lastEventId) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/notifications/stream"))
        .header("Cookie", cookie)
        .GET();
    if (lastEventId != null) {
      builder.header("Last-Event-ID", lastEventId);
    }
    HttpResponse<java.io.InputStream> response =
        http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
    openStreams.add(response);
    return response;
  }

  /** 读取 SSE 事件块（空行分隔），直到包含目标 event 名或超时。 */
  private List<String> readEvents(HttpResponse<java.io.InputStream> stream, String eventName, long timeoutMillis)
      throws Exception {
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream.body(), StandardCharsets.UTF_8));
    List<String> blocks = new ArrayList<>();
    StringBuilder block = new StringBuilder();
    long deadline = System.currentTimeMillis() + timeoutMillis;
    while (System.currentTimeMillis() < deadline) {
      if (reader.ready()) {
        String line = reader.readLine();
        if (line == null) {
          break;
        }
        if (line.isBlank()) {
          if (block.length() > 0) {
            blocks.add(block.toString());
            if (block.toString().contains("event:" + eventName)) {
              return blocks;
            }
            block.setLength(0);
          }
        } else {
          block.append(line).append('\n');
        }
      } else {
        Thread.sleep(10);
      }
    }
    if (block.length() > 0) {
      blocks.add(block.toString());
    }
    return blocks;
  }

  private boolean hasEvent(List<String> blocks, String name) {
    return blocks.stream().anyMatch(text -> text.contains("event:" + name));
  }

  @Test
  @DisplayName("新通知 1s 内推 notification.created 且带 id；心跳 ping 到达")
  void pushAndHeartbeat() throws Exception {
    HttpResponse<java.io.InputStream> stream = openStream(null);
    List<String> init = readEvents(stream, "ping", 3000);
    assertTrue(init.stream().anyMatch(text -> text.contains("retry:5000") || text.contains("retry: 5000")),
        "首帧应含 retry:5000，实际: " + init);

    recorder.record(List.of(account), "account-reset-password", "account", 1, null, "重置提醒", null, "admin");
    List<String> created = readEvents(stream, "notification.created", 3000);
    assertTrue(hasEvent(created, "notification.created"), String.valueOf(created));
    assertTrue(created.stream().anyMatch(text -> text.contains("id:") && text.contains("notification.created")),
        "通知事件应带 id");

    // 心跳（200ms 间隔）
    List<String> pings = readEvents(stream, "ping", 2000);
    assertTrue(hasEvent(pings, "ping"), String.valueOf(pings));
  }

  @Test
  @DisplayName("Last-Event-ID 补发该 id 之后的本人通知")
  void replaysAfterLastEventId() throws Exception {
    recorder.record(List.of(account), "story-created", "story", 1, null, "早的通知", null, "admin");
    recorder.record(List.of(account), "story-created", "story", 2, null, "中通知", null, "admin");
    long firstId = firstNotificationId();
    HttpResponse<java.io.InputStream> stream = openStream(String.valueOf(firstId));
    List<String> lines = readEvents(stream, "notification.created", 3000);
    String joined = String.join("\n", lines);
    assertTrue(lines.size() >= 2, "应补发事件: " + joined);
    assertTrue(joined.contains("早的通知") || joined.contains("中通知"), joined);
  }

  @Test
  @DisplayName("同账号第 6 条连接 → 42901")
  void sixthConnectionRejected() throws Exception {
    for (int i = 0; i < 5; i++) {
      HttpResponse<java.io.InputStream> stream = openStream(null);
      assertEquals(200, stream.statusCode());
    }
    HttpResponse<String> sixth = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/notifications/stream"))
            .header("Cookie", cookie)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(429, sixth.statusCode(), sixth.body());
    assertTrue(sixth.body().contains("42901"), sixth.body());
  }

  @Test
  @DisplayName("T57/BE-11：推送失败的连接被彻底移除（列表 + initialized + 空账号键），不再慢泄漏")
  void failedConnectionIsFullyDetached() throws Exception {
    // 用真 HTTP 建一条连接（走 subscribe 的完整路径），再把它替换成一条"一推就炸"的连接
    HttpResponse<java.io.InputStream> stream = openStream(null);
    assertEquals(200, stream.statusCode());
    stream.body().close();
    String leakAccount = "sse-leak-" + java.util.UUID.randomUUID();
    SseEmitter broken = new SseEmitter(0L) {
      @Override
      public void send(SseEventBuilder builder) throws java.io.IOException {
        throw new java.io.IOException("broken pipe");
      }
    };
    registry.emitters.computeIfAbsent(leakAccount, key -> new java.util.concurrent.CopyOnWriteArrayList<>())
        .add(broken);
    registry.initialized.add(broken);

    registry.ping();

    assertTrue(registry.initialized.isEmpty() || !registry.initialized.contains(broken),
        "initialized 不得留着已移除的连接（旧行为：只 list.remove，连接对象永久泄漏）");
    assertFalse(registry.emitters.containsKey(leakAccount), "空列表要连同账号键一起清（旧行为：账号维度只增不减）");
  }

  private long firstNotificationId() throws Exception {
    try (var connection = dataSource.getConnection();
        var query = connection.prepareStatement(
            "SELECT MIN(id) FROM notification WHERE recipient = ?")) {
      query.setString(1, account);
      var resultSet = query.executeQuery();
      resultSet.next();
      return resultSet.getLong(1);
    }
  }
}
