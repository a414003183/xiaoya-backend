package net.zentao.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * T-8 测试单（quality 卡 §8）：start 落 realBeganAt；close 缺 realFinishedAt 或越界 → 42201；
 * blocked|done → activate → doing；record-result 幂等 upsert 同行不新增 + lastRun 三字段同步 + 非 doing → 42202。
 */
class TestRunStateMachineTest extends net.zentao.H2TestSupport {

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
        "{\"name\":\"测试单-" + System.nanoTime() + "\"}", adminCookie).body()).at("/data/id").asLong();
    // 项目 + 执行（V7 project 表）：execution 供测试单挂载（载荷口径同 TaskDataScopeIT）
    long projectId = json.readTree(send("POST", "/api/v1/projects",
        "{\"name\":\"测试单项目-" + System.nanoTime() + "\",\"beginDate\":\"2026-01-01\","
            + "\"endDate\":\"2026-12-31\",\"acl\":\"private\",\"productIds\":[" + productId + "]}",
        adminCookie).body()).at("/data/id").asLong();
    executionId = json.readTree(send("POST", "/api/v1/projects/" + projectId + "/executions",
        "{\"type\":\"sprint\",\"name\":\"迭代一\",\"beginDate\":\"2026-01-01\",\"endDate\":\"2026-06-30\"}",
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
    HttpResponse<String> created = send("POST", "/api/v1/products/" + productId + "/test-runs",
        "{\"executionId\":" + executionId + ",\"name\":\"" + name
            + "\",\"beginDate\":\"2026-01-10\",\"endDate\":\"2026-01-20\"}");
    assertEquals(200, created.statusCode(), created.body());
    return json.readTree(created.body()).at("/data/id").asLong();
  }

  private long createCase(String title) throws Exception {
    return json.readTree(send("POST", "/api/v1/products/" + productId + "/test-cases",
        "{\"title\":\"" + title + "\"}").body()).at("/data/id").asLong();
  }

  private JsonNode run(long id) throws Exception {
    return json.readTree(send("GET", "/api/v1/test-runs/" + id, null).body()).at("/data");
  }

  @Test
  @DisplayName("生命周期：wait→start 落 realBeganAt→block→activate→close（realFinishedAt 落）；done→activate→doing")
  void lifecycle() throws Exception {
    long id = createRun("生命周期");
    assertEquals("wait", run(id).at("/status").asText());

    HttpResponse<String> started = send("POST", "/api/v1/test-runs/" + id + "/start", null);
    assertEquals(200, started.statusCode(), started.body());
    JsonNode afterStart = run(id);
    assertEquals("doing", afterStart.at("/status").asText(), afterStart.toString());
    assertNotNull(afterStart.at("/realBeganAt").asText(), afterStart.toString());
    // 重复 start → 42202
    assertEquals(422, send("POST", "/api/v1/test-runs/" + id + "/start", null).statusCode());

    assertEquals(200, send("POST", "/api/v1/test-runs/" + id + "/block",
        "{\"comment\":\"被阻塞\"}").statusCode());
    assertEquals("blocked", run(id).at("/status").asText());
    assertEquals(200, send("POST", "/api/v1/test-runs/" + id + "/activate", null).statusCode());
    assertEquals("doing", run(id).at("/status").asText());

    HttpResponse<String> closed = send("POST", "/api/v1/test-runs/" + id + "/close",
        "{\"realFinishedAt\":\"2026-01-20T10:00:00Z\"}");
    assertEquals(200, closed.statusCode(), closed.body());
    JsonNode afterClose = run(id);
    assertEquals("done", afterClose.at("/status").asText(), afterClose.toString());
    assertEquals("2026-01-20T10:00:00Z", afterClose.at("/realFinishedAt").asText(), afterClose.toString());

    assertEquals(200, send("POST", "/api/v1/test-runs/" + id + "/activate", null).statusCode());
    assertEquals("doing", run(id).at("/status").asText());
  }

  @Test
  @DisplayName("close 守卫：缺 realFinishedAt → 42201；早于 beginDate / 晚于 endDate 次日 → 42201")
  void closeGuards() throws Exception {
    long id = createRun("关闭守卫");
    send("POST", "/api/v1/test-runs/" + id + "/start", null);

    HttpResponse<String> missing = send("POST", "/api/v1/test-runs/" + id + "/close", "{\"comment\":\"缺时间\"}");
    assertEquals(422, missing.statusCode(), missing.body());
    assertTrue(missing.body().contains("42201"), missing.body());

    HttpResponse<String> early = send("POST", "/api/v1/test-runs/" + id + "/close",
        "{\"realFinishedAt\":\"2026-01-09T10:00:00Z\"}");
    assertEquals(422, early.statusCode(), early.body());
    assertTrue(early.body().contains("42201"), early.body());

    HttpResponse<String> late = send("POST", "/api/v1/test-runs/" + id + "/close",
        "{\"realFinishedAt\":\"2026-01-22T10:00:00Z\"}");
    assertEquals(422, late.statusCode(), late.body());

    // 次日（endDate+1）可关
    HttpResponse<String> nextDay = send("POST", "/api/v1/test-runs/" + id + "/close",
        "{\"realFinishedAt\":\"2026-01-21T10:00:00Z\"}");
    assertEquals(200, nextDay.statusCode(), nextDay.body());
  }

  @Test
  @DisplayName("record-result：登记后行内结果更新、重复登记同行覆写不新增、lastRun 三字段同步、非 doing → 42202")
  void recordResult() throws Exception {
    long caseId = createCase("登记用例");
    long id = createRun("登记");
    send("POST", "/api/v1/test-runs/" + id + "/cases",
        "{\"caseIds\":[" + caseId + "]}");

    // 非 doing 登记 → 42202
    HttpResponse<String> beforeStart = send("POST",
        "/api/v1/test-runs/" + id + "/cases/" + caseId + "/result",
        "{\"result\":\"pass\"}");
    assertEquals(422, beforeStart.statusCode(), beforeStart.body());
    assertTrue(beforeStart.body().contains("42202"), beforeStart.body());

    send("POST", "/api/v1/test-runs/" + id + "/start", null);
    HttpResponse<String> recorded = send("POST",
        "/api/v1/test-runs/" + id + "/cases/" + caseId + "/result",
        "{\"result\":\"fail\",\"comment\":\"首登失败\"}");
    assertEquals(200, recorded.statusCode(), recorded.body());
    JsonNode view = json.readTree(recorded.body()).at("/data");
    assertEquals("fail", view.at("/result").asText(), view.toString());
    assertEquals("登记用例", view.at("/caseTitle").asText(), view.toString());

    // lastRun 同步到用例
    JsonNode caseView = json.readTree(send("GET", "/api/v1/test-cases/" + caseId, null).body()).at("/data");
    assertEquals("fail", caseView.at("/lastRunResult").asText(), caseView.toString());
    assertEquals("admin", caseView.at("/lastRunner").asText(), caseView.toString());
    assertTrue(!caseView.at("/lastRunAt").isNull(), caseView.toString());

    // 重复登记：同行覆写，不新增行
    HttpResponse<String> again = send("POST",
        "/api/v1/test-runs/" + id + "/cases/" + caseId + "/result",
        "{\"result\":\"pass\"}");
    assertEquals(200, again.statusCode(), again.body());
    JsonNode cases = json.readTree(send("GET", "/api/v1/test-runs/" + id + "/cases", null).body())
        .at("/data/items");
    assertEquals(1, cases.size(), cases.toString());
    assertEquals("pass", cases.get(0).at("/result").asText(), cases.toString());

    // 动态流 runCase（extra=result）
    JsonNode activities = json.readTree(send("GET", "/api/v1/test-runs/" + id + "/activities", null).body())
        .at("/data/items");
    assertTrue(activities.size() >= 2, activities.toString());
    assertEquals("runCase", activities.get(0).at("/action").asText(), activities.toString());
    assertEquals("pass", activities.get(0).at("/detail/0/newValue").asText(), activities.toString());

    // 指派执行人（行须存在）
    HttpResponse<String> assigned = send("POST",
        "/api/v1/test-runs/" + id + "/cases/" + caseId + "/assign",
        "{\"assignee\":\"admin\"}");
    assertEquals(200, assigned.statusCode(), assigned.body());
    assertEquals("admin", json.readTree(assigned.body()).at("/data/assignee").asText());
  }
}
