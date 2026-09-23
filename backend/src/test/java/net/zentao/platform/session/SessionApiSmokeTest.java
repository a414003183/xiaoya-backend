package net.zentao.platform.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** 登录 → cookie → /me → 登出 垂直链路冒烟（真 HTTP + 真库断言，含 TraceId/Csrf 过滤器与 H2+Flyway）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionApiSmokeTest {

  @Value("${local.server.port}")
  int port;

  @Autowired
  DataSource dataSource;

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  @DisplayName("login→cookie→getMe→logout 全链路")
  void loginMeLogoutFlow() throws Exception {
    // CSRF：写请求缺 X-Requested-With: fetch → 40301
    HttpResponse<String> noCsrf =
        post("/api/v1/session", "{\"account\":\"admin\",\"password\":\"admin123\"}", Map.of());
    assertEquals(403, noCsrf.statusCode());
    assertTrue(noCsrf.body().contains("40301"), noCsrf.body());

    // 错误凭据 → 40101 且不种 cookie
    HttpResponse<String> wrong =
        post("/api/v1/session", "{\"account\":\"admin\",\"password\":\"bad\"}", Map.of("X-Requested-With", "fetch"));
    assertEquals(401, wrong.statusCode(), wrong.body());
    assertTrue(wrong.body().contains("40101"), wrong.body());
    assertTrue(cookieValues(wrong).isEmpty(), "登录失败不得种 cookie");

    // 正确登录 → 200 + HttpOnly SameSite=Lax cookie
    HttpResponse<String> login =
        post("/api/v1/session", "{\"account\":\"admin\",\"password\":\"admin123\"}", Map.of("X-Requested-With", "fetch"));
    assertEquals(200, login.statusCode(), login.body());
    assertTrue(login.body().contains("\"account\":\"admin\""), login.body());
    String sessionCookie = cookieValues(login).stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow(() -> new AssertionError("登录响应缺少 ZT_SESSION: " + cookieValues(login)));
    assertTrue(sessionCookie.contains("HttpOnly"), sessionCookie);
    assertTrue(sessionCookie.contains("SameSite=Lax"), sessionCookie);
    // dev 默认 profile 走 http，Secure 必须关（prod 开启见 SessionHardeningTest）
    assertFalse(sessionCookie.contains("Secure"), sessionCookie);
    String token = sessionCookie.split(";", 2)[0];
    String tokenId = token.substring("ZT_SESSION=".length());
    // T51 SEC-03：库里存的是 token 的 sha256——按明文查必须 0 行，按摘要查才是那条会话
    assertEquals(0, sessionRowCount(tokenId), "明文 token 不得落库");
    assertEquals(1, sessionRowCount(SessionTokenHash.of(tokenId)), "登录后 session 表应有该摘要行");
    assertTrue(meAccountRowExists(), "V3 种子应已插入 admin 账号");

    // 未登录 → 40101
    HttpResponse<String> anonymous = get("/api/v1/me", Map.of());
    assertEquals(401, anonymous.statusCode());
    assertTrue(anonymous.body().contains("40101"), anonymous.body());

    // 带 cookie 的 /me → 真库账号 + 超管 privileges（编目全集）；dictionaries 已移除（2026-09-19）
    HttpResponse<String> me =
        get("/api/v1/me", Map.of("Cookie", token, "X-Requested-With", "fetch"));
    assertEquals(200, me.statusCode(), me.body());
    assertTrue(me.body().contains("\"account\":\"admin\""), me.body());
    assertTrue(me.body().contains("\"file-upload\""), me.body());
    assertFalse(me.body().contains("dictionaries"), me.body());

    // 登出 → {data:null}；旧 cookie 立即失效（行已物理删除）
    HttpResponse<String> logout =
        delete("/api/v1/session", Map.of("Cookie", token, "X-Requested-With", "fetch"));
    assertEquals(200, logout.statusCode(), logout.body());
    assertTrue(logout.body().contains("\"data\":null"), logout.body());
    assertEquals(0, sessionRowCount(SessionTokenHash.of(tokenId)), "登出后 session 行应已删除");
    assertEquals(401, get("/api/v1/me", Map.of("Cookie", token)).statusCode());

    // traceId 响应头存在
    assertFalse(get("/api/v1/me", Map.of()).headers().firstValue("X-Trace-Id").orElse("").isBlank());
  }

  @Test
  @DisplayName("登录体校验失败 → 42201 带字段级错误")
  void loginValidation() throws Exception {
    HttpResponse<String> response =
        post("/api/v1/session", "{\"account\":\"\",\"password\":\"\"}", Map.of("X-Requested-With", "fetch"));
    assertEquals(422, response.statusCode(), response.body());
    assertTrue(response.body().contains("42201"), response.body());
    assertTrue(response.body().contains("\"fields\""), response.body());
  }

  private HttpResponse<String> get(String path, Map<String, String> headers) throws Exception {
    return send("GET", path, null, headers);
  }

  private HttpResponse<String> post(String path, String json, Map<String, String> headers) throws Exception {
    return send("POST", path, json, headers);
  }

  private HttpResponse<String> delete(String path, Map<String, String> headers) throws Exception {
    return send("DELETE", path, null, headers);
  }

  private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers)
      throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    builder.header("Content-Type", "application/json");
    headers.forEach(builder::header);
    return http.send(
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static List<String> cookieValues(HttpResponse<String> response) {
    return response.headers().allValues("set-cookie");
  }

  private int sessionRowCount(String token) throws Exception {
    try (var connection = dataSource.getConnection();
        var query = connection.prepareStatement("SELECT COUNT(*) FROM session WHERE id = ?")) {
      query.setString(1, token);
      var resultSet = query.executeQuery();
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  private boolean meAccountRowExists() throws Exception {
    try (var connection = dataSource.getConnection();
        var query = connection.prepareStatement("SELECT 1 FROM account WHERE account = 'admin'")) {
      return query.executeQuery().next();
    }
  }
}
