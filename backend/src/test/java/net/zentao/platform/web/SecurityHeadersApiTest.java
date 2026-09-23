package net.zentao.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** 安全响应头真链路验证（06 对齐 A7-1）：401/403 拒绝响应也带头，SSE 不被 CSP 误伤。 */
@TestPropertySource(properties = "zentao.notification.sse.heartbeat=200")
class SecurityHeadersApiTest extends ApiTestSupport {

  private void assertHeaders(HttpResponse<?> response) {
    assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(""));
    assertEquals("DENY", response.headers().firstValue("X-Frame-Options").orElse(""));
    assertEquals("same-origin", response.headers().firstValue("Referrer-Policy").orElse(""));
    assertEquals(SecurityHeadersFilter.CSP, response.headers().firstValue("Content-Security-Policy").orElse(""));
  }

  @Test
  @DisplayName("匿名 401 响应四头齐全")
  void anonymousResponseCarriesHeaders() throws Exception {
    HttpResponse<String> response = send("GET", "/api/v1/me", null, null);
    assertEquals(401, response.statusCode());
    assertHeaders(response);
  }

  @Test
  @DisplayName("CSRF 拒绝（过滤器阶段直写 40301）四头齐全")
  void csrfRejectionCarriesHeaders() throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/session"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"account\":\"admin\",\"password\":\"admin123\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(403, response.statusCode(), response.body());
    assertTrue(response.body().contains("40301"), response.body());
    assertHeaders(response);
  }

  @Test
  @DisplayName("SSE 不被 CSP 误伤：流正常 200 且四头在响应上")
  void sseStreamNotBlocked() throws Exception {
    String cookie = login("admin", "admin123");
    HttpResponse<java.io.InputStream> response = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/notifications/stream"))
            .header("Cookie", cookie)
            .header("Accept", "text/event-stream")
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofInputStream());
    try {
      assertEquals(200, response.statusCode());
      assertHeaders(response);
      assertEquals("text/event-stream", response.headers().firstValue("Content-Type").orElse("").split(";")[0]);
    } finally {
      response.body().close();
    }
  }

  @Test
  @DisplayName("阀未装（默认）：伪造 X-Forwarded-Proto: https 也不发 HSTS（不采信自报协议）")
  void spoofedForwardedProtoDoesNotTriggerHsts() throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
            .header("X-Requested-With", "fetch")
            .header("X-Forwarded-Proto", "https")
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals("", response.headers().firstValue("Strict-Transport-Security").orElse(""));
  }
}
