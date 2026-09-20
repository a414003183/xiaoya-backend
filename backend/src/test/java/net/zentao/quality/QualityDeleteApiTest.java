package net.zentao.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * A-07 质量域软删 DELETE ×6（quality 卡 §5 / 契约 deleteBug|deleteTestCase|deleteSuite|deleteLibrary|
 * deleteTestRun|deleteReport）：data:null 响应、软删后详情 40401、再删 40401、
 * library 守卫 42203、report 删除回写 test_run.reportId 清空。
 */
class QualityDeleteApiTest extends net.zentao.ApiTestSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private long productId;
  private long projectId;
  private long executionId;

  @BeforeEach
  void prepare() throws Exception {
    HttpResponse<String> login = send("POST", "/api/v1/session",
        "{\"account\":\"admin\",\"password\":\"admin123\"}", null);
    assertEquals(200, login.statusCode(), login.body());
    adminCookie = login.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
    productId = id(send("POST", "/api/v1/products",
        "{\"name\":\"软删产品-" + System.nanoTime() + "\"}", adminCookie));
    projectId = id(send("POST", "/api/v1/projects",
        "{\"name\":\"软删项目-" + System.nanoTime() + "\",\"beginDate\":\"2026-01-01\","
            + "\"endDate\":\"2026-12-31\",\"acl\":\"private\",\"productIds\":[" + productId + "]}",
        adminCookie));
    executionId = id(send("POST", "/api/v1/projects/" + projectId + "/executions",
        "{\"type\":\"sprint\",\"name\":\"软删迭代\",\"beginDate\":\"2026-01-01\",\"endDate\":\"2026-06-30\"}",
        adminCookie));
  }

  private HttpResponse<String> send(String method, String path, String body) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Content-Type", "application/json")
        .header("Cookie", adminCookie);
    builder.method(method,
        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private long id(HttpResponse<String> response) throws Exception {
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data/id").asLong();
  }

  @Test
  @DisplayName("deleteBug：软删 data:null → 详情 40401 → 再删 40401")
  void deleteBug() throws Exception {
    long bugId = id(send("POST", "/api/v1/products/" + productId + "/bugs",
        "{\"title\":\"软删 Bug\",\"openedBuilds\":\"1\"}"));

    HttpResponse<String> deleted = send("DELETE", "/api/v1/bugs/" + bugId, null);
    assertEquals(200, deleted.statusCode(), deleted.body());
    assertTrue(json.readTree(deleted.body()).at("/data").isNull(), deleted.body());

    HttpResponse<String> gone = send("GET", "/api/v1/bugs/" + bugId, null);
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());
    assertEquals(404, send("DELETE", "/api/v1/bugs/" + bugId, null).statusCode());
  }

  @Test
  @DisplayName("deleteTestCase：软删后详情 40401；deleteSuite：软删后详情 40401")
  void deleteTestCaseAndSuite() throws Exception {
    long caseId = id(send("POST", "/api/v1/products/" + productId + "/test-cases",
        "{\"title\":\"软删用例\"}"));
    assertEquals(200, send("DELETE", "/api/v1/test-cases/" + caseId, null).statusCode());
    assertEquals(404, send("GET", "/api/v1/test-cases/" + caseId, null).statusCode());

    long suiteId = id(send("POST", "/api/v1/products/" + productId + "/suites",
        "{\"name\":\"软删套件\"}"));
    assertEquals(200, send("DELETE", "/api/v1/suites/" + suiteId, null).statusCode());
    assertEquals(404, send("GET", "/api/v1/suites/" + suiteId, null).statusCode());
  }

  @Test
  @DisplayName("deleteLibrary：库内有未删用例 → 42203；空库软删成功")
  void deleteLibraryGuard() throws Exception {
    long libraryId = id(send("POST", "/api/v1/libraries", "{\"name\":\"软删库-" + System.nanoTime() + "\"}"));
    id(send("POST", "/api/v1/libraries/" + libraryId + "/test-cases", "{\"title\":\"库内用例\"}"));

    HttpResponse<String> guarded = send("DELETE", "/api/v1/libraries/" + libraryId, null);
    assertEquals(422, guarded.statusCode(), guarded.body());
    assertTrue(guarded.body().contains("42203"), guarded.body());
    assertEquals(200, send("GET", "/api/v1/libraries/" + libraryId, null).statusCode(), "守卫拒绝后库仍在");

    long emptyLibrary = id(send("POST", "/api/v1/libraries", "{\"name\":\"空库-" + System.nanoTime() + "\"}"));
    HttpResponse<String> deleted = send("DELETE", "/api/v1/libraries/" + emptyLibrary, null);
    assertEquals(200, deleted.statusCode(), deleted.body());
    assertEquals(404, send("GET", "/api/v1/libraries/" + emptyLibrary, null).statusCode());
  }

  @Test
  @DisplayName("deleteTestRun：软删后详情 40401")
  void deleteTestRun() throws Exception {
    long runId = id(send("POST", "/api/v1/products/" + productId + "/test-runs",
        "{\"executionId\":" + executionId + ",\"name\":\"软删测试单\","
            + "\"beginDate\":\"2026-01-10\",\"endDate\":\"2026-01-20\"}"));
    assertEquals(200, send("DELETE", "/api/v1/test-runs/" + runId, null).statusCode());
    HttpResponse<String> gone = send("GET", "/api/v1/test-runs/" + runId, null);
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());
  }

  @Test
  @DisplayName("deleteReport：软删后详情 40401，关联测试单 reportId 清空回写")
  void deleteReportClearsRunBackfill() throws Exception {
    long runId = id(send("POST", "/api/v1/products/" + productId + "/test-runs",
        "{\"executionId\":" + executionId + ",\"name\":\"报告回写测试单\","
            + "\"beginDate\":\"2026-01-10\",\"endDate\":\"2026-01-20\"}"));
    long reportId = id(send("POST", "/api/v1/executions/" + executionId + "/reports",
        "{\"title\":\"软删报告\",\"beginDate\":\"2026-01-10\",\"endDate\":\"2026-01-20\","
            + "\"testRunIds\":[" + runId + "]}", adminCookie));
    JsonNode runBefore = json.readTree(send("GET", "/api/v1/test-runs/" + runId, null).body()).at("/data");
    assertEquals(reportId, runBefore.at("/reportId").asLong(), runBefore.toString());

    assertEquals(200, send("DELETE", "/api/v1/reports/" + reportId, null).statusCode());
    assertEquals(404, send("GET", "/api/v1/reports/" + reportId, null).statusCode());

    JsonNode runAfter = json.readTree(send("GET", "/api/v1/test-runs/" + runId, null).body()).at("/data");
    assertEquals(0, runAfter.at("/reportId").asLong(), "报告软删须清空回写 reportId：" + runAfter);
  }
}
