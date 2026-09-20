package net.zentao.org;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.zentao.platform.activity.ActivityRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 账号动作（org 卡 §4/§8）：状态机 42202、守卫 42203、锁定窗口、重置密码通知、disable 会话失效。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountActionTest {

  @Value("${local.server.port}")
  int port;

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Autowired
  ActivityRecorder activityRecorder;

  private final HttpClient http = HttpClient.newHttpClient();
  private String adminCookie;
  private String plainAccount;
  private String plainCookie;
  private long plainId;

  @BeforeEach
  void seed() throws Exception {
    HttpResponse<String> login = send("POST", "/api/v1/session",
        "{\"account\":\"admin\",\"password\":\"admin123\"}", null, null);
    adminCookie = cookieOf(login);

    plainAccount = "action-user-" + System.nanoTime();
    jdbcTemplate.update("INSERT INTO account (account, password, real_name) VALUES (?, ?, '动作用户')",
        plainAccount, new BCryptPasswordEncoder().encode("secret123"));
    plainId = jdbcTemplate.queryForObject("SELECT id FROM account WHERE account = ?", Long.class, plainAccount);
    plainCookie = cookieOf(loginAs(plainAccount, "secret123"));
  }

  private HttpResponse<String> loginAs(String account, String password) throws Exception {
    return send("POST", "/api/v1/session",
        "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}", null, null);
  }

  private String cookieOf(HttpResponse<String> response) {
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private HttpResponse<String> send(String method, String path, String body, String cookie, String put) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Content-Type", "application/json");
    if (cookie != null) {
      builder.header("Cookie", cookie);
    }
    builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String action(String cookieValue, long id, String action, String body) throws Exception {
    return send("POST", "/api/v1/accounts/" + id + "/" + action, body, cookieValue, null).body();
  }  @Test
  @DisplayName("状态机：disable→enable；disabled 再 disable → 42202；停用/删除自己或 admin → 42203")
  void stateMachineAndGuards() throws Exception {
    assertTrue(action(adminCookie, plainId, "disable", "{}").contains("\"status\":\"disabled\""), "停用");
    assertTrue(action(adminCookie, plainId, "enable", "{}").contains("\"status\":\"active\""), "启用");

    action(adminCookie, plainId, "disable", "{}");
    String second = action(adminCookie, plainId, "disable", "{}");
    assertTrue(second.contains("42202"), "已停用再停用 → 42202: " + second);
    action(adminCookie, plainId, "enable", "{}");

    String self = action(adminCookie, 1L, "disable", "{}");
    assertTrue(self.contains("42203"), "停用内置 admin → 42203: " + self);
    String selfDelete = action(adminCookie, 1L, "delete", "{}");
    assertTrue(selfDelete.contains("42203"), "删除内置 admin → 42203: " + selfDelete);
  }

  @Test
  @DisplayName("disable 后旧 cookie 立即 40101 且登录拒绝；enable 后可登录")
  void disableInvalidatesSessions() throws Exception {
    HttpResponse<String> meBefore = send("GET", "/api/v1/me", null, plainCookie, null);
    assertEquals(200, meBefore.statusCode(), meBefore.body());

    action(adminCookie, plainId, "disable", "{}");
    assertEquals(401, send("GET", "/api/v1/me", null, plainCookie, null).statusCode(), "停用后会话立即失效");
    HttpResponse<String> relogin = loginAs(plainAccount, "secret123");
    assertEquals(401, relogin.statusCode(), "停用账号登录 → 40101: " + relogin.body());

    action(adminCookie, plainId, "enable", "{}");
    assertEquals(200, loginAs(plainAccount, "secret123").statusCode(), "启用后可重新登录");
  }

  @Test
  @DisplayName("锁定：6 次失败置锁定 40101；unlock 立即清；reset-password 通知本人")
  void lockAndReset() throws Exception {
    // 5 次失败（未到 6）→ 第 6 次成功登录仍可以？锁定阈值为 6：第 6 次失败置 lockedAt
    for (int i = 0; i < 6; i++) {
      loginAs(plainAccount, "wrong-password");
    }
    HttpResponse<String> locked = loginAs(plainAccount, "secret123");
    assertTrue(locked.body().contains("40101"), "锁定窗口内正确密码也拒绝: " + locked.body());

    // unlock 立即清除
    HttpResponse<String> unlock = send("POST", "/api/v1/accounts/" + plainId + "/unlock", null, adminCookie, null);
    assertEquals(200, unlock.statusCode(), unlock.body());
    HttpResponse<String> afterUnlock = loginAs(plainAccount, "secret123");
    System.out.println("AFTER UNLOCK " + afterUnlock.statusCode() + " " + afterUnlock.body());
    assertEquals(200, afterUnlock.statusCode(), "解锁后可登录");

    // reset-password：新密码生效 + 通知本人 + 动态流 passwordChanged
    HttpResponse<String> reset = send("POST", "/api/v1/accounts/" + plainId + "/reset-password",
        "{\"newPassword\":\"newpass123\"}", adminCookie, null);
    assertEquals(200, reset.statusCode(), reset.body());
    assertEquals(200, loginAs(plainAccount, "newpass123").statusCode(), "重置后新密码可登录");
    Integer notifications = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM notification WHERE recipient = ? AND type = 'account-reset-password'",
        Integer.class, plainAccount);
    assertEquals(1, notifications, "重置后本人收到通知");
    assertTrue(action(adminCookie, plainId, "unlock", null).contains("\"fails\":0"), "unlock 清 fails");

    // 本人改密：旧密码错 → 42201
    HttpResponse<String> me = loginAs(plainAccount, "newpass123");
    String meCookie = cookieOf(me);
    HttpResponse<String> wrongOld = send("POST", "/api/v1/accounts/" + plainId + "/password",
        "{\"oldPassword\":\"bad\",\"newPassword\":\"another123\"}", meCookie, null);
    assertEquals(422, wrongOld.statusCode(), wrongOld.body());
    assertTrue(wrongOld.body().contains("42201"), wrongOld.body());
  }

  @Test
  @DisplayName("密码策略（A7-4）：弱口令 42201（短/纯字母/纯数字），≥8 且字母+数字放行")
  void passwordPolicy() throws Exception {
    // 重置端点与本人改密共用 requireNewPassword，故用重置端点覆盖策略分支
    String shortPwd = send("POST", "/api/v1/accounts/" + plainId + "/reset-password",
        "{\"newPassword\":\"a1b2c3\"}", adminCookie, null).body();
    assertTrue(shortPwd.contains("42201") && shortPwd.contains("size"), "7 位被拒: " + shortPwd);

    String lettersOnly = send("POST", "/api/v1/accounts/" + plainId + "/reset-password",
        "{\"newPassword\":\"abcdefghij\"}", adminCookie, null).body();
    assertTrue(lettersOnly.contains("42201") && lettersOnly.contains("weak"), "纯字母被拒: " + lettersOnly);

    String digitsOnly = send("POST", "/api/v1/accounts/" + plainId + "/reset-password",
        "{\"newPassword\":\"12345678\"}", adminCookie, null).body();
    assertTrue(digitsOnly.contains("42201") && digitsOnly.contains("weak"), "纯数字被拒: " + digitsOnly);

    // 边界：8 位且字母+数字 → 通过；被拒口令不得生效
    HttpResponse<String> ok = send("POST", "/api/v1/accounts/" + plainId + "/reset-password",
        "{\"newPassword\":\"strong123\"}", adminCookie, null);
    assertEquals(200, ok.statusCode(), ok.body());
    assertEquals(200, loginAs(plainAccount, "strong123").statusCode(), "合规口令生效");
    assertTrue(loginAs(plainAccount, "12345678").body().contains("40101"), "被拒口令未生效");
  }
}
