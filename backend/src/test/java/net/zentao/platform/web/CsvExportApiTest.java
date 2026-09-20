package net.zentao.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.zentao.platform.notification.NotificationRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** A-04 CSV 导出端到端：format=csv 请求经切面代理真 Controller，产出 text/csv + BOM。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CsvExportApiTest {

  @Value("${local.server.port}")
  int port;

  @Autowired
  NotificationRecorder recorder;

  private final HttpClient http = HttpClient.newHttpClient();

  private String login() throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/session"))
            .header("Content-Type", "application/json")
            .header("X-Requested-With", "fetch")
            .POST(HttpRequest.BodyPublishers.ofString("{\"account\":\"admin\",\"password\":\"admin123\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  @Test
  @DisplayName("GET /notifications?format=csv → 200 text/csv + UTF-8 BOM + 表头为 record 组件名")
  void exportsNotificationsAsCsv() throws Exception {
    String cookie = login();
    recorder.record(List.of("admin"), "csv-check", "story", 1, null, "导出样本", null, "admin");

    HttpResponse<byte[]> csv = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/notifications?format=csv"))
            .header("Cookie", cookie)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofByteArray());
    assertEquals(200, csv.statusCode());
    assertTrue(csv.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"),
        csv.headers().firstValue("Content-Type").orElse(""));
    byte[] body = csv.body();
    assertEquals(0xEF, body[0] & 0xFF, "BOM");
    String text = new String(body, StandardCharsets.UTF_8);
    assertTrue(text.startsWith("\uFEFFid,recipient,type"), text);
    assertTrue(text.contains("导出样本"), text);

    // 同一端点不带 format → 正常 JSON 信封
    HttpResponse<String> json = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/notifications?limit=1"))
            .header("Cookie", cookie)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, json.statusCode(), json.body());
    assertTrue(json.body().contains("\"data\""), json.body());
  }
}
