package net.zentao.platform.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.zentao.platform.error.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** 搜索 API（platform 卡 §8）：scope 白名单外 40001；P1 空注册表返回空集。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchApiTest {

  @Value("${local.server.port}")
  int port;

  @Autowired
  SearchRegistry registry;

  private final HttpClient http = HttpClient.newHttpClient();

  private String loginCookie() throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/session"))
            .header("Content-Type", "application/json")
            .header("X-Requested-With", "fetch")
            .POST(HttpRequest.BodyPublishers.ofString("{\"account\":\"admin\",\"password\":\"admin123\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  @Test
  @DisplayName("scope 白名单外 → 40001；注册表拒绝未知 scope")
  void unknownScopeRejected() throws Exception {
    String cookie = loginCookie();
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/search?q=x&scope=unknownScope"))
            .header("Cookie", cookie)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(400, response.statusCode(), response.body());
    assertTrue(response.body().contains("40001"), response.body());

    ApiException local = assertThrows(ApiException.class, () -> registry.requireScope("unknownScope"));
    assertEquals(40001, local.errorCode().code());
  }

  @Test
  @DisplayName("白名单内 scope：P1 空注册表 → 空集；缺 q → 400")
  void emptyRegistryYieldsEmptyResults() throws Exception {
    String cookie = loginCookie();
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/search?q=%E7%99%BB%E5%BD%95&scope=story"))
            .header("Cookie", cookie)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    assertTrue(response.body().contains("\"total\":0"), response.body());

    HttpResponse<String> noQ = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/search"))
            .header("Cookie", cookie)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(400, noQ.statusCode(), noQ.body());
  }
}
