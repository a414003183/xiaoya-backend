package net.zentao.platform.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.yaml.snakeyaml.Yaml;

/**
 * 会话加固（06 对齐 A7-2）：cookie Secure 随 profile 开（prod）/关（dev），
 * 会话绝对过期上限 30d——滑动续期不得突破，超限走与过期相同的删行 + 40101。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
    "zentao.session.secure-cookie=true")
@TestPropertySource(properties = "zentao.session.secure-cookie=true")
class SessionHardeningTest extends ApiTestSupport {

  private static final Duration MAX_LIFETIME = Duration.ofDays(30);

  @Autowired
  DataSource dataSource;

  @Test
  @DisplayName("prod 开关打开时登录 cookie 带 Secure（其余属性不变）")
  void loginCookieCarriesSecure() throws Exception {
    HttpResponse<String> response =
        send("POST", "/api/v1/session", "{\"account\":\"admin\",\"password\":\"admin123\"}", null);
    assertEquals(200, response.statusCode(), response.body());
    String cookie = response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow();
    assertTrue(cookie.contains("Secure"), cookie);
    assertTrue(cookie.contains("HttpOnly"), cookie);
    assertTrue(cookie.contains("SameSite=Lax"), cookie);
  }

  @Test
  @DisplayName("prod profile 声明 secure-cookie: true（测试上下文跑不了 prod，只能校验配置）")
  void prodProfileEnablesSecureCookie() throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/application-prod.yml")) {
      Map<?, ?> root = new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      Map<?, ?> session = (Map<?, ?>) ((Map<?, ?>) root.get("zentao")).get("session");
      assertEquals(true, session.get("secure-cookie"), String.valueOf(root));
    }
  }

  @Test
  @DisplayName("创建于 31 天前的会话：绝对过期拦截 → 40101 且删行（即使 expiresAt 仍未来）")
  void sessionBeyondAbsoluteCapRejected() throws Exception {
    Instant now = Instant.now();
    String id = insertSession(now.minus(Duration.ofDays(31)), now.plus(Duration.ofDays(7)), now.minus(Duration.ofHours(2)));

    HttpResponse<String> response = send("GET", "/api/v1/me", null, "ZT_SESSION=" + id);
    assertEquals(401, response.statusCode(), response.body());
    assertTrue(response.body().contains("40101"), response.body());
    assertEquals(0, sessionRowCount(id), "超绝对上限的会话行应被删除");
  }

  @Test
  @DisplayName("创建于 29 天前：放行，且续期截断在 createdAt+30d 不越界")
  void renewalStopsAtAbsoluteCap() throws Exception {
    Instant now = Instant.now();
    Instant createdAt = now.minus(Duration.ofDays(29));
    String id = insertSession(createdAt, now.plus(Duration.ofDays(7)), now.minus(Duration.ofDays(2)));

    HttpResponse<String> response = send("GET", "/api/v1/me", null, "ZT_SESSION=" + id);
    assertEquals(200, response.statusCode(), response.body());

    Instant expiresAt = sessionExpiresAt(id);
    assertFalse(expiresAt.isAfter(createdAt.plus(MAX_LIFETIME).plusSeconds(1)),
        "续期不得突破绝对上限: " + expiresAt + " vs " + createdAt.plus(MAX_LIFETIME));
    assertTrue(expiresAt.isAfter(now), "会话仍在有效期内: " + expiresAt);
  }

  /** 直接落一行会话（绕过登录），用于构造绝对过期边界会话。 */
  private String insertSession(Instant createdAt, Instant expiresAt, Instant lastSeenAt) throws Exception {
    String id = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    try (var connection = dataSource.getConnection();
        var insert = connection.prepareStatement(
            "INSERT INTO session (id, account_id, account, created_at, expires_at, last_seen_at)"
                + " SELECT ?, id, account, ?, ?, ? FROM account WHERE account = 'admin'")) {
      insert.setString(1, id);
      insert.setTimestamp(2, Timestamp.from(createdAt));
      insert.setTimestamp(3, Timestamp.from(expiresAt));
      insert.setTimestamp(4, Timestamp.from(lastSeenAt));
      assertEquals(1, insert.executeUpdate());
    }
    return id;
  }

  private int sessionRowCount(String id) throws Exception {
    try (var connection = dataSource.getConnection();
        var query = connection.prepareStatement("SELECT COUNT(*) FROM session WHERE id = ?")) {
      query.setString(1, id);
      var resultSet = query.executeQuery();
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  private Instant sessionExpiresAt(String id) throws Exception {
    try (var connection = dataSource.getConnection();
        var query = connection.prepareStatement("SELECT expires_at FROM session WHERE id = ?")) {
      query.setString(1, id);
      var resultSet = query.executeQuery();
      assertTrue(resultSet.next(), "会话行仍应存在");
      return resultSet.getTimestamp(1).toInstant();
    }
  }
}
