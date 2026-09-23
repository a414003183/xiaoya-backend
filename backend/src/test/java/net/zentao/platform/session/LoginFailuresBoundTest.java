package net.zentao.platform.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * T58 登录防线（三）· 失败表有界（SEC-13）：账号喷射（每次换一个不存在的账号名）不得把内存表撑成无界增长。
 * 容量压到 8 便于观测（默认 10000，见 application.yml）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "zentao.login.rate-limit-tracked-keys=8")
class LoginFailuresBoundTest {

  @Value("${local.server.port}")
  int port;

  @Autowired
  LoginHandler loginHandler;

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  @DisplayName("喷 12 个不同账号：失败表键数不超上限 8（LRU 淘汰），该记的失败仍在记")
  void sprayedAccountsDoNotGrowFailuresUnbounded() throws Exception {
    for (int index = 0; index < 12; index++) {
      assertEquals(401, login("t58-spray-" + index, "whatever-pass1").statusCode(),
          "账号维度各自只欠一次，不该被拦");
    }

    int tracked = loginHandler.trackedAccounts();
    assertTrue(tracked <= 8, "账号失败表应有界（上限 8），实测 " + tracked);
    assertTrue(tracked > 0, "淘汰不是清空：失败仍要记着");
  }

  private HttpResponse<String> login(String account, String password) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/session"))
            .header("Content-Type", "application/json")
            .header("X-Requested-With", "fetch")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
