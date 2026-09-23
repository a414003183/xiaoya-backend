package net.zentao.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * T75 / OPS-10·OPS-11 可观测基础看护：
 *
 * <ol>
 *   <li>指标源：/actuator/prometheus 出指标（jvm_/http_ 前缀），口径不扩大——匿名仍 401（SurfaceGuardFilter）；</li>
 *   <li>结构化日志：logback-spring.xml 加载不炸（本类起 Spring 即验证），JSON 文件日志必带
 *       traceId（MDC，TraceIdFilter 置入的同一键）与 ISO-8601 时间戳——traceId 全链的日志半在此钉住。</li>
 * </ol>
 */
class ObservabilityTest extends ApiTestSupport {

  @Test
  @DisplayName("/actuator/prometheus 可达且含 jvm_/http_ 指标；匿名仍 401（不扩大敏感面）")
  void prometheusEndpointExposesMetrics() throws Exception {
    assertEquals(401, send("GET", "/actuator/prometheus", null, null).statusCode());
    String cookie = login("admin", "admin123");
    HttpResponse<String> response = send("GET", "/actuator/prometheus", null, cookie);
    assertEquals(200, response.statusCode(), response.body());
    assertTrue(response.body().contains("jvm_") || response.body().contains("http_"),
        response.body().substring(0, Math.min(400, response.body().length())));
  }

  @Test
  @DisplayName("JSON 文件日志带 traceId（MDC）与 ISO-8601 时间戳")
  void jsonLogCarriesTraceIdAndIsoTimestamp() throws Exception {
    String marker = "t75-json-selfcheck-" + System.nanoTime();
    MDC.put("traceId", "trace-t75.check_1-2");
    try {
      LoggerFactory.getLogger(ObservabilityTest.class).info(marker);
    } finally {
      MDC.remove("traceId");
    }
    List<String> lines = Files.readAllLines(Path.of("data", "logs", "zentao.log"));
    String line = lines.stream()
        .filter(candidate -> candidate.contains(marker))
        .findFirst()
        .orElseThrow(() -> new AssertionError("JSON 日志里没找到本次标记行（logback-spring.xml 未生效？）：" + marker));
    assertTrue(line.contains("\"traceId\":\"trace-t75.check_1-2\""), line);
    assertTrue(line.contains("\"@timestamp\":\"") && line.matches(".*\"@timestamp\":\"\\d{4}-\\d{2}-\\d{2}T.*"), line);
  }
}
