package net.zentao.platform.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.util.Map;
import javax.sql.DataSource;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 会话加固（T51）：凭据不落库（SEC-03）、改密/重置踢会话（SEC-04）、并发会话上限（SEC-18）、
 * 默认不信 X-Forwarded-*（SEC-10 部分——开关打开的对照见 {@code SessionForwardedIpTest}）。
 *
 * <p>真 HTTP + 真库：断言落在 `session` 表与接口回显上，因为本卡要守的性质是"库里有什么、谁还能用"。
 * 并发上限调成 2（默认 5）避免用例为撞上限而登录六次。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "zentao.session.max-per-account=2")
class SessionSecurityTest extends ApiTestSupport {

  @Autowired
  DataSource dataSource;

  @Test
  @DisplayName("SEC-03 凭据不落库：按明文 token 查 0 行、按 sha256 查 1 行，且该 cookie 仍可用")
  void plaintextTokenNeverLandsInDatabase() throws Exception {
    String cookie = login("admin", "admin123");
    String token = tokenOf(cookie);

    assertEquals(0, count("SELECT COUNT(*) FROM session WHERE id = ?", token), "库里不得出现明文 token 的行");
    assertEquals(1, count("SELECT COUNT(*) FROM session WHERE id = ?", SessionTokenHash.of(token)),
        "行主键应是 token 的 sha256");
    assertEquals(200, send("GET", "/api/v1/me", null, cookie).statusCode(), "换成摘要键值后解析链仍通");
  }

  @Test
  @DisplayName("SEC-03 在线用户对外 id 就是行主键（摘要），不是 cookie 值、也不含凭据")
  void onlineUserExposesDigestNotCredential() throws Exception {
    String cookie = login("admin", "admin123");
    String token = tokenOf(cookie);
    var rows = data(send("GET", "/api/v1/online-users?filters%5Baccount%5D=admin&limit=200", null, cookie));

    String selfId = null;
    for (var row : rows.get("items")) {
      if (row.get("current").asBoolean()) {
        selfId = row.get("id").asText();
      }
    }
    assertEquals(SessionTokenHash.of(token), selfId, "自己那条的对外 id = token 的 sha256（即行主键）");
    assertNotEquals(token, selfId, "对外 id 不得是 cookie 值");
    assertFalse(rows.toString().contains(token), "凭据不得出现在响应里");
  }

  @Test
  @DisplayName("SEC-04 本人改密：当前会话保留，其他设备会话全退")
  void passwordChangeKeepsCurrentKicksOthers() throws Exception {
    String adminCookie = login("admin", "admin123");
    String account = "t51-pwd-" + suffix();
    long accountId = dataId(send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"t51\"}", adminCookie));
    String first = login(account, "secret123");
    String second = login(account, "secret123");
    assertEquals(200, send("GET", "/api/v1/me", null, first).statusCode(), "两条会话登录后都可用");

    var changed = send("POST", "/api/v1/accounts/" + accountId + "/password",
        "{\"oldPassword\":\"secret123\",\"newPassword\":\"secret456\"}", second);
    assertEquals(200, changed.statusCode(), changed.body());

    assertEquals(200, send("GET", "/api/v1/me", null, second).statusCode(), "改密者自己的会话保留");
    HttpResponse<String> kicked = send("GET", "/api/v1/me", null, first);
    assertEquals(401, kicked.statusCode(), "其他设备的会话应被踢：" + kicked.body());
    assertTrue(kicked.body().contains("40101"), kicked.body());
  }

  @Test
  @DisplayName("SEC-04 管理员重置口令：目标账号全部会话失效（含其当前会话）")
  void adminResetKicksEverySession() throws Exception {
    String adminCookie = login("admin", "admin123");
    String account = "t51-reset-" + suffix();
    long accountId = dataId(send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"t51\"}", adminCookie));
    String cookie = login(account, "secret123");

    var reset = send("POST", "/api/v1/accounts/" + accountId + "/reset-password",
        "{\"newPassword\":\"reset789\"}", adminCookie);
    assertEquals(200, reset.statusCode(), reset.body());

    HttpResponse<String> afterReset = send("GET", "/api/v1/me", null, cookie);
    assertEquals(401, afterReset.statusCode(), "重置后旧会话不得继续可用：" + afterReset.body());
    assertEquals(200, send("GET", "/api/v1/me", null, login(account, "reset789")).statusCode(), "新口令可登录");
  }

  @Test
  @DisplayName("SEC-18 并发上限（max=2）：第 3 次登录踢掉最旧的那条，库里恰留上限条")
  void concurrentSessionsAreCapped() throws Exception {
    String adminCookie = login("admin", "admin123");
    String account = "t51-max-" + suffix();
    dataId(send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"t51\"}", adminCookie));

    String first = login(account, "secret123");
    String second = login(account, "secret123");
    String third = login(account, "secret123");

    assertEquals(2, count("SELECT COUNT(*) FROM session WHERE account = ?", account), "该账号在库里恰好留下上限条会话");
    assertEquals(401, send("GET", "/api/v1/me", null, first).statusCode(), "最旧的被踢");
    assertEquals(200, send("GET", "/api/v1/me", null, second).statusCode(), "次新的还在");
    assertEquals(200, send("GET", "/api/v1/me", null, third).statusCode(), "最新的一条可用");
  }

  @Test
  @DisplayName("SEC-10 默认不信 X-Forwarded-For：伪造的客户端 IP 不进库（防污染审计）")
  void forwardedHeaderIgnoredByDefault() throws Exception {
    HttpResponse<String> login = postWithForwardedFor("203.0.113.7");
    assertEquals(200, login.statusCode(), login.body());

    String recorded = ipOf(SessionTokenHash.of(tokenOf(cookieOf(login))));
    assertNotEquals("203.0.113.7", recorded, "默认策略下不得采信客户端自带的转发头");
    assertTrue(recorded.startsWith("127.") || recorded.contains("0:0:0:0:0:0:0:1"),
        "应记 TCP 对端（回环）地址：" + recorded);
  }

  // ── 夹具 ──

  private HttpResponse<String> postWithForwardedFor(String forwardedFor) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/session"))
        .header("Content-Type", "application/json")
        .header("X-Requested-With", "fetch")
        .header("X-Forwarded-For", forwardedFor);
    return http.send(
        builder.POST(HttpRequest.BodyPublishers.ofString("{\"account\":\"admin\",\"password\":\"admin123\"}")).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static String cookieOf(HttpResponse<String> loginResponse) {
    return loginResponse.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow(() -> new AssertionError("登录响应缺少 ZT_SESSION"))
        .split(";", 2)[0];
  }

  private static String tokenOf(String cookie) {
    return cookie.substring(cookie.indexOf('=') + 1);
  }

  private static String suffix() {
    return String.valueOf(System.nanoTime() % 100000000);
  }

  private String ipOf(String id) throws Exception {
    try (var connection = dataSource.getConnection();
        var query = connection.prepareStatement("SELECT ip FROM session WHERE id = ?")) {
      query.setString(1, id);
      var resultSet = query.executeQuery();
      assertTrue(resultSet.next(), "会话行应存在");
      return resultSet.getString(1);
    }
  }

  private int count(String sql, String value) throws Exception {
    try (var connection = dataSource.getConnection(); PreparedStatement query = connection.prepareStatement(sql)) {
      query.setString(1, value);
      var resultSet = query.executeQuery();
      resultSet.next();
      return resultSet.getInt(1);
    }
  }
}
