package net.zentao.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.zentao.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * T-9 发布跨域副作用 IT（product 卡 §8，Testcontainers MySQL 8.4）：
 * 创建后需求 stage=released、需求侧落 linked2release 动态流、notifyAccounts 落通知行；重复 terminate → 42202。
 */
class ReleaseSideEffectIT extends MySqlContainerSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private static long groupId;

  @BeforeEach
  void prepare() throws Exception {
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
    if (groupId != 0) {
      return groupId;
    }
    HttpResponse<String> group = send("POST", "/api/v1/roles", "{\"name\":\"IT 发布组\"}", adminCookie);
    assertEquals(200, group.statusCode(), group.body());
    groupId = json.readTree(group.body()).at("/data/id").asLong();
    send("PUT", "/api/v1/roles/" + groupId + "/privileges",
        "{\"codes\":[\"product-view\",\"story-view\",\"release-view\",\"release-create\",\"release-terminate\"]}",
        adminCookie);
    return groupId;
  }

  @Test
  @DisplayName("创建发布：需求 stage → released + 双侧动态流 + notifyAccounts 收到通知；重复 terminate 42202")
  void releaseSideEffects() throws Exception {
    HttpResponse<String> account = send("POST", "/api/v1/accounts",
        "{\"account\":\"it-release-qa\",\"password\":\"secret123\",\"realName\":\"发布观察员\",\"roleIds\":["
            + ensureGroup() + "]}",
        adminCookie);
    assertEquals(200, account.statusCode(), account.body());

    HttpResponse<String> product = send("POST", "/api/v1/products",
        "{\"name\":\"IT 发布产品\"}", adminCookie);
    long productId = json.readTree(product.body()).at("/data/id").asLong();

    HttpResponse<String> story = send("POST", "/api/v1/products/" + productId + "/stories",
        "{\"title\":\"IT 待发布需求\"}", adminCookie);
    long storyId = json.readTree(story.body()).at("/data/id").asLong();
    assertEquals("wait", json.readTree(story.body()).at("/data/stage").asText(), story.body());

    HttpResponse<String> release = send("POST", "/api/v1/products/" + productId + "/releases",
        "{\"name\":\"IT 1.0 发布\",\"releaseDate\":\"2026-09-18\",\"storyIds\":[" + storyId
            + "],\"notifyAccounts\":[\"it-release-qa\"]}",
        adminCookie);
    assertEquals(200, release.statusCode(), release.body());
    long releaseId = json.readTree(release.body()).at("/data/id").asLong();

    // 需求侧：stage → released + linked2release 动态流
    JsonNode storyAfter = json.readTree(send("GET", "/api/v1/stories/" + storyId, null, adminCookie).body())
        .at("/data");
    assertEquals("released", storyAfter.at("/stage").asText(), storyAfter.toString());
    HttpResponse<String> storyActivities = send("GET", "/api/v1/stories/" + storyId + "/activities", null, adminCookie);
    assertTrue(storyActivities.body().contains("linked2release"), storyActivities.body());

    // 发布侧：创建动态流
    HttpResponse<String> releaseActivities = send("GET", "/api/v1/releases/" + releaseId + "/activities", null,
        adminCookie);
    assertTrue(releaseActivities.body().contains("created"), releaseActivities.body());

    // 通知：接收人本人可见（type=release-created）
    String qaCookie = loginAs("it-release-qa", "secret123");
    HttpResponse<String> notifications = send("GET", "/api/v1/notifications", null, qaCookie);
    assertEquals(200, notifications.statusCode(), notifications.body());
    assertTrue(notifications.body().contains("release-created"), notifications.body());
    assertTrue(notifications.body().contains("IT 1.0 发布"), notifications.body());

    // terminate → terminated + 通知；重复 terminate → 42202
    HttpResponse<String> terminated = send("POST", "/api/v1/releases/" + releaseId + "/terminate",
        "{\"comment\":\"停止维护\"}", adminCookie);
    assertEquals(200, terminated.statusCode(), terminated.body());
    assertEquals("terminated", json.readTree(terminated.body()).at("/data/status").asText(), terminated.body());

    HttpResponse<String> again = send("POST", "/api/v1/releases/" + releaseId + "/terminate", "{}", adminCookie);
    assertEquals(422, again.statusCode(), again.body());
    assertTrue(again.body().contains("42202"), again.body());

    HttpResponse<String> notificationsAfter = send("GET", "/api/v1/notifications", null, qaCookie);
    assertTrue(notificationsAfter.body().contains("release-terminate"), notificationsAfter.body());
  }
}
