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
 * T-8 计划关联（product 卡 §8）：link/unlink 幂等、closed 计划 link → 42202、
 * 跨产品对象 42203、objectType 白名单 42203、bug 分支为 P2 占位（40401/空列表）。
 */
class PlanLinkTest extends net.zentao.H2TestSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private long productId;
  private long otherProductId;

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
    productId = createProduct("关联产品");
    otherProductId = createProduct("关联产品他");
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

  private long createProduct(String name) throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/products",
        "{\"name\":\"" + name + "-" + System.nanoTime() + "\"}", adminCookie);
    assertEquals(200, created.statusCode(), created.body());
    return json.readTree(created.body()).at("/data/id").asLong();
  }

  private long createPlan(long product, String title) throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/products/" + product + "/plans",
        "{\"title\":\"" + title + "\"}", adminCookie);
    assertEquals(200, created.statusCode(), created.body());
    return json.readTree(created.body()).at("/data/id").asLong();
  }

  private long createStory(long product, String title) throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/products/" + product + "/stories",
        "{\"title\":\"" + title + "\"}", adminCookie);
    assertEquals(200, created.statusCode(), created.body());
    return json.readTree(created.body()).at("/data/id").asLong();
  }

  @Test
  @DisplayName("link 落 story.planId、幂等不重复；unlink 清空且再次 unlink 仍 ok；计划需求列表可读")
  void linkAndUnlink() throws Exception {
    long planId = createPlan(productId, "关联计划");
    long storyId = createStory(productId, "关联需求");

    HttpResponse<String> linked = send("POST", "/api/v1/plans/" + planId + "/link",
        "{\"objectType\":\"story\",\"ids\":[" + storyId + "]}", adminCookie);
    assertEquals(200, linked.statusCode(), linked.body());

    JsonNode story = json.readTree(send("GET", "/api/v1/stories/" + storyId, null, adminCookie).body()).at("/data");
    assertEquals(planId, story.at("/planId").asLong(), story.toString());

    HttpResponse<String> again = send("POST", "/api/v1/plans/" + planId + "/link",
        "{\"objectType\":\"story\",\"ids\":[" + storyId + "," + storyId + "]}", adminCookie);
    assertEquals(200, again.statusCode(), again.body());

    HttpResponse<String> stories = send("GET", "/api/v1/plans/" + planId + "/stories", null, adminCookie);
    assertEquals(1, json.readTree(stories.body()).at("/data/total").asLong(), stories.body());

    HttpResponse<String> unlinked = send("POST", "/api/v1/plans/" + planId + "/unlink",
        "{\"objectType\":\"story\",\"ids\":[" + storyId + "]}", adminCookie);
    assertEquals(200, unlinked.statusCode(), unlinked.body());
    JsonNode afterUnlink = json.readTree(send("GET", "/api/v1/stories/" + storyId, null, adminCookie).body())
        .at("/data");
    assertTrue(afterUnlink.at("/planId").isNull(), afterUnlink.toString());

    HttpResponse<String> unlinkAgain = send("POST", "/api/v1/plans/" + planId + "/unlink",
        "{\"objectType\":\"story\",\"ids\":[" + storyId + "]}", adminCookie);
    assertEquals(200, unlinkAgain.statusCode(), unlinkAgain.body());

    HttpResponse<String> activities = send("GET", "/api/v1/plans/" + planId + "/activities", null, adminCookie);
    assertTrue(activities.body().contains("linked"), activities.body());
    assertTrue(activities.body().contains("unlinked"), activities.body());
  }

  @Test
  @DisplayName("守卫：closed 计划 link → 42202；跨产品需求 42203；objectType 非法 42203；bug 分支真实现 42203/关联可读")
  void linkGuards() throws Exception {
    long planId = createPlan(productId, "守卫计划");
    long storyId = createStory(productId, "守卫需求");
    long foreignStory = createStory(otherProductId, "他产品需求");

    HttpResponse<String> crossProduct = send("POST", "/api/v1/plans/" + planId + "/link",
        "{\"objectType\":\"story\",\"ids\":[" + foreignStory + "]}", adminCookie);
    assertEquals(422, crossProduct.statusCode(), crossProduct.body());
    assertTrue(crossProduct.body().contains("42203"), crossProduct.body());

    HttpResponse<String> badType = send("POST", "/api/v1/plans/" + planId + "/link",
        "{\"objectType\":\"task\",\"ids\":[" + storyId + "]}", adminCookie);
    assertEquals(422, badType.statusCode(), badType.body());
    assertTrue(badType.body().contains("42203"), badType.body());

    HttpResponse<String> emptyIds = send("POST", "/api/v1/plans/" + planId + "/link",
        "{\"objectType\":\"story\",\"ids\":[]}", adminCookie);
    assertEquals(422, emptyIds.statusCode(), emptyIds.body());

    // bug 分支（P4 真实现）：不存在的 Bug → 42203（与 story 分支同语义）
    HttpResponse<String> bugLink = send("POST", "/api/v1/plans/" + planId + "/link",
        "{\"objectType\":\"bug\",\"ids\":[99999]}", adminCookie);
    assertEquals(422, bugLink.statusCode(), bugLink.body());
    assertTrue(bugLink.body().contains("42203"), bugLink.body());

    // bug 关联落 bug.planId 且计划 bug 列表可读；unlink 清空
    long bugId = createBug(productId, "守卫 Bug");
    HttpResponse<String> bugLinked = send("POST", "/api/v1/plans/" + planId + "/link",
        "{\"objectType\":\"bug\",\"ids\":[" + bugId + "]}", adminCookie);
    assertEquals(200, bugLinked.statusCode(), bugLinked.body());
    HttpResponse<String> bugs = send("GET", "/api/v1/plans/" + planId + "/bugs", null, adminCookie);
    assertEquals(1, json.readTree(bugs.body()).at("/data/total").asLong(), bugs.body());
    HttpResponse<String> bugUnlinked = send("POST", "/api/v1/plans/" + planId + "/unlink",
        "{\"objectType\":\"bug\",\"ids\":[" + bugId + "]}", adminCookie);
    assertEquals(200, bugUnlinked.statusCode(), bugUnlinked.body());
    HttpResponse<String> bugsAfter = send("GET", "/api/v1/plans/" + planId + "/bugs", null, adminCookie);
    assertEquals(0, json.readTree(bugsAfter.body()).at("/data/total").asLong(), bugsAfter.body());

    // closed 计划不可 link
    send("POST", "/api/v1/plans/" + planId + "/start", null, adminCookie);
    send("POST", "/api/v1/plans/" + planId + "/close", "{\"closedReason\":\"cancel\"}", adminCookie);
    HttpResponse<String> closedLink = send("POST", "/api/v1/plans/" + planId + "/link",
        "{\"objectType\":\"story\",\"ids\":[" + storyId + "]}", adminCookie);
    assertEquals(422, closedLink.statusCode(), closedLink.body());
    assertTrue(closedLink.body().contains("42202"), closedLink.body());
  }

  private long createBug(long product, String title) throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/products/" + product + "/bugs",
        "{\"title\":\"" + title + "\"}", adminCookie);
    assertEquals(200, created.statusCode(), created.body());
    return json.readTree(created.body()).at("/data/id").asLong();
  }
}
