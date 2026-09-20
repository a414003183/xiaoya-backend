package net.zentao.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    filter = new CsrfHeaderFilter(JsonMapper.builder().build());
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
  }

  @Test
  @DisplayName("POST 带 X-Requested-With: fetch 放行")
  void postWithHeaderPasses() throws Exception {
    request.setMethod("POST");
    request.addHeader("X-Requested-With", "fetch");
    filter.doFilter(request, response, chain);
    assertEquals(200, response.getStatus());
  }
}
