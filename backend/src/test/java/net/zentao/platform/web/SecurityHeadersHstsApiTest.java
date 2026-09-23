package net.zentao.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * T61/SEC-10：HSTS 只在 HTTPS 上发——反代部署（T51 RemoteIpValve 采信 X-Forwarded-Proto=https）即视为 HTTPS，
 * 纯 HTTP 不发。与 {@link SecurityHeadersApiTest#spoofedForwardedProtoDoesNotTriggerHsts}（阀未装 → 伪造头不采信）
 * 是一对，口径同 T51 的 IP 面：**是否采信只取决于两个转发头属性是否配置**。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "server.tomcat.remoteip.remote-ip-header=x-forwarded-for",
    "server.tomcat.remoteip.protocol-header=x-forwarded-proto"})
class SecurityHeadersHstsApiTest extends ApiTestSupport {

  @Test
  @DisplayName("代理场景：X-Forwarded-Proto: https 采信后发 HSTS")
  void hstsSentWhenForwardedProtoHttps() throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
            .header("X-Requested-With", "fetch")
            .header("X-Forwarded-Proto", "https")
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals("max-age=31536000", response.headers().firstValue("Strict-Transport-Security").orElse(""),
        response.body());
  }

  @Test
  @DisplayName("纯 HTTP（无转发头）不发 HSTS——localhost 开发态不被钉死 HTTPS")
  void hstsAbsentOnPlainHttp() throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
            .header("X-Requested-With", "fetch")
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertNull(response.headers().firstValue("Strict-Transport-Security").orElse(null), response.body());
  }
}
