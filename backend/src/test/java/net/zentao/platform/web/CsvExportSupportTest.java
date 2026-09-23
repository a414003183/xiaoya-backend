package net.zentao.platform.web;

import static net.zentao.platform.web.CsvExportSupport.escape;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import net.zentao.platform.audit.AuditRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.ratelimit.RateLimits;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.session.SessionResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/** A-04 CSV 导出横切：mock joinPoint.proceed → 断言 CSV 文本/BOM/RFC4180 转义/行数闸门/非 csv 直通。 */
class CsvExportSupportTest {

  record Row(long id, String title, List<String> tags) {}

  record RowList(List<Row> items, long total) {}

  private final CsvExportSupport support = support(20);

  /** 真限流器 + 桩会话：限流真跑（不 mock），会话只给个账号；导出审计记录器本类不关心（T10 起构造要它）。 */
  private static CsvExportSupport support(int exportLimit) {
    RateLimits limits = new RateLimits(Duration.ofMinutes(1), 1000, 10, exportLimit, 10, 10);
    SessionResolver resolver = mock(SessionResolver.class);
    when(resolver.cachedPrincipal(any())).thenReturn(new SessionPrincipal("sid", 1L, "admin"));
    return new CsvExportSupport(JsonMapper.builder().build(), limits, resolver, mock(AuditRecorder.class));
  }

  @AfterEach
  void resetRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  private ServletRequestAttributes bind(MockHttpServletRequest request, MockHttpServletResponse response) {
    ServletRequestAttributes attributes = new ServletRequestAttributes(request, response);
    RequestContextHolder.setRequestAttributes(attributes);
    return attributes;
  }

  @Test
  @DisplayName("format=csv：表头=组件名声明序，值逐字段转 CSV，BOM 前缀，text/csv")
  void writesCsvWithBomAndEscaping() throws Throwable {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("format", "csv");
    MockHttpServletResponse response = new MockHttpServletResponse();
    bind(request, response);
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    when(joinPoint.proceed()).thenReturn(DataEnvelope.of(new RowList(List.of(
        new Row(1, "普通", List.of("a")),
        new Row(2, "带,逗号\"引号\"\n换行", List.of("x", "y"))), 2)));

    assertNull(support.exportIfRequested(joinPoint), "短路 MVC：返回 null");
    assertEquals("text/csv; charset=UTF-8", response.getContentType());
    byte[] body = response.getContentAsByteArray();
    assertEquals(0xEF, body[0] & 0xFF, "UTF-8 BOM");
    assertEquals(0xBB, body[1] & 0xFF, "UTF-8 BOM");
    assertEquals(0xBF, body[2] & 0xFF, "UTF-8 BOM");
    String csv = new String(body, StandardCharsets.UTF_8);
    String expected = "\uFEFF" + "id,title,tags\n"
        + "1,普通,\"[\"\"a\"\"]\"\n"
        + "2,\"带,逗号\"\"引号\"\"\n换行\",\"[\"\"x\"\",\"\"y\"\"]\"\n";
    assertEquals(expected, csv);
  }

  @Test
  @DisplayName("空 items：仅 BOM，无表头")
  void emptyItemsBomOnly() throws Throwable {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("format", "csv");
    MockHttpServletResponse response = new MockHttpServletResponse();
    bind(request, response);
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    when(joinPoint.proceed()).thenReturn(DataEnvelope.of(new RowList(List.of(), 0)));

    assertNull(support.exportIfRequested(joinPoint));
    assertEquals(3, response.getContentAsByteArray().length, "仅 BOM 三字节");
  }

  @Test
  @DisplayName("total > 5000 → 40001")
  void tooManyRowsRejected() throws Throwable {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("format", "csv");
    MockHttpServletResponse response = new MockHttpServletResponse();
    bind(request, response);
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    when(joinPoint.proceed()).thenReturn(DataEnvelope.of(new RowList(List.of(), 5001)));

    ApiException error = assertThrows(ApiException.class, () -> support.exportIfRequested(joinPoint));
    assertEquals(40001, error.errorCode().code());
    assertEquals(0, response.getContentAsByteArray().length, "闸门拒绝时不写响应体");
  }

  @Test
  @DisplayName("非 format=csv 请求：直接 proceed 原样返回，不写响应")
  void nonCsvRequestPassthrough() throws Throwable {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    bind(request, response);
    DataEnvelope<RowList> envelope = DataEnvelope.of(new RowList(List.of(new Row(1, "x", null)), 1));
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    when(joinPoint.proceed()).thenReturn(envelope);

    assertEquals(envelope, support.exportIfRequested(joinPoint));
    verify(joinPoint, times(1)).proceed();
    assertEquals(0, response.getContentAsByteArray().length);
    assertTrue(response.getContentType() == null);
  }

  @Test
  @DisplayName("公式注入防御（T59 SEC-06）：= + - @ / 制表 / 回车 开头的单元格前缀单引号")
  void defusesFormulaPrefixes() {
    assertTrue(escape("=cmd|' /C calc'!A0").startsWith("'="), escape("=cmd|' /C calc'!A0"));
    assertEquals("'+1+1", escape("+1+1"));
    assertEquals("'-2+3", escape("-2+3"), "以减号开头的非数字也是公式面");
    assertEquals("'@SUM(A1)", escape("@SUM(A1)"));
    assertEquals("'\t=1+1", escape("\t=1+1"));
    assertEquals("\"'\r=1+1\"", escape("\r=1+1"), "回车本身还触发 RFC4180 引号，故整体包引号");
    assertEquals("'=1+1", escape("'=1+1"), "已经文本化的不再叠加");
  }

  @Test
  @DisplayName("公式防御的数值例外：-5 / +3.2 / 1e5 仍按数值原样输出（负数不得变文本）")
  void keepsSignedNumbersUntouched() {
    assertEquals("-5", escape("-5"));
    assertEquals("+3.2", escape("+3.2"));
    assertEquals("-1e5", escape("-1e5"));
    assertEquals("-.5", escape("-.5"));
    assertEquals("\"'-5,6\"", escape("-5,6"), "带千分位的不是纯数值：先文本化再按 RFC4180 引号");
  }

  @Test
  @DisplayName("公式注入防御贯穿整行：导出的字段值带前缀，表头不受影响")
  void defusesFormulaInRowValues() throws Throwable {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("format", "csv");
    MockHttpServletResponse response = new MockHttpServletResponse();
    bind(request, response);
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    when(joinPoint.proceed()).thenReturn(DataEnvelope.of(new RowList(List.of(
        new Row(1, "=1+1", List.of()),
        new Row(2, "-5", List.of())), 2)));

    assertNull(support.exportIfRequested(joinPoint));
    String csv = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
    assertEquals("\uFEFF" + "id,title,tags\n1,'=1+1,[]\n2,-5,[]\n", csv);
  }

  @Test
  @DisplayName("导出限流（T59 SEC-07）：超限时在查询之前就拦下——proceed 一次都不许多余发生")
  void exportThrottleFiresBeforeProceed() throws Throwable {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("format", "csv");
    bind(request, new MockHttpServletResponse());
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    when(joinPoint.proceed()).thenReturn(DataEnvelope.of(new RowList(List.of(new Row(1, "x", null)), 1)));
    CsvExportSupport limited = support(1);

    limited.exportIfRequested(joinPoint);
    ApiException error = assertThrows(ApiException.class, () -> limited.exportIfRequested(joinPoint));

    assertEquals(42901, error.errorCode().code());
    verify(joinPoint, times(1)).proceed();
  }
}
