package net.zentao.platform.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 登录真实现（T-1）：cookie 属性 / 错误凭据 / 限流 42901 / 停用账号 40101（platform 卡 §8）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginHandlerTest {

  @Autowired
  DataSource dataSource;

  @org.springframework.beans.factory.annotation.Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  @DisplayName("正确凭据 → 200 + HttpOnly SameSite=Lax cookie，会话落库")
  void loginSeedsCookieAndSessionRow() throws Exception {
    HttpResponse<String> response = post("/api/v1/session", "{\"account\":\"admin\",\"password\":\"admin123\"}");
    assertEquals(200, response.statusCode(), response.body());
    String cookie = cookieValues(response).stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow(() -> new AssertionError("缺少 ZT_SESSION"));
    assertTrue(cookie.contains("HttpOnly"), cookie);
    assertTrue(cookie.contains("SameSite=Lax"), cookie);
    String token = cookie.split(";", 2)[0].substring("ZT_SESSION=".length());
    try (var connection = dataSource.getConnection();
        var query = connection.prepareStatement("SELECT account_id FROM session WHERE id = ?")) {
      query.setString(1, token);
      var resultSet = query.executeQuery();
      Assertions.assertTrue(resultSet.next(), "session 表应存在该会话行");
    }
  }

  @Test
  @DisplayName("错误凭据 → 40101 且不种 cookie")
  void wrongPasswordRejected() throws Exception {
    HttpResponse<String> response = post("/api/v1/session", "{\"account\":\"admin\",\"password\":\"bad\"}");
    assertEquals(401, response.statusCode(), response.body());
    assertTrue(response.body().contains("40101"), response.body());
    assertTrue(cookieValues(response).isEmpty(), "登录失败不得种 cookie");
  }

  @Test
  @DisplayName("同账号 1 分钟失败 ≥10 次 → 42901")
  void rateLimitedAfterTenFailures() throws Exception {
    int lastStatus = 0;
    String lastBody = "";
    for (int i = 0; i < 11; i++) {
      HttpResponse<String> response =
          post("/api/v1/session", "{\"account\":\"ratelimit-user\",\"password\":\"nope\"}");
      lastStatus = response.statusCode();
      lastBody = response.body();
    }
    assertEquals(429, lastStatus, lastBody);
    assertTrue(lastBody.contains("42901"), lastBody);
  }

  @Test
  @DisplayName("停用账号 → 40101")
  void disabledAccountRejected() throws Exception {
    String hash = new BCryptPasswordEncoder().encode("admin123");
    try (var connection = dataSource.getConnection();
        var insert = connection.prepareStatement(
            "INSERT INTO account (account, password, real_name, status) VALUES ('login-disabled', ?, '停用账号', 'disabled')")) {
      insert.setString(1, hash);
      insert.executeUpdate();
    }
    HttpResponse<String> response = post("/api/v1/session", "{\"account\":\"login-disabled\",\"password\":\"admin123\"}");
    assertEquals(401, response.statusCode(), response.body());
    assertTrue(response.body().contains("40101"), response.body());
  }

  private HttpResponse<String> post(String path, String json) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("X-Requested-With", "fetch")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static List<String> cookieValues(HttpResponse<String> response) {
    return response.headers().allValues("set-cookie");
  }
}
