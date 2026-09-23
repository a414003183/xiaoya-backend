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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * T58 登录防线（二）· IP 维度（SEC-14）：一个 IP 刷一堆**不同**账号时，账号维度各自计数拦不住，
 * 由来源 IP 的失败窗兜住；一次成功即清空该 IP 的窗（自己人打错几次不至于被锁在门外）。
 *
 * <p>阈值压到 3 便于观测（默认 100，见 application.yml）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "zentao.login.rate-limit-max-per-ip=3")
class LoginIpRateLimitTest {

  private static final Pattern MESSAGE = Pattern.compile("\"message\":\"([^\"]*)\"");

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  @DisplayName("IP 窗：成功一次即清零；之后从零累计，第 3 次失败后换账号也拦（42901）")
  void ipWindowBlocksSprayAndSuccessClearsIt() throws Exception {
    // 先记 2 次失败（未达阈值 3）——若成功不清窗，这 2 笔旧账会一直挂着
    assertEquals(401, login("t58-ip-a", "whatever-pass1").status);
    assertEquals(401, login("t58-ip-b", "whatever-pass1").status);
    assertEquals(200, login("admin", "admin123").status, "成功登录不得被自己的失败窗拦住");

    // 清窗后从零累计：前两次仍放行（旧账若没清，第 2 次就已是第 4 笔 → 429）
    assertEquals(401, login("t58-ip-c", "whatever-pass1").status, "成功应清空该 IP 的失败窗");
    assertEquals(401, login("t58-ip-d", "whatever-pass1").status);

    // 第 3 次失败记满阈值，第 4 次换**第四个账号**照样拦下 → 拦的是 IP 维度，不是账号维度
    assertEquals(401, login("t58-ip-e", "whatever-pass1").status);
    Attempt blocked = login("t58-ip-f", "whatever-pass1");
    assertEquals(429, blocked.status, blocked.body);
    assertTrue(blocked.body.contains("42901"), blocked.body);

    // 窗满期间该来源一律拦下——**口令正确也一样**：既是防撞库，也把 BCrypt 的 CPU 花销挡在门外。
    // 代价是同一出口 IP 的其他人要等窗口滑过（阈值可调，`--zentao.login.rate-limit-max-per-ip=0` 关闭该维度）。
    Attempt correctWhileBlocked = login("admin", "admin123");
    assertEquals(429, correctWhileBlocked.status, correctWhileBlocked.body);
  }

  private Attempt login(String account, String password) throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/session"))
            .header("Content-Type", "application/json")
            .header("X-Requested-With", "fetch")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    return new Attempt(response.statusCode(), response.body(), response.headers().allValues("set-cookie"));
  }

  private record Attempt(int status, String body, List<String> cookies) {

    String message() {
      Matcher matcher = MESSAGE.matcher(body);
      return matcher.find() ? matcher.group(1) : "";
    }
  }
}
