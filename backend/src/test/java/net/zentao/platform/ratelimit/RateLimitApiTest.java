package net.zentao.platform.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * T59 端点级限流（SEC-07）：自助改密 / CSV 导出 / 全局搜索 / 文件上传四个敏感面按账号计窗，窗口内超限 42901。
 * 阈值压到 2 便于观测（默认见 application.yml 的 `zentao.ratelimit.*`）。
 *
 * <p>每个端点只由本类的一个用例打（配额按账号累计，跨用例共用会互相影响）；
 * 「各端点独立配额」这条性质由同包的 `RateLimitsTest` 直接对组件断言。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "zentao.ratelimit.change-password-per-window=2",
    "zentao.ratelimit.export-per-window=2",
    "zentao.ratelimit.search-per-window=2",
    "zentao.ratelimit.upload-per-window=2",
})
class RateLimitApiTest {

  private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private String cookie;
  private long adminId = 1;

  @BeforeEach
  void signIn() throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/session"))
            .header("Content-Type", "application/json")
            .header("X-Requested-With", "fetch")
            .POST(HttpRequest.BodyPublishers.ofString("{\"account\":\"admin\",\"password\":\"admin123\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    cookie = response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION=")).findFirst().orElseThrow().split(";", 2)[0];
    Matcher matcher = ID.matcher(response.body());
    if (matcher.find()) {
      adminId = Long.parseLong(matcher.group(1));
    }
  }

  @Test
  @DisplayName("全局搜索：窗口内第 3 次即 42901")
  void searchQuota() throws Exception {
    assertEquals(200, get("/api/v1/search?q=admin&limit=1").statusCode());
    assertEquals(200, get("/api/v1/search?q=admin&limit=1").statusCode());

    assertRateLimited(get("/api/v1/search?q=admin&limit=1"));
  }

  @Test
  @DisplayName("CSV 导出：窗口内第 3 次即 42901")
  void exportQuota() throws Exception {
    assertEquals(200, get("/api/v1/accounts?format=csv").statusCode());
    assertEquals(200, get("/api/v1/accounts?format=csv").statusCode());

    assertRateLimited(get("/api/v1/accounts?format=csv"));
  }

  @Test
  @DisplayName("自助改密：旧口令连试到第 3 次即 42901（前两次是 42201 口令不符）")
  void changePasswordQuota() throws Exception {
    String body = "{\"oldPassword\":\"wrong-pass1\",\"newPassword\":\"whatever-pass1\"}";
    assertEquals(422, post("/api/v1/accounts/" + adminId + "/password", body).statusCode());
    assertEquals(422, post("/api/v1/accounts/" + adminId + "/password", body).statusCode());

    assertRateLimited(post("/api/v1/accounts/" + adminId + "/password", body));
  }

  @Test
  @DisplayName("文件上传：窗口内第 3 次即 42901")
  void uploadQuota() throws Exception {
    assertEquals(200, upload("t59-rate-limit-1.txt").statusCode());
    assertEquals(200, upload("t59-rate-limit-2.txt").statusCode());

    assertRateLimited(upload("t59-rate-limit-3.txt"));
  }

  private void assertRateLimited(HttpResponse<String> response) {
    assertEquals(429, response.statusCode(), response.body());
    assertTrue(response.body().contains("42901"), response.body());
  }

  private HttpResponse<String> get(String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("X-Requested-With", "fetch")
            .header("Cookie", cookie)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path, String json) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("X-Requested-With", "fetch")
            .header("Cookie", cookie)
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  /** 手造 multipart（java.net.http 无表单构造口）。 */
  private HttpResponse<String> upload(String fileName) throws Exception {
    String boundary = "----t59" + System.nanoTime();
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
        + "\"\r\nContent-Type: text/plain\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    body.write("t59 rate limit sample".getBytes(StandardCharsets.UTF_8));
    body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/files"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .header("X-Requested-With", "fetch")
            .header("Cookie", cookie)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
