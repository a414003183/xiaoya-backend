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
 * T-10 测试报告（quality 卡 §8）：创建回填各 TestRun.reportId；executionId 不可改 → 40001；
 * 关联测试单须属该执行；列表随执行可读。
 */
class ReportApiTest extends net.zentao.H2TestSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private long productId;
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
    productId = json.readTree(send("POST", "/api/v1/products",
        "{\"name\":\"报告-" + System.nanoTime() + "\"}", adminCookie).body()).at("/data/id").asLong();
    long projectId = json.readTree(send("POST", "/api/v1/projects",
        "{\"name\":\"报告项目-" + System.nanoTime() + "\",\"beginDate\":\"2026-01-01\","
            + "\"endDate\":\"2026-12-31\",\"acl\":\"private\",\"productIds\":[" + productId + "]}",
        adminCookie).body()).at("/data/id").asLong();
    executionId = json.readTree(send("POST", "/api/v1/projects/" + projectId + "/executions",
        "{\"type\":\"sprint\",\"name\":\"报告迭代\",\"beginDate\":\"2026-01-01\",\"endDate\":\"2026-06-30\"}",
        adminCookie).body()).at("/data/id").asLong();
  }

  private HttpResponse<String> send(String method, String path, String body, String cookie) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Content-Type", "application/json");
    if (cookie != null) {
      builder.header("Cookie", cookie);
    }
    builder.method(method,
        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> send(String method, String path, String body) throws Exception {
    return send(method, path, body, adminCookie);
  }

  private long createRun(String name) throws Exception {
    return json.readTree(send("POST", "/api/v1/products/" + productId + "/test-runs",
        "{\"executionId\":" + executionId + ",\"name\":\"" + name
            + "\",\"beginDate\":\"2026-01-10\",\"endDate\":\"2026-01-20\"}").body()).at("/data/id").asLong();
  }

  @Test
  @DisplayName("创建回填 reportId；executionId 不可改 → 40001；越执行测试单 → 42201；testRunIds 变更重指")
  void createBackfillsAndGuards() throws Exception {
    long runA = createRun("单A");
    long runB = createRun("单B");

    HttpResponse<String> created = send("POST", "/api/v1/executions/" + executionId + "/reports",
        "{\"title\":\"一轮报告\",\"testRunIds\":[" + runA + "," + runB
            + "],\"beginDate\":\"2026-01-10\",\"endDate\":\"2026-01-20\"}");
    assertEquals(200, created.statusCode(), created.body());
    long reportId = json.readTree(created.body()).at("/data/id").asLong();

    assertEquals(reportId, json.readTree(send("GET", "/api/v1/test-runs/" + runA, null).body())
        .at("/data/reportId").asLong());
    assertEquals(reportId, json.readTree(send("GET", "/api/v1/test-runs/" + runB, null).body())
        .at("/data/reportId").asLong());

    // executionId 不可改
    int lock = json.readTree(send("GET", "/api/v1/reports/" + reportId, null).body())
        .at("/data/lockVersion").asInt();
    HttpResponse<String> immutable = send("PATCH", "/api/v1/reports/" + reportId,
        "{\"executionId\":999,\"lockVersion\":" + lock + "}");
    assertEquals(400, immutable.statusCode(), immutable.body());
    assertTrue(immutable.body().contains("40001"), immutable.body());

    // 越执行测试单
    HttpResponse<String> foreign = send("PATCH", "/api/v1/reports/" + reportId,
        "{\"testRunIds\":[99999],\"lockVersion\":" + lock + "}");
    assertEquals(422, foreign.statusCode(), foreign.body());
    int lockAfterForeign = json.readTree(send("GET", "/api/v1/reports/" + reportId, null).body())
        .at("/data/lockVersion").asInt();
    assertEquals(lock, lockAfterForeign, "foreign PATCH 应整体回滚（乐观锁不推进）");

    // testRunIds 收窄 → 被移出的测试单 reportId 清空
    HttpResponse<String> narrowed = send("PATCH", "/api/v1/reports/" + reportId,
        "{\"testRunIds\":[" + runA + "],\"lockVersion\":" + lockAfterForeign + "}");
    assertEquals(200, narrowed.statusCode(), narrowed.body());
    assertEquals(reportId, json.readTree(send("GET", "/api/v1/test-runs/" + runA, null).body())
        .at("/data/reportId").asLong());
    assertEquals(0, json.readTree(send("GET", "/api/v1/test-runs/" + runB, null).body())
        .at("/data/reportId").asLong());

    // 列表随执行可读
    JsonNode list = json.readTree(send("GET", "/api/v1/executions/" + executionId + "/reports", null).body())
        .at("/data/items");
    assertEquals(1, list.size(), list.toString());
  }
}
