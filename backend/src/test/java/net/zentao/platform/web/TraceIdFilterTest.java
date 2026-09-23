package net.zentao.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** T61/SEC-16 追踪 id：入站 X-Trace-Id 只收 [A-Za-z0-9._-]{1,64}，其余丢弃改自生成（不回显攻击者内容）；MDC 贯通。 */
class TraceIdFilterTest {

  private TraceIdFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    filter = new TraceIdFilter();
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
  }

  private String runFilter() throws Exception {
    AtomicReference<String> inChain = new AtomicReference<>();
    filter.doFilter(request, response, (req, res) -> inChain.set(MDC.get("traceId")));
    assertEquals(inChain.get(), response.getHeader("X-Trace-Id"), "MDC 与响应头同一值");
    return response.getHeader("X-Trace-Id");
  }

  @Test
  @DisplayName("合法字符集的入站值原样采用（响应头回显 + MDC 贯通）")
  void safeValueAccepted() throws Exception {
    request.addHeader("X-Trace-Id", "abc-123_x.y");
    assertEquals("abc-123_x.y", runFilter());
  }

  @Test
  @DisplayName("畸形值（控制符/空白/中文）丢弃改自生成 32 位 hex，不回显攻击者内容")
  void malformedValueReplacedNotEchoed() throws Exception {
    request.addHeader("X-Trace-Id", "<script>alert(1)</script>");
    String traceId = runFilter();
    assertNotEquals("<script>alert(1)</script>", traceId, "攻击者内容不许回显");
    assertTrue(traceId.matches("[0-9a-f]{32}"), traceId);

    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    request.addHeader("X-Trace-Id", "has space");
    assertFalse(runFilter().equals("has space"), "空白字符不收");
  }

  @Test
  @DisplayName("超长（65 字符）丢弃改自生成；64 字符恰好收")
  void lengthBounded() throws Exception {
    request.addHeader("X-Trace-Id", "a".repeat(65));
    assertFalse(runFilter().equals("a".repeat(65)), "超长不收");

    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    request.addHeader("X-Trace-Id", "a".repeat(64));
    assertEquals("a".repeat(64), runFilter(), "64 字符合法");
  }

  @Test
  @DisplayName("缺失时自生成 32 位 hex；请求结束 MDC 清空")
  void generatedWhenAbsentAndMdcCleared() throws Exception {
    assertTrue(runFilter().matches("[0-9a-f]{32}"));
    assertNull(MDC.get("traceId"), "请求结束 MDC 必须清空（虚拟线程池复用不串号）");
  }
}
