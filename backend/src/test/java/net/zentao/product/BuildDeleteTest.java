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

/** T-9 构建（product 卡 §8）：被发布引用时 DELETE → 42203；删除后详情 40401；release/build link 幂等。 */
class BuildDeleteTest extends net.zentao.H2TestSupport {

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
    HttpResponse<String> product = send("POST", "/api/v1/products",
        "{\"name\":\"构建产品-" + System.nanoTime() + "\"}", adminCookie);
    productId = json.readTree(product.body()).at("/data/id").asLong();
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

  private long createBuild(String name) throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/products/" + productId + "/builds",
        "{\"name\":\"" + name + "\"}", adminCookie);
    assertEquals(200, created.statusCode(), created.body());
    return json.readTree(created.body()).at("/data/id").asLong();
  }

  @Test
  @DisplayName("删除守卫：被发布 buildId 引用 → 42203；未引用删除成功且详情 40401；builder 默认当前账号")
  void deleteGuard() throws Exception {
    long referenced = createBuild("被引用构建");
    long free = createBuild("自由构建");

    JsonNode build = json.readTree(send("GET", "/api/v1/builds/" + free, null, adminCookie).body()).at("/data");
    assertEquals("admin", build.at("/builder").asText(), build.toString());
    assertTrue(build.at("/buildDate").isTextual(), build.toString());

    HttpResponse<String> release = send("POST", "/api/v1/products/" + productId + "/releases",
        "{\"name\":\"引用构建的发布\",\"releaseDate\":\"2026-09-18\",\"buildId\":" + referenced + "}", adminCookie);
    assertEquals(200, release.statusCode(), release.body());

    HttpResponse<String> blocked = send("DELETE", "/api/v1/builds/" + referenced, null, adminCookie);
    assertEquals(422, blocked.statusCode(), blocked.body());
    assertTrue(blocked.body().contains("42203"), blocked.body());

    HttpResponse<String> deleted = send("DELETE", "/api/v1/builds/" + free, null, adminCookie);
    assertEquals(200, deleted.statusCode(), deleted.body());
    HttpResponse<String> gone = send("GET", "/api/v1/builds/" + free, null, adminCookie);
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());
  }

  @Test
  @DisplayName("release/build link 幂等：重复 link 不重复计入，unlink 后移除；跨产品需求 42203")
  void linkIdempotent() throws Exception {
    long story = json.readTree(send("POST", "/api/v1/products/" + productId + "/stories",
        "{\"title\":\"关联需求\"}", adminCookie).body()).at("/data/id").asLong();
    long buildId = createBuild("关联构建");
    long releaseId = json.readTree(send("POST", "/api/v1/products/" + productId + "/releases",
        "{\"name\":\"关联发布\",\"releaseDate\":\"2026-09-18\"}", adminCookie).body()).at("/data/id").asLong();

    for (int i = 0; i < 2; i++) {
      HttpResponse<String> linked = send("POST", "/api/v1/releases/" + releaseId + "/link",
          "{\"objectType\":\"story\",\"ids\":[" + story + "]}", adminCookie);
      assertEquals(200, linked.statusCode(), linked.body());
    }
    JsonNode release = json.readTree(send("GET", "/api/v1/releases/" + releaseId, null, adminCookie).body())
        .at("/data");
    assertEquals(1, release.at("/storyIds").size(), release.toString());

    HttpResponse<String> buildLink = send("POST", "/api/v1/builds/" + buildId + "/link",
        "{\"objectType\":\"story\",\"ids\":[" + story + "]}", adminCookie);
    assertEquals(200, buildLink.statusCode(), buildLink.body());
    assertEquals(1, json.readTree(buildLink.body()).at("/data/storyIds").size(), buildLink.body());

    HttpResponse<String> unlinked = send("POST", "/api/v1/releases/" + releaseId + "/unlink",
        "{\"objectType\":\"story\",\"ids\":[" + story + "]}", adminCookie);
    assertEquals(0, json.readTree(unlinked.body()).at("/data/storyIds").size(), unlinked.body());

    HttpResponse<String> otherProduct = send("POST", "/api/v1/products",
        "{\"name\":\"构建产品他-" + System.nanoTime() + "\"}", adminCookie);
    long otherId = json.readTree(otherProduct.body()).at("/data/id").asLong();
    long foreignStory = json.readTree(send("POST", "/api/v1/products/" + otherId + "/stories",
        "{\"title\":\"他产品需求\"}", adminCookie).body()).at("/data/id").asLong();
    HttpResponse<String> crossProduct = send("POST", "/api/v1/builds/" + buildId + "/link",
        "{\"objectType\":\"story\",\"ids\":[" + foreignStory + "]}", adminCookie);
    assertEquals(422, crossProduct.statusCode(), crossProduct.body());
    assertTrue(crossProduct.body().contains("42203"), crossProduct.body());
  }

  @Test
  @DisplayName("发布/构建列表与关联需求列表可读；发布 terminate 后 link → 42202")
  void releaseListingAndTerminateGuard() throws Exception {
    long story = json.readTree(send("POST", "/api/v1/products/" + productId + "/stories",
        "{\"title\":\"发布列表需求\"}", adminCookie).body()).at("/data/id").asLong();
    long releaseId = json.readTree(send("POST", "/api/v1/products/" + productId + "/releases",
        "{\"name\":\"列表发布\",\"releaseDate\":\"2026-09-18\",\"storyIds\":[" + story + "]}", adminCookie).body())
        .at("/data/id").asLong();

    HttpResponse<String> list = send("GET", "/api/v1/products/" + productId + "/releases", null, adminCookie);
    assertEquals(1, json.readTree(list.body()).at("/data/total").asLong(), list.body());

    HttpResponse<String> stories = send("GET", "/api/v1/releases/" + releaseId + "/stories", null, adminCookie);
    assertEquals(1, json.readTree(stories.body()).at("/data/total").asLong(), stories.body());

    HttpResponse<String> milestones = send("GET",
        "/api/v1/products/" + productId + "/releases?filters%5Bstatus%5D=normal", null, adminCookie);
    assertEquals(1, json.readTree(milestones.body()).at("/data/total").asLong(), milestones.body());

    send("POST", "/api/v1/releases/" + releaseId + "/terminate", "{}", adminCookie);
    HttpResponse<String> terminated = send("POST", "/api/v1/releases/" + releaseId + "/link",
        "{\"objectType\":\"story\",\"ids\":[" + story + "]}", adminCookie);
    assertEquals(422, terminated.statusCode(), terminated.body());
    assertTrue(terminated.body().contains("42202"), terminated.body());
  }
}
