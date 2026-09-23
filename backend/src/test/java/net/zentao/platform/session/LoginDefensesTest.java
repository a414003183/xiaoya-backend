package net.zentao.platform.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * T58 登录防线（一）· 账号枚举面（SEC-12）：未知账号 / 口令错 / 锁定三态，对调用方"看得见"什么。
 *
 * <p>未知账号与口令错必须**同状态码、同文案、同量级耗时**；账号的锁定/停用只在**口令正确时**才告知
 * ——否则「账号已锁定」这句文案本身就是「这个账号存在」的答案。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginDefensesTest {

  private static final Pattern MESSAGE = Pattern.compile("\"message\":\"([^\"]*)\"");

  @Value("${local.server.port}")
  int port;

  @Autowired
  JdbcTemplate jdbc;

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  @DisplayName("未知账号 与 口令错：同 40101、同文案、都付一次 BCrypt（无快失败）")
  void unknownAccountIsIndistinguishableFromWrongPassword() throws Exception {
    activeAccount("t58-known");
    warmUp();
    Attempt unknown = login("t58-nobody", "whatever-pass1");
    Attempt knownWrong = login("t58-known", "whatever-pass1");

    assertEquals(401, unknown.status, unknown.body);
    assertEquals(401, knownWrong.status, knownWrong.body);
    assertEquals(knownWrong.message(), unknown.message(), "未知账号不得有独立文案（=账号存在性预言机）");
    assertTrue(unknown.body.contains("40101"), unknown.body);
    assertTrue(unknown.cookies.isEmpty(), "登录失败不得种 cookie");
    // 计时面：strength-10 的 BCrypt 是几十毫秒量级，跳过它的旧快路径只有个位数毫秒。
    // 三次取中位（单次实测会被 JIT/GC 带偏）+ 比值判定（绝对阈值随机器算力漂移）。
    long unknownMillis = medianMillis("t58-nobody", "whatever-pass2");
    long knownMillis = medianMillis("t58-known", "whatever-pass2");
    assertTrue(unknownMillis * 2 > knownMillis,
        "未知账号不得走快路径（SEC-12 计时差）：unknown=" + unknownMillis + "ms known=" + knownMillis + "ms");
  }

  @Test
  @DisplayName("锁定账号：口令错 → 同未知账号文案；口令对 → 才回锁定")
  void lockStateHiddenUntilPasswordMatches() throws Exception {
    lockedAccount("t58-locked");
    Attempt nobody = login("t58-nobody-2", "whatever-pass1");
    Attempt wrong = login("t58-locked", "whatever-pass1");
    Attempt right = login("t58-locked", "admin123");

    assertEquals(401, wrong.status, wrong.body);
    assertEquals(nobody.message(), wrong.message(), "锁定状态不得在口令错时泄露");
    assertEquals(401, right.status, right.body);
    assertTrue(right.message().contains("锁定"), "口令正确才回答锁定：" + right.message());
  }

  @Test
  @DisplayName("锁定期内再失败不累加（否则错口令可无限续锁 = 自助 DoS）")
  void failureWhileLockedDoesNotExtendLock() throws Exception {
    lockedAccount("t58-lockext");
    login("t58-lockext", "whatever-pass1");

    assertEquals(6, fails("t58-lockext"), "锁定期内的失败不得推进锁定");
  }

  /** 首次请求含管道冷启动，不计入任何断言（放一个便宜的无会话请求把链路烤热）。 */
  private void warmUp() throws Exception {
    http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private void lockedAccount(String account) {
    jdbc.update(
        "INSERT INTO account (account, password, real_name, status, fails, locked_at)"
            + " VALUES (?, ?, '锁定用例', 'active', 6, CURRENT_TIMESTAMP)",
        account, new BCryptPasswordEncoder().encode("admin123"));
  }

  /** 未锁定、口令 admin123 的专用账号——计时对照与「已知账号」用，不碰 admin（免得推进它的锁定计数）。 */
  private void activeAccount(String account) {
    jdbc.update("INSERT INTO account (account, password, real_name) VALUES (?, ?, '计时用例')",
        account, new BCryptPasswordEncoder().encode("admin123"));
  }

  /** 同一场景打三次取中位。 */
  private long medianMillis(String account, String password) throws Exception {
    long[] samples = new long[3];
    for (int index = 0; index < samples.length; index++) {
      samples[index] = login(account, password).millis;
    }
    java.util.Arrays.sort(samples);
    return samples[samples.length / 2];
  }

  private int fails(String account) {
    return jdbc.queryForObject("SELECT fails FROM account WHERE account = ?", Integer.class, account);
  }

  private Attempt login(String account, String password) throws Exception {
    long startedAt = System.nanoTime();
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/session"))
            .header("Content-Type", "application/json")
            .header("X-Requested-With", "fetch")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    return new Attempt(response.statusCode(), response.body(),
        (System.nanoTime() - startedAt) / 1_000_000, response.headers().allValues("set-cookie"));
  }

  private record Attempt(int status, String body, long millis, List<String> cookies) {

    String message() {
      Matcher matcher = MESSAGE.matcher(body);
      return matcher.find() ? matcher.group(1) : "";
    }
  }
}
