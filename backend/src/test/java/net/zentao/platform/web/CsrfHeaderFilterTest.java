package net.zentao.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.i18n.RequestLocaleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** CSRF 头校验（03 §7）：写请求缺 X-Requested-With: fetch → 40301；GET 与带头写请求放行。 */
class CsrfHeaderFilterTest {

  private CsrfHeaderFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private MockFilterChain chain;

  @BeforeEach
  void setUp() {
    // 文案走语言包键（T23）；本测试只验信封，语言包为空 → 回落键名（真实文案见集成测试与门禁）
    filter = new CsrfHeaderFilter(JsonMapper.builder().build(), new RequestLocaleResolver(),
        new MessageResolver(new StaticMessageSource()));
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    chain = new MockFilterChain();
  }

  @Test
  @DisplayName("GET 请求免校验")
  void getPasses() throws Exception {
    request.setMethod("GET");
    filter.doFilter(request, response, chain);
    assertEquals(200, response.getStatus());
  }

  @Test
  @DisplayName("POST 缺头 → 40301 错误信封")
  void postWithoutHeaderRejected() throws Exception {
    request.setMethod("POST");
    filter.doFilter(request, response, chain);
    assertEquals(403, response.getStatus());
    String body = response.getContentAsString();
    assertTrue(body.contains("40301"), body);
    assertTrue(body.contains("traceId"), body);
    assertTrue(body.contains("error.csrf.missing"), "文案走键（缺键回落键名）：" + body);
  }

  @Test
  @DisplayName("POST 带 X-Requested-With: fetch 放行")
  void postWithHeaderPasses() throws Exception {
    request.setMethod("POST");
    request.addHeader("X-Requested-With", "fetch");
    filter.doFilter(request, response, chain);
    assertEquals(200, response.getStatus());
  }

  // ── T61 / SEC-19 二层：Origin 同源 + Sec-Fetch-Site ──

  private void stateChange() {
    request.setMethod("POST");
    request.addHeader("X-Requested-With", "fetch");
    request.addHeader("Host", "app.example.com");
  }

  @Test
  @DisplayName("跨源 Origin → 40301（error.csrf.origin），同源 Origin 放行")
  void crossOriginRejectedSameOriginPasses() throws Exception {
    stateChange();
    request.addHeader("Origin", "https://evil.example");
    filter.doFilter(request, response, chain);
    assertEquals(403, response.getStatus());
    String body = response.getContentAsString();
    assertTrue(body.contains("40301"), body);
    assertTrue(body.contains("error.csrf.origin"), "文案走键（缺键回落键名）：" + body);

    response = new MockHttpServletResponse();
    chain = new MockFilterChain();
    request = new MockHttpServletRequest();
    stateChange();
    request.addHeader("Origin", "https://app.example.com");
    filter.doFilter(request, response, chain);
    assertEquals(200, response.getStatus());
  }

  @Test
  @DisplayName("Origin 与 X-Forwarded-Host（反代采信后的 host）同源也算同源")
  void originMatchesForwardedHost() throws Exception {
    stateChange();
    request.addHeader("Origin", "https://public.example.com");
    request.addHeader("X-Forwarded-Host", "public.example.com");
    request.addHeader("Host", "10.0.0.5:8080");
    filter.doFilter(request, response, chain);
    assertEquals(200, response.getStatus());
  }

  @Test
  @DisplayName("Sec-Fetch-Site: cross-site 拒绝；same-origin/same-site/none 放行；不带照旧放行")
  void secFetchSiteCrossSiteRejected() throws Exception {
    for (String allowed : new String[] {"same-origin", "same-site", "none"}) {
      request = new MockHttpServletRequest();
      response = new MockHttpServletResponse();
      chain = new MockFilterChain();
      stateChange();
      request.addHeader("Sec-Fetch-Site", allowed);
      filter.doFilter(request, response, chain);
      assertEquals(200, response.getStatus(), allowed);
    }

    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    chain = new MockFilterChain();
    stateChange();
    request.addHeader("Sec-Fetch-Site", "cross-site");
    filter.doFilter(request, response, chain);
    assertEquals(403, response.getStatus());
    assertTrue(response.getContentAsString().contains("40301"));

    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    chain = new MockFilterChain();
    stateChange();
    filter.doFilter(request, response, chain);
    assertEquals(200, response.getStatus(), "无 Origin/Sec-Fetch-Site 的非浏览器客户端照旧放行");
  }

  @Test
  @DisplayName("GET 免二层校验（跨源 GET 也放行）；畸形 Origin 拒绝")
  void safeMethodsExemptAndMalformedOriginRejected() throws Exception {
    request.setMethod("GET");
    request.addHeader("Host", "app.example.com");
    request.addHeader("Origin", "https://evil.example");
    filter.doFilter(request, response, chain);
    assertEquals(200, response.getStatus());

    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    chain = new MockFilterChain();
    stateChange();
    request.addHeader("Origin", "null");
    filter.doFilter(request, response, chain);
    assertEquals(403, response.getStatus(), "opaque 来源（null）不与任何 host 同源");
  }
}
