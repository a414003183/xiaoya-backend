package net.zentao.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 安全响应头（06 对齐 A7-1）：四头取值 + script-src 不放宽 + 下游异常也不丢头。 */
class SecurityHeadersFilterTest {

  private SecurityHeadersFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    filter = new SecurityHeadersFilter();
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
  }

  @Test
  @DisplayName("普通响应四头齐全且取值固定")
  void setsAllHeaders() throws Exception {
    filter.doFilter(request, response, new MockFilterChain());
    assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    assertEquals("DENY", response.getHeader("X-Frame-Options"));
    assertEquals("same-origin", response.getHeader("Referrer-Policy"));
    assertEquals(SecurityHeadersFilter.CSP, response.getHeader("Content-Security-Policy"));
  }

  @Test
  @DisplayName("CSP 允许 style 内联（antd cssVar），script 无 unsafe-inline")
  void cspAllowsInlineStyleOnly() throws Exception {
    filter.doFilter(request, response, new MockFilterChain());
    String csp = response.getHeader("Content-Security-Policy");
    assertEquals("default-src 'self'", csp.split(";")[0].trim());
    assertEquals("style-src 'self' 'unsafe-inline'", csp.split(";")[2].trim());
    assertFalse(csp.contains("script-src 'unsafe-inline'"), csp);
  }

  @Test
  @DisplayName("下游抛异常时响应仍已带四头（过滤器阶段拒绝也受覆盖）")
  void headersSurviveDownstreamFailure() {
    assertThrows(IllegalStateException.class,
        () -> filter.doFilter(request, response, (req, res) -> {
          throw new IllegalStateException("boom");
        }));
    assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    assertEquals("DENY", response.getHeader("X-Frame-Options"));
    assertEquals("same-origin", response.getHeader("Referrer-Policy"));
    assertEquals(SecurityHeadersFilter.CSP, response.getHeader("Content-Security-Policy"));
  }
}
