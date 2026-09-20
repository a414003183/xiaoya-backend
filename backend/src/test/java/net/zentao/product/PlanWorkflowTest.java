package net.zentao.product;

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

/**
 * T-8 计划（product 卡 §8）：4 动作迁移矩阵（42202）、finish 落 finishedAt、
 * close 守卫与 done/cancel 双分支、activate 清三值、父子两级约束、父计划聚合联动。
 */
class PlanWorkflowTest extends net.zentao.H2TestSupport {

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
        "{\"name\":\"计划产品-" + System.nanoTime() + "\"}", adminCookie);
    assertEquals(200, created.statusCode(), created.body());
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

  private long createPlan(String body) throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/products/" + productId + "/plans", body, adminCookie);
    assertEquals(200, created.statusCode(), created.body());
    return json.readTree(created.body()).at("/data/id").asLong();
  }

  private JsonNode act(String action, long planId, String body) throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/plans/" + planId + "/" + action, body, adminCookie);
    assertEquals(200, response.statusCode(), action + " 失败：" + response.body());
    return json.readTree(response.body()).at("/data");
  }

  @Test
  @DisplayName("迁移矩阵：start→doing→finish→done→activate→doing；非法迁移 42202；finish 落 finishedAt")
  void stateMachine() throws Exception {
    long planId = createPlan("{\"title\":\"计划状态机\"}");

    assertEquals(422, send("POST", "/api/v1/plans/" + planId + "/finish", "{}", adminCookie).statusCode());
    assertEquals("doing", act("start", planId, null).at("/status").asText());
    assertEquals(422, send("POST", "/api/v1/plans/" + planId + "/start", null, adminCookie).statusCode());

    JsonNode done = act("finish", planId, "{\"comment\":\"完成\"}");
    assertEquals("done", done.at("/status").asText());
    assertTrue(done.at("/finishedAt").isTextual(), done.toString());

    JsonNode doing = act("activate", planId, "{}");
    assertEquals("doing", doing.at("/status").asText());
    assertTrue(doing.at("/finishedAt").isNull(), doing.toString());
    assertTrue(doing.at("/closedAt").isNull(), doing.toString());
    assertTrue(doing.at("/closedReason").isNull(), doing.toString());
  }

  @Test
  @DisplayName("close 守卫：缺/错 closedReason → 42201；done 双分支落 finishedAt+cancel 分支只落 closedAt")
  void closeBranches() throws Exception {
    long first = createPlan("{\"title\":\"关闭-done\"}");
    act("start", first, null);
    HttpResponse<String> missing = send("POST", "/api/v1/plans/" + first + "/close", "{}", adminCookie);
    assertEquals(422, missing.statusCode(), missing.body());
    assertTrue(missing.body().contains("closedReason"), missing.body());
    HttpResponse<String> bad = send("POST", "/api/v1/plans/" + first + "/close",
        "{\"closedReason\":\"whatever\"}", adminCookie);
    assertEquals(422, bad.statusCode(), bad.body());

    JsonNode closedDone = act("close", first, "{\"closedReason\":\"done\"}");
    assertEquals("closed", closedDone.at("/status").asText());
    assertTrue(closedDone.at("/finishedAt").isTextual(), closedDone.toString());
    assertTrue(closedDone.at("/closedAt").isTextual(), closedDone.toString());

    long second = createPlan("{\"title\":\"关闭-cancel\"}");
    act("start", second, null);
    JsonNode closedCancel = act("close", second, "{\"closedReason\":\"cancel\"}");
    assertTrue(closedCancel.at("/closedAt").isTextual(), closedCancel.toString());
    assertTrue(closedCancel.at("/finishedAt").isNull(), closedCancel.toString());
  }

  @Test
  @DisplayName("父子两级：父不许再挂父 42201、跨产品父 42201、endDate < beginDate 42201")
  void parentRules() throws Exception {
    long parent = createPlan("{\"title\":\"一级计划\"}");
    long child = createPlan("{\"title\":\"二级计划\",\"parentId\":" + parent + "}");

    HttpResponse<String> nested = send("POST", "/api/v1/products/" + productId + "/plans",
        "{\"title\":\"三级计划\",\"parentId\":" + child + "}", adminCookie);
    assertEquals(422, nested.statusCode(), nested.body());
    assertTrue(nested.body().contains("parentId"), nested.body());

    HttpResponse<String> otherProduct = send("POST", "/api/v1/products",
        "{\"name\":\"计划产品他-" + System.nanoTime() + "\"}", adminCookie);
    long otherId = json.readTree(otherProduct.body()).at("/data/id").asLong();
    HttpResponse<String> crossProduct = send("POST", "/api/v1/products/" + otherId + "/plans",
        "{\"title\":\"跨产品子计划\",\"parentId\":" + parent + "}", adminCookie);
    assertEquals(422, crossProduct.statusCode(), crossProduct.body());

    HttpResponse<String> badRange = send("POST", "/api/v1/products/" + productId + "/plans",
        "{\"title\":\"日期倒挂\",\"beginDate\":\"2026-05-01\",\"endDate\":\"2026-04-01\"}", adminCookie);
    assertEquals(422, badRange.statusCode(), badRange.body());
    assertTrue(badRange.body().contains("endDate"), badRange.body());

    HttpResponse<String> stale = send("PATCH", "/api/v1/plans/" + parent,
        "{\"title\":\"改名\",\"lockVersion\":5}", adminCookie);
    assertEquals(409, stale.statusCode(), stale.body());
  }

  @Test
  @DisplayName("父计划聚合联动：子 doing → 父 doing，子全 closed → 父 closed + 动态流 closedbychild")
  void parentRollup() throws Exception {
    long parent = createPlan("{\"title\":\"聚合父计划\"}");
    long firstChild = createPlan("{\"title\":\"聚合子甲\",\"parentId\":" + parent + "}");
    long secondChild = createPlan("{\"title\":\"聚合子乙\",\"parentId\":" + parent + "}");

    act("start", firstChild, null);
    HttpResponse<String> parentAfterStart = send("GET", "/api/v1/plans/" + parent, null, adminCookie);
    assertEquals("doing", json.readTree(parentAfterStart.body()).at("/data/status").asText(), parentAfterStart.body());

    act("finish", firstChild, "{}");
    act("start", secondChild, null);
    act("close", secondChild, "{\"closedReason\":\"done\"}");
    act("close", firstChild, "{\"closedReason\":\"done\"}");

    HttpResponse<String> parentAfterClose = send("GET", "/api/v1/plans/" + parent, null, adminCookie);
    assertEquals("closed", json.readTree(parentAfterClose.body()).at("/data/status").asText(),
        parentAfterClose.body());

    HttpResponse<String> activities = send("GET", "/api/v1/plans/" + parent + "/activities", null, adminCookie);
    assertTrue(activities.body().contains("closedbychild"), activities.body());
  }
}
