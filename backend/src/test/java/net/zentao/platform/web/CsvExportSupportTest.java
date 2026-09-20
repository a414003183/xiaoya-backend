package net.zentao.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import net.zentao.platform.error.ApiException;
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

  private final CsvExportSupport support = new CsvExportSupport(JsonMapper.builder().build());

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
}
