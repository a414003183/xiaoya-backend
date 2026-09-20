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
 * T-4 用例（quality 卡 §8）：仅 wait 可 review（42202）；pass → normal 且 reviewers 追加/reviewedAt 落；
 * clarify 保持 wait；needReview 创建进 wait；标记态三态直改生效、改 wait → 40001；steps 整体替换。
 */
class TestCaseStateMachineTest extends net.zentao.H2TestSupport {

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
    HttpResponse<String> created = send("POST", "/api/v1/products",
        "{\"name\":\"用例状态机-" + System.nanoTime() + "\"}", adminCookie);
    productId = json.readTree(created.body()).at("/data/id").asLong();
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

  private long createCase(String title, boolean needReview, String steps) throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/products/" + productId + "/test-cases",
        "{\"title\":\"" + title + "\",\"needReview\":" + needReview + ",\"steps\":" + steps + "}");
    assertEquals(200, created.statusCode(), created.body());
    return json.readTree(created.body()).at("/data/id").asLong();
  }

  private JsonNode detail(long id) throws Exception {
    return json.readTree(send("GET", "/api/v1/test-cases/" + id, null).body()).at("/data");
  }

  @Test
  @DisplayName("评审流：needReview 创建进 wait；仅 wait 可 review；pass → normal 且 reviewers 追加；clarify 保持 wait")
  void reviewFlow() throws Exception {
    long id = createCase("评审流", true, "[]");
    assertEquals("wait", detail(id).at("/status").asText());

    // 非 wait 不可 review：先建 normal 用例
    long normal = createCase("免评审", false, "[]");
    assertEquals("normal", detail(normal).at("/status").asText());
    HttpResponse<String> reviewNormal = send("POST", "/api/v1/test-cases/" + normal + "/review",
        "{\"result\":\"pass\"}");
    assertEquals(422, reviewNormal.statusCode(), reviewNormal.body());
    assertTrue(reviewNormal.body().contains("42202"), reviewNormal.body());

    HttpResponse<String> clarified = send("POST", "/api/v1/test-cases/" + id + "/review",
        "{\"result\":\"clarify\",\"comment\":\"再改改\"}");
    assertEquals(200, clarified.statusCode(), clarified.body());
    JsonNode afterClarify = detail(id);
    assertEquals("wait", afterClarify.at("/status").asText(), afterClarify.toString());
    assertTrue(afterClarify.at("/reviewedAt").isNull(), afterClarify.toString());

    HttpResponse<String> passed = send("POST", "/api/v1/test-cases/" + id + "/review",
        "{\"result\":\"pass\"}");
    assertEquals(200, passed.statusCode(), passed.body());
    JsonNode afterPass = detail(id);
    assertEquals("normal", afterPass.at("/status").asText(), afterPass.toString());
    assertEquals("admin", afterPass.at("/reviewers/0").asText(), afterPass.toString());
    assertTrue(!afterPass.at("/reviewedAt").isNull(), afterPass.toString());

    JsonNode activities = json.readTree(send("GET", "/api/v1/test-cases/" + id + "/activities", null).body())
        .at("/data/items");
    assertTrue(activities.size() >= 3, activities.toString());
    assertEquals("clarify", activities.get(1).at("/detail/0/newValue").asText(), activities.toString());
    assertEquals("pass", activities.get(0).at("/detail/0/newValue").asText(), activities.toString());
  }

  @Test
  @DisplayName("标记态例外：normal↔blocked↔investigate 直改生效；改 wait → 40001；wait 态 PATCH status → 40001")
  void markerStatuses() throws Exception {
    long id = createCase("标记态", false, "[]");
    int lock = detail(id).at("/lockVersion").asInt();

    HttpResponse<String> toBlocked = send("PATCH", "/api/v1/test-cases/" + id,
        "{\"status\":\"blocked\",\"lockVersion\":" + lock + "}");
    assertEquals(200, toBlocked.statusCode(), toBlocked.body());
    lock = detail(id).at("/lockVersion").asInt();

    HttpResponse<String> toInvestigate = send("PATCH", "/api/v1/test-cases/" + id,
        "{\"status\":\"investigate\",\"lockVersion\":" + lock + "}");
    assertEquals(200, toInvestigate.statusCode(), toInvestigate.body());
    lock = detail(id).at("/lockVersion").asInt();

    HttpResponse<String> toWait = send("PATCH", "/api/v1/test-cases/" + id,
        "{\"status\":\"wait\",\"lockVersion\":" + lock + "}");
    assertEquals(400, toWait.statusCode(), toWait.body());
    assertTrue(toWait.body().contains("40001"), toWait.body());

    long waiting = createCase("待评审", true, "[]");
    HttpResponse<String> waitToBlocked = send("PATCH", "/api/v1/test-cases/" + waiting,
        "{\"status\":\"blocked\",\"lockVersion\":0}");
    assertEquals(400, waitToBlocked.statusCode(), waitToBlocked.body());
  }

  @Test
  @DisplayName("steps 整体替换：旧两行删新一行落；sort 缺省按行号；步骤校验（缺 description → 42201）")
  void stepsReplace() throws Exception {
    long id = createCase("步骤", false,
        "[{\"description\":\"步骤一\",\"expects\":\"结果一\"},{\"description\":\"步骤二\"}]");
    JsonNode two = detail(id).at("/steps");
    assertEquals(2, two.size(), two.toString());
    assertEquals(1, two.get(0).at("/sort").asInt());
    assertEquals(2, two.get(1).at("/sort").asInt());

    int lock = detail(id).at("/lockVersion").asInt();
    HttpResponse<String> replaced = send("PATCH", "/api/v1/test-cases/" + id,
        "{\"steps\":[{\"description\":\"新步骤\"}],\"lockVersion\":" + lock + "}");
    assertEquals(200, replaced.statusCode(), replaced.body());
    JsonNode one = detail(id).at("/steps");
    assertEquals(1, one.size(), one.toString());
    assertEquals("新步骤", one.get(0).at("/description").asText());
    assertTrue(one.get(0).at("/expects").isNull(), one.toString());

    lock = detail(id).at("/lockVersion").asInt();
    HttpResponse<String> invalid = send("PATCH", "/api/v1/test-cases/" + id,
        "{\"steps\":[{\"expects\":\"缺描述\"}],\"lockVersion\":" + lock + "}");
    assertEquals(422, invalid.statusCode(), invalid.body());
    assertTrue(invalid.body().contains("42201"), invalid.body());
  }

  @Test
  @DisplayName("批量创建 ≤50 逐条结果；批量 review 部分成功（wait 过、normal 拒）")
  void batchCreateAndReview() throws Exception {
    HttpResponse<String> batch = send("POST", "/api/v1/products/" + productId + "/test-cases/batch",
        "{\"items\":[{\"title\":\"批一\",\"needReview\":true},{\"title\":\"\"}]}");
    assertEquals(200, batch.statusCode(), batch.body());
    JsonNode results = json.readTree(batch.body()).at("/data/results");
    assertEquals(2, results.size(), results.toString());
    assertTrue(results.get(0).at("/ok").asBoolean());
    assertTrue(!results.get(1).at("/ok").asBoolean(), results.toString());
    long waiting = results.get(0).at("/id").asLong();

    HttpResponse<String> actions = send("POST", "/api/v1/test-cases/batch",
        "{\"ids\":[" + waiting + "],\"action\":\"review\",\"params\":{\"result\":\"pass\"}}");
    assertEquals(200, actions.statusCode(), actions.body());
    assertTrue(json.readTree(actions.body()).at("/data/results/0/ok").asBoolean(), actions.body());
    assertEquals("normal", detail(waiting).at("/status").asText());
  }
}
