package net.zentao.platform.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 通知 API（platform 卡 §8）：落行/unread-count 准确/重复 mark-read 幂等/他人通知 40302。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationApiTest {

  @Value("${local.server.port}")
  int port;

  @Autowired
  NotificationRecorder recorder;

  @Autowired
  net.zentao.platform.meta.SettingRepository settingRepository;

  @Autowired
  DataSource dataSource;

  private final HttpClient http = HttpClient.newHttpClient();
  private String account;
  private String cookie;

  @BeforeEach
  void loginAsPlainUser() throws Exception {
    account = "notify-user-" + java.util.UUID.randomUUID();
    String hash = new BCryptPasswordEncoder().encode("admin123");
    try (var connection = dataSource.getConnection();
        var insert = connection.prepareStatement(
            "INSERT INTO account (account, password, real_name) VALUES (?, ?, '通知用户')")) {
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

  private HttpResponse<String> send(String method, String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Cookie", cookie)
            .header("X-Requested-With", "fetch")
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  @DisplayName("通知落行逐接收人一行；列表恒只见本人；unread-count 准确；mark-read 幂等")
  void notificationFlow() throws Exception {
    recorder.record(List.of(account, "someone-else"), "bug-resolved", "bug", 7, null, "Bug 已解决", null, "admin");

    HttpResponse<String> list = send("GET", "/api/v1/notifications?filters%5BreadAt%5D=%40null");
    assertEquals(200, list.statusCode(), list.body());
    assertTrue(list.body().contains("Bug 已解决"), list.body());
    assertTrue(list.body().contains("\"total\":1"), "应只见本人 1 条: " + list.body());

    HttpResponse<String> unread = send("GET", "/api/v1/notifications/unread-count");
    assertTrue(unread.body().contains("\"count\":1"), unread.body());

    long id = latestId(account);
    HttpResponse<String> read1 = send("POST", "/api/v1/notifications/" + id + "/read");
    assertEquals(200, read1.statusCode(), read1.body());
    assertTrue(read1.body().contains("\"readAt\""), read1.body());

    HttpResponse<String> read2 = send("POST", "/api/v1/notifications/" + id + "/read");
    assertEquals(200, read2.statusCode(), "重复标记应幂等 200");

    HttpResponse<String> unreadAfter = send("GET", "/api/v1/notifications/unread-count");
    assertTrue(unreadAfter.body().contains("\"count\":0"), unreadAfter.body());

    // 未读过滤不再出现
    HttpResponse<String> unreadList = send("GET", "/api/v1/notifications?filters%5BreadAt%5D=%40null");
    assertTrue(unreadList.body().contains("\"total\":0"), unreadList.body());
  }

  @Test
  @DisplayName("标记他人通知 → 40302；type=空接收人不落行")
  void othersNotificationForbidden() throws Exception {
    recorder.record(List.of("another-user"), "story-changed", "story", 3, null, "他人的通知", null, "admin");
    long otherId = latestId("another-user");
    HttpResponse<String> forbidden = send("POST", "/api/v1/notifications/" + otherId + "/read");
    assertEquals(403, forbidden.statusCode(), forbidden.body());
    assertTrue(forbidden.body().contains("40302"), forbidden.body());

    recorder.record(List.of(), "story-changed", "story", 3, null, "空接收人", null, "admin");
    HttpResponse<String> notifList = send("GET", "/api/v1/notifications");
    assertTrue(notifList.body().contains("\"total\":0"), "空接收人不落任何行: " + notifList.body());
    HttpResponse<String> list = send("GET", "/api/v1/notifications");
    assertTrue(list.body().contains("\"total\":0"), "空接收人不落任何行: " + list.body());
  }

  @Test
  @DisplayName("B-PLT-02：个人级 notify.<type>=false 屏蔽落行（Boolean 与字符串两种形态）；true/未设置照常")
  void personalNotifySettingMutesRecipient() throws Exception {
    String mutedByBoolean = "mute-bool-" + java.util.UUID.randomUUID();
    String mutedByString = "mute-str-" + java.util.UUID.randomUUID();
    String settingTrue = "mute-true-" + java.util.UUID.randomUUID();
    String noSetting = "mute-none-" + java.util.UUID.randomUUID();
    settingRepository.upsert(mutedByBoolean, "notify", "bug-resolved", "false");
    settingRepository.upsert(mutedByString, "notify", "bug-resolved", "\"false\"");
    settingRepository.upsert(settingTrue, "notify", "bug-resolved", "true");

    recorder.record(List.of(mutedByBoolean, mutedByString, settingTrue, noSetting), "bug-resolved", "bug", 9, null,
        "屏蔽口径", null, "admin");

    assertEquals(0, countRows(mutedByBoolean), "Boolean false 应屏蔽");
    assertEquals(0, countRows(mutedByString), "字符串 \"false\" 应屏蔽");
    assertEquals(1, countRows(settingTrue), "true 不屏蔽");
    assertEquals(1, countRows(noSetting), "未设置不屏蔽");

    // 屏蔽账号的通知列表不含该标题（无行可查）
    HttpResponse<String> list = send("GET", "/api/v1/notifications");
    assertEquals(200, list.statusCode(), list.body());
  }

  @Test
  @DisplayName("B-PLT-02：设置只按 type 生效——屏蔽 bug-resolved 的账号仍收 story-changed")
  void settingIsPerType() throws Exception {
    settingRepository.upsert(account, "notify", "bug-resolved", "false");
    recorder.record(List.of(account), "story-changed", "story", 4, null, "其他类型不屏蔽", null, "admin");
    assertEquals(1, countRows(account), "只屏蔽同 type");
  }

  private int countRows(String recipient) throws Exception {
    try (var connection = dataSource.getConnection();
        var query = connection.prepareStatement("SELECT COUNT(*) FROM notification WHERE recipient = ?")) {
      query.setString(1, recipient);
      var resultSet = query.executeQuery();
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  private long latestId(String recipient) throws Exception {
    try (var connection = dataSource.getConnection();
        var query = connection.prepareStatement("SELECT MAX(id) FROM notification WHERE recipient = ?")) {
      query.setString(1, recipient);
      var resultSet = query.executeQuery();
      resultSet.next();
      return resultSet.getLong(1);
    }
  }
}
