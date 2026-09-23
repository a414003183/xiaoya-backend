package net.zentao.org;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 口令生命周期（T62 SEC-11）：① 重置后强制改密；② 新口令不得等于当前口令；③ 不得复用最近 N 个用过的口令
 * （N=2 便于验「剪枝后最老的可复用」）。
 *
 * <p>跑法同 AccountApiTest（真 HTTP + H2）：改密要拿目标账号自己的会话（端点要求 accountId = 当前账号），
 * 重置走管理员会话。每个用例自建账号，避免与其它用例互相污染（同一 Spring 上下文共用一个库）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "zentao.security.password.history-count=2")
class PasswordLifecycleTest {

  @Value("${local.server.port}")
  int port;

  @Autowired
  JdbcTemplate jdbcTemplate;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private static final AtomicInteger SEQ = new AtomicInteger();

  /** 建一个全新账号（初始口令 seed1234），返回 [id, account]。 */
  private Object[] createAccount(String password) throws Exception {
    String account = "pwd" + SEQ.incrementAndGet();
    HttpResponse<String> created = send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"" + password + "\",\"realName\":\"口令用例\"}",
        adminCookie());
    assertEquals(200, created.statusCode(), created.body());
    return new Object[] {json.readTree(created.body()).at("/data/id").asLong(), account};
  }

  private String adminCookie() throws Exception {
    return login("admin", "admin123");
  }

  private String login(String account, String password) throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/session",
        "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}", null);
    assertEquals(200, response.statusCode(), response.body());
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private HttpResponse<String> send(String method, String path, String body, String cookie) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Content-Type", "application/json");
    if (cookie != null) {
      builder.header("Cookie", cookie);
    }
    builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  /** 本人改密（无 oldPassword 之外的期望）。 */
  private HttpResponse<String> changePassword(String cookie, long accountId, String oldPassword, String newPassword)
      throws Exception {
    return send("POST", "/api/v1/accounts/" + accountId + "/password",
        "{\"oldPassword\":\"" + oldPassword + "\",\"newPassword\":\"" + newPassword + "\"}", cookie);
  }

  private HttpResponse<String> resetPassword(String adminCookie, long accountId, String newPassword) throws Exception {
    return send("POST", "/api/v1/accounts/" + accountId + "/reset-password",
        "{\"newPassword\":\"" + newPassword + "\"}", adminCookie);
  }

  @Test
  @DisplayName("新=旧 → 422 newPassword=reused，且旧口令仍是当前口令")
  void rejectsSameAsCurrent() throws Exception {
    Object[] created = createAccount("seed1234");
    long id = (long) created[0];
    String userCookie = login((String) created[1], "seed1234");

    HttpResponse<String> reused = changePassword(userCookie, id, "seed1234", "seed1234");
    assertEquals(422, reused.statusCode(), reused.body());
    assertTrue(reused.body().contains("42201") && reused.body().contains("reused"), reused.body());

    // 失败必须是整体失败：旧口令还能登录（改密没落库）
    assertEquals(200, send("POST", "/api/v1/session",
        "{\"account\":\"" + created[1] + "\",\"password\":\"seed1234\"}", null).statusCode());
  }

  @Test
  @DisplayName("历史禁复用：改回上上次 → 422；被剪枝的最老口令 → 放行；历史恰留 N 条")
  void rejectsHistoryAndPrunes() throws Exception {
    Object[] created = createAccount("seed1234");
    long id = (long) created[0];
    String account = (String) created[1];
    String cookie = login(account, "seed1234");

    assertEquals(200, changePassword(cookie, id, "seed1234", "passA123").statusCode(), "第一次改密应放行");
    assertEquals(200, changePassword(cookie, id, "passA123", "passB123").statusCode(), "第二次改密应放行");

    HttpResponse<String> back = changePassword(cookie, id, "passB123", "passA123");
    assertEquals(422, back.statusCode(), back.body());
    assertTrue(back.body().contains("reused"), back.body());

    // 第三次改密把 seed1234 挤出窗口（N=2）→ 可以改回它
    assertEquals(200, changePassword(cookie, id, "passB123", "passC123").statusCode(), "第三次改密应放行");
    assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM password_history WHERE account_id = ?",
        Integer.class, id).intValue(), "N=2：历史只留最新 2 条");
    assertEquals(200, changePassword(cookie, id, "passC123", "seed1234").statusCode(), "被剪枝的口令应可复用");
    assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM password_history WHERE account_id = ?",
        Integer.class, id).intValue(), "历史条数不得超过 N");

    // 当前口令仍是 seed1234：再改回自己 → 422（不受 N 影响）
    assertEquals(422, changePassword(cookie, id, "seed1234", "seed1234").statusCode());
  }

  @Test
  @DisplayName("管理员重置：置强制改密标记 + 踢掉旧会话 + 重置口令可登录但仍带标记；连按两次同口令 → 第二次 422")
  void resetForcesChangeAndKicksSessions() throws Exception {
    Object[] created = createAccount("seed1234");
    long id = (long) created[0];
    String account = (String) created[1];
    String userCookie = login(account, "seed1234");

    HttpResponse<String> reset = resetPassword(adminCookie(), id, "reset1234");
    assertEquals(200, reset.statusCode(), reset.body());
    JsonNode view = json.readTree(reset.body()).at("/data");
    assertTrue(view.at("/mustChangePassword").asBoolean(), reset.body());

    // 旧会话已被踢（T51 SEC-04 口径不变）
    assertEquals(401, send("GET", "/api/v1/me", null, userCookie).statusCode());

    // 重置口令可登录，但 /me 仍带标记 → 会话门禁会把用户按在改密页
    String newCookie = login(account, "reset1234");
    HttpResponse<String> me = send("GET", "/api/v1/me", null, newCookie);
    assertEquals(200, me.statusCode(), me.body());
    assertTrue(json.readTree(me.body()).at("/data/account/mustChangePassword").asBoolean(), me.body());

    // 重置不得设成当前口令（否则管理员把已知口令又还回去）
    HttpResponse<String> again = resetPassword(adminCookie(), id, "reset1234");
    assertEquals(422, again.statusCode(), again.body());
    assertTrue(again.body().contains("reused"), again.body());
  }

  @Test
  @DisplayName("本人改密后标记清零，且不得复用管理员重置留下的口令")
  void selfChangeClearsFlagAndRemembersReset() throws Exception {
    Object[] created = createAccount("seed1234");
    long id = (long) created[0];
    String account = (String) created[1];

    assertEquals(200, resetPassword(adminCookie(), id, "reset1234").statusCode());
    String cookie = login(account, "reset1234");
    assertEquals(200, changePassword(cookie, id, "reset1234", "final1234").statusCode(), "改密应放行");
    HttpResponse<String> me = send("GET", "/api/v1/me", null, cookie);
    assertFalse(json.readTree(me.body()).at("/data/account/mustChangePassword").asBoolean(), me.body());

    // 改回管理员重置用的那条 → 历史命中
    assertEquals(422, changePassword(cookie, id, "final1234", "reset1234").statusCode());
  }
}
