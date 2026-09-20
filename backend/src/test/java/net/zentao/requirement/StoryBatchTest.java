package net.zentao.requirement;

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
import org.springframework.boot.test.context.SpringBootTest;

/** T-4 批量（requirement 卡 §8）：批量创建部分成功逐项 {index,ok,id,error}；批量动作逐项结果与越权 40301。 */
class StoryBatchTest extends net.zentao.H2TestSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private long productId;
  private static long restrictedGroupId;

  @BeforeEach
  void prepare() throws Exception {
    adminCookie = loginAs("admin", "admin123");
    HttpResponse<String> created = send("POST", "/api/v1/products",
        "{\"name\":\"批量需求产品\",\"acl\":\"public\"}", adminCookie);
    assertEquals(200, created.statusCode(), created.body());
    productId = json.readTree(created.body()).at("/data/id").asLong();
  }

  private String loginAs(String account, String password) throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/session",
        "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}", null);
    assertEquals(200, response.statusCode(), response.body());
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
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

  /** 只读组：有 story-view 但没有 story-close（批量动作越权用例）。 */
  private long restrictedGroup() throws Exception {
    if (restrictedGroupId != 0) {
      return restrictedGroupId;
    }
    HttpResponse<String> group = send("POST", "/api/v1/groups", "{\"name\":\"需求只读组\"}", adminCookie);
    assertEquals(200, group.statusCode(), group.body());
    restrictedGroupId = json.readTree(group.body()).at("/data/id").asLong();
    send("PUT", "/api/v1/groups/" + restrictedGroupId + "/privileges",
        "{\"codes\":[\"product-view\",\"story-view\"]}", adminCookie);
    return restrictedGroupId;
  }

  private long createStory(String fields) throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/products/" + productId + "/stories", fields, adminCookie);
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data/id").asLong();
  }

  @Test
  @DisplayName("批量创建：≤50 条逐条校验，合法行入库、非法行带 code:message 且不影响其他行")
  void batchCreate() throws Exception {
    String body = "{\"items\":["
        + "{\"title\":\"批量甲\"},"
        + "{\"title\":\"\"},"
        + "{\"title\":\"批量丙\",\"priority\":7},"
        + "{\"title\":\"批量丁\",\"type\":\"epic\"}]}";
    HttpResponse<String> response = send("POST", "/api/v1/products/" + productId + "/stories/batch", body, adminCookie);
    assertEquals(200, response.statusCode(), response.body());
    JsonNode results = json.readTree(response.body()).at("/data/results");
    assertEquals(4, results.size(), response.body());
    assertTrue(results.get(0).at("/ok").asBoolean(), response.body());
    assertEquals(0, results.get(0).at("/index").asInt(), response.body());
    assertEquals(false, results.get(1).at("/ok").asBoolean(), response.body());
    assertTrue(results.get(1).at("/error").asText().startsWith("42201"), response.body());
    assertEquals(false, results.get(2).at("/ok").asBoolean(), response.body());
    assertTrue(results.get(3).at("/ok").asBoolean(), response.body());

    HttpResponse<String> list = send("GET", "/api/v1/products/" + productId + "/stories", null, adminCookie);
    assertEquals(2, json.readTree(list.body()).at("/data/total").asLong(), list.body());

    String tooMany = "{\"items\":[" + "{\"title\":\"x\"},".repeat(50) + "{\"title\":\"y\"}]}";
    HttpResponse<String> overLimit = send("POST", "/api/v1/products/" + productId + "/stories/batch", tooMany,
        adminCookie);
    assertEquals(422, overLimit.statusCode(), overLimit.body());
  }

  @Test
  @DisplayName("批量动作：close 部分成功逐项结果；不支持动作 40001；无动作码账号 40301")
  void batchActions() throws Exception {
    long active = createStory("{\"title\":\"批量动作甲\",\"needNotReview\":true}");
    send("POST", "/api/v1/stories/" + active + "/submit-review", "{}", adminCookie);
    long draft = createStory("{\"title\":\"批量动作乙\"}");

    HttpResponse<String> response = send("POST", "/api/v1/stories/batch",
        "{\"ids\":[" + active + "," + draft + "],\"action\":\"close\",\"params\":{\"closedReason\":\"done\"}}",
        adminCookie);
    assertEquals(200, response.statusCode(), response.body());
    JsonNode results = json.readTree(response.body()).at("/data/results");
    assertTrue(results.get(0).at("/ok").asBoolean(), response.body());
    assertEquals(false, results.get(1).at("/ok").asBoolean(), response.body());
    assertTrue(results.get(1).at("/error").asText().startsWith("42202"), response.body());

    HttpResponse<String> unsupported = send("POST", "/api/v1/stories/batch",
        "{\"ids\":[" + active + "],\"action\":\"frobnicate\"}", adminCookie);
    assertEquals(400, unsupported.statusCode(), unsupported.body());

    // 只读账号：story-view 有、story-close 无 → 40301
    send("POST", "/api/v1/accounts",
        "{\"account\":\"batch-reader\",\"password\":\"secret123\",\"realName\":\"只读者\",\"groupIds\":["
            + restrictedGroup() + "]}",
        adminCookie);
    String readerCookie = loginAs("batch-reader", "secret123");
    HttpResponse<String> forbidden = send("POST", "/api/v1/stories/batch",
        "{\"ids\":[" + active + "],\"action\":\"close\",\"params\":{\"closedReason\":\"done\"}}", readerCookie);
    assertEquals(403, forbidden.statusCode(), forbidden.body());
    assertTrue(forbidden.body().contains("40301"), forbidden.body());
  }
}
