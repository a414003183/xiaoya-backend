package net.zentao.requirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import net.zentao.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * T-4 数据权限 IT（requirement 卡 §8，Testcontainers MySQL 8.4）：
 * private 产品外人列表 0 条 / 详情 40302；超管全见；乐观锁 40901；createdAt 区间过滤。
 */
class StoryAclIT extends MySqlContainerSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private static long storyGroupId;

  @BeforeEach
  void login() throws Exception {
    adminCookie = loginAs("admin", "admin123");
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

  private long ensureGroup() throws Exception {
    if (storyGroupId != 0) {
      return storyGroupId;
    }
    HttpResponse<String> group = send("POST", "/api/v1/groups", "{\"name\":\"IT 需求组\"}", adminCookie);
    assertEquals(200, group.statusCode(), group.body());
    storyGroupId = json.readTree(group.body()).at("/data/id").asLong();
    send("PUT", "/api/v1/groups/" + storyGroupId + "/privileges",
        "{\"codes\":[\"product-view\",\"story-view\",\"story-create\",\"story-edit\"]}", adminCookie);
    return storyGroupId;
  }

  private void ensureAccount(String account) throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"" + account
            + "\",\"groupIds\":[" + ensureGroup() + "]}",
        adminCookie);
    assertTrue(created.statusCode() == 200 || created.statusCode() == 422, created.body());
  }

  @Test
  @DisplayName("private 产品下的需求：外人 0 条 + 详情 40302；白名单可见；超管全见；乐观锁 40901")
  void aclOnRealDatabase() throws Exception {
    ensureAccount("it-story-dev");
    ensureAccount("it-story-guest");
    HttpResponse<String> product = send("POST", "/api/v1/products",
        "{\"name\":\"IT 私有需求产品\",\"acl\":\"private\",\"whitelist\":[\"it-story-dev\"]}", adminCookie);
    assertEquals(200, product.statusCode(), product.body());
    long productId = json.readTree(product.body()).at("/data/id").asLong();

    HttpResponse<String> story = send("POST", "/api/v1/products/" + productId + "/stories",
        "{\"title\":\"IT 私有需求\"}", adminCookie);
    assertEquals(200, story.statusCode(), story.body());
    long storyId = json.readTree(story.body()).at("/data/id").asLong();

    String devCookie = loginAs("it-story-dev", "secret123");
    String guestCookie = loginAs("it-story-guest", "secret123");

    HttpResponse<String> devList = send("GET", "/api/v1/products/" + productId + "/stories", null, devCookie);
    assertEquals(1, json.readTree(devList.body()).at("/data/total").asLong(), devList.body());
    assertEquals(200, send("GET", "/api/v1/stories/" + storyId, null, devCookie).statusCode());

    HttpResponse<String> guestList = send("GET", "/api/v1/products/" + productId + "/stories", null, guestCookie);
    assertEquals(0, json.readTree(guestList.body()).at("/data/total").asLong(), guestList.body());
    HttpResponse<String> guestDetail = send("GET", "/api/v1/stories/" + storyId, null, guestCookie);
    assertEquals(403, guestDetail.statusCode(), guestDetail.body());
    assertTrue(guestDetail.body().contains("40302"), guestDetail.body());

    JsonNode adminList = json.readTree(send("GET", "/api/v1/products/" + productId + "/stories", null, adminCookie).body());
    assertEquals(1, adminList.at("/data/total").asLong(), adminList.toString());

    HttpResponse<String> stale = send("PATCH", "/api/v1/stories/" + storyId,
        "{\"title\":\"改名\",\"lockVersion\":77}", adminCookie);
    assertEquals(409, stale.statusCode(), stale.body());
    assertTrue(stale.body().contains("40901"), stale.body());

    String today = LocalDate.now().toString();
    HttpResponse<String> ranged = send("GET",
        "/api/v1/products/" + productId + "/stories?filters%5BcreatedAt%5D=" + today + ".." + today,
        null, adminCookie);
    assertEquals(1, json.readTree(ranged.body()).at("/data/total").asLong(), ranged.body());
  }
}
