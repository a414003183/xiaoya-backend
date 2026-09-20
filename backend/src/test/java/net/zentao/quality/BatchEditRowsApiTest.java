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
 * A-03 批量编辑统一（quality 卡 §5 批量动作契约口径 2026-09-19）：action=edit 读 params.rows=
 * [{id, lockVersion, …}] 逐行应用——带 lockVersion 成功、错 lockVersion 该行 error 40901、
 * 缺 lockVersion 该行 error 40901、其他行不受影响；其余动作仍读扁平 params + command.ids()。
 */
class BatchEditRowsApiTest extends net.zentao.ApiTestSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private long productId;

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
        "{\"name\":\"批量编辑-" + System.nanoTime() + "\"}"));
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

  private long createBug(String title) throws Exception {
    return id(send("POST", "/api/v1/products/" + productId + "/bugs",
        "{\"title\":\"" + title + "\",\"openedBuilds\":\"1\"}"));
  }

  private JsonNode bugBatch(String body) throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/bugs/batch", body);
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data/results");
  }

  @Test
  @DisplayName("Bug edit rows：成功/错锁/缺锁逐行结果，其余行不受影响")
  void bugEditRows() throws Exception {
    long ok = createBug("行一");
    long stale = createBug("行二");
    long missing = createBug("行三");

    JsonNode results = bugBatch("{\"ids\":[" + ok + "," + stale + "," + missing + "],\"action\":\"edit\","
        + "\"params\":{\"rows\":["
        + "{\"id\":" + ok + ",\"title\":\"行一改\",\"lockVersion\":0},"
        + "{\"id\":" + stale + ",\"title\":\"行二改\",\"lockVersion\":99},"
        + "{\"id\":" + missing + ",\"title\":\"行三改\"}]}}");

    assertTrue(results.get(0).at("/ok").asBoolean(), results.toString());
    assertEquals(false, results.get(1).at("/ok").asBoolean(), results.toString());
    assertTrue(results.get(1).at("/error").asText().startsWith("40901"), results.toString());
    assertEquals(false, results.get(2).at("/ok").asBoolean(), results.toString());
    assertEquals("40901:lockVersion required", results.get(2).at("/error").asText(), results.toString());

    assertEquals("行一改", json.readTree(send("GET", "/api/v1/bugs/" + ok, null).body())
        .at("/data/title").asText());
    assertEquals("行二", json.readTree(send("GET", "/api/v1/bugs/" + stale, null).body())
        .at("/data/title").asText(), "错锁行不得落地");
    assertEquals("行三", json.readTree(send("GET", "/api/v1/bugs/" + missing, null).body())
        .at("/data/title").asText(), "缺锁行不得落地");
  }

  @Test
  @DisplayName("Bug edit：rows 可写 PATCH 白名单字段（severity/priority）；缺 rows 整体 40001")
  void bugEditRowsFieldsAndShape() throws Exception {
    long bug = createBug("白名单行");
    JsonNode results = bugBatch("{\"ids\":[" + bug + "],\"action\":\"edit\",\"params\":{"
        + "\"rows\":[{\"id\":" + bug + ",\"severity\":1,\"priority\":4,\"lockVersion\":0}]}}");
    assertTrue(results.get(0).at("/ok").asBoolean(), results.toString());
    JsonNode after = json.readTree(send("GET", "/api/v1/bugs/" + bug, null).body()).at("/data");
    assertEquals(1, after.at("/severity").asInt(), after.toString());
    assertEquals(4, after.at("/priority").asInt(), after.toString());

    HttpResponse<String> noRows = send("POST", "/api/v1/bugs/batch",
        "{\"ids\":[" + bug + "],\"action\":\"edit\",\"params\":{}}");
    assertEquals(400, noRows.statusCode(), noRows.body());
    assertTrue(noRows.body().contains("40001"), noRows.body());
  }

  @Test
  @DisplayName("Bug 非 edit 动作不回归：扁平 params + command.ids()")
  void bugNonEditActionsUnchanged() throws Exception {
    long bug = createBug("扁平动作");
    JsonNode results = bugBatch("{\"ids\":[" + bug + "],\"action\":\"confirm\",\"params\":"
        + "{\"assignee\":\"admin\",\"comment\":\"批量确认\"}}");
    assertTrue(results.get(0).at("/ok").asBoolean(), results.toString());
    assertEquals(true, json.readTree(send("GET", "/api/v1/bugs/" + bug, null).body())
        .at("/data/confirmed").asBoolean());
  }

  @Test
  @DisplayName("TestCase edit rows：成功/错锁/缺锁逐行结果，其余行不受影响；review 仍走扁平 params")
  void testCaseEditRows() throws Exception {
    long ok = id(send("POST", "/api/v1/products/" + productId + "/test-cases", "{\"title\":\"用例行一\"}"));
    long stale = id(send("POST", "/api/v1/products/" + productId + "/test-cases", "{\"title\":\"用例行二\"}"));
    long missing = id(send("POST", "/api/v1/products/" + productId + "/test-cases", "{\"title\":\"用例行三\"}"));

    HttpResponse<String> response = send("POST", "/api/v1/test-cases/batch",
        "{\"ids\":[" + ok + "," + stale + "," + missing + "],\"action\":\"edit\",\"params\":{\"rows\":["
            + "{\"id\":" + ok + ",\"priority\":1,\"lockVersion\":0},"
            + "{\"id\":" + stale + ",\"priority\":2,\"lockVersion\":99},"
            + "{\"id\":" + missing + ",\"priority\":3}]}}");
    assertEquals(200, response.statusCode(), response.body());
    JsonNode results = json.readTree(response.body()).at("/data/results");

    assertTrue(results.get(0).at("/ok").asBoolean(), results.toString());
    assertTrue(results.get(1).at("/error").asText().startsWith("40901"), results.toString());
    assertEquals("40901:lockVersion required", results.get(2).at("/error").asText(), results.toString());

    assertEquals(1, json.readTree(send("GET", "/api/v1/test-cases/" + ok, null).body())
        .at("/data/priority").asInt());
    assertEquals(3, json.readTree(send("GET", "/api/v1/test-cases/" + stale, null).body())
        .at("/data/priority").asInt(), "错锁行保持默认 3");
    assertEquals(3, json.readTree(send("GET", "/api/v1/test-cases/" + missing, null).body())
        .at("/data/priority").asInt(), "缺锁行保持默认 3");
  }
}
