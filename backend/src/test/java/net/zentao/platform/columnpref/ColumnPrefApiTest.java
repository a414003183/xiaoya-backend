package net.zentao.platform.columnpref;

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
 * 列设置 API（platform「列设置」）：列表页列设置的服务端持久化。
 * 覆盖：保存→读取往返（顺序/fixed 原样）、未设置回 columns=null、个人级隔离（他账号不可见）、
 * 重置删行回默认、非法请求体 42201。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ColumnPrefApiTest {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;

  @BeforeEach
  void login() throws Exception {
    adminCookie = cookieOf(send("POST", "/api/v1/session",
        "{\"account\":\"admin\",\"password\":\"admin123\"}", null));
  }

  private String cookieOf(HttpResponse<String> response) {
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private HttpResponse<String> send(String method, String path, String body, String cookie) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("Content-Type", "application/json")
        .header("X-Requested-With", "fetch");
    if (cookie != null) {
      builder.header("Cookie", cookie);
    }
    return http.send(builder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode data(HttpResponse<String> response) throws Exception {
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).get("data");
  }

  /** 每个用例一个独立资源名（共享 H2 库，避免用例间互相看见）。 */
  private String resource(String tag) {
    return "test-" + tag + "-" + System.nanoTime() % 100000;
  }

  @Test
  @DisplayName("保存→读取往返：顺序、visible、fixed 原样返回；同账号同资源幂等覆盖")
  void saveThenGetRoundtrip() throws Exception {
    String resource = resource("roundtrip");
    String payload = "{\"columns\":["
        + "{\"key\":\"id\",\"visible\":true,\"fixed\":\"left\"},"
        + "{\"key\":\"name\",\"visible\":false,\"fixed\":null},"
        + "{\"key\":\"status\",\"visible\":true,\"fixed\":\"right\"}]}";
    JsonNode saved = data(send("PUT", "/api/v1/column-prefs/" + resource, payload, adminCookie));
    assertEquals(resource, saved.get("resource").asText());
    assertEquals("id", saved.get("columns").get(0).get("key").asText());
    assertEquals("name", saved.get("columns").get(1).get("key").asText());
    assertTrue(saved.get("columns").get(1).get("visible").isBoolean());
    assertEquals(false, saved.get("columns").get(1).get("visible").asBoolean());
    assertTrue(saved.get("columns").get(1).get("fixed").isNull());
    assertEquals("left", saved.get("columns").get(0).get("fixed").asText());

    JsonNode reread = data(send("GET", "/api/v1/column-prefs/" + resource, null, adminCookie));
    assertEquals(saved.get("columns").toString(), reread.get("columns").toString());

    JsonNode overwritten = data(send("PUT", "/api/v1/column-prefs/" + resource,
        "{\"columns\":[{\"key\":\"status\",\"visible\":true,\"fixed\":null}]}", adminCookie));
    assertEquals(1, overwritten.get("columns").size());
    assertEquals("status",
        data(send("GET", "/api/v1/column-prefs/" + resource, null, adminCookie)).get("columns").get(0).get("key").asText());
  }

  @Test
  @DisplayName("未设置的资源回 columns=null（前端据此用页面默认列）")
  void unsetResourceReturnsNull() throws Exception {
    JsonNode view = data(send("GET", "/api/v1/column-prefs/" + resource("unset"), null, adminCookie));
    assertTrue(view.get("columns").isNull(), view.toString());
  }

  @Test
  @DisplayName("个人级隔离：他账号看不到我的设置，各存各的")
  void prefIsPerAccount() throws Exception {
    String account = "colpref-" + System.nanoTime() % 100000;
    data(send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"列设置乙\"}", adminCookie));
    String otherCookie = cookieOf(send("POST", "/api/v1/session",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\"}", null));

    String resource = resource("isolation");
    data(send("PUT", "/api/v1/column-prefs/" + resource,
        "{\"columns\":[{\"key\":\"id\",\"visible\":true,\"fixed\":\"left\"}]}", adminCookie));

    JsonNode other = data(send("GET", "/api/v1/column-prefs/" + resource, null, otherCookie));
    assertTrue(other.get("columns").isNull(), other.toString());

    data(send("PUT", "/api/v1/column-prefs/" + resource,
        "{\"columns\":[{\"key\":\"name\",\"visible\":false,\"fixed\":null}]}", otherCookie));
    JsonNode mine = data(send("GET", "/api/v1/column-prefs/" + resource, null, adminCookie));
    assertEquals("id", mine.get("columns").get(0).get("key").asText());
  }

  @Test
  @DisplayName("重置即删行：DELETE 后回 columns=null；重复 DELETE 幂等")
  void resetDeletes() throws Exception {
    String resource = resource("reset");
    data(send("PUT", "/api/v1/column-prefs/" + resource,
        "{\"columns\":[{\"key\":\"id\",\"visible\":true,\"fixed\":null}]}", adminCookie));

    HttpResponse<String> deleted = send("DELETE", "/api/v1/column-prefs/" + resource, null, adminCookie);
    assertEquals(200, deleted.statusCode(), deleted.body());
    assertTrue(json.readTree(deleted.body()).get("data").isNull(), deleted.body());
    assertTrue(data(send("GET", "/api/v1/column-prefs/" + resource, null, adminCookie)).get("columns").isNull());
    assertEquals(200, send("DELETE", "/api/v1/column-prefs/" + resource, null, adminCookie).statusCode());
  }

  @Test
  @DisplayName("非法列项与非法资源 → 42201（fields 带定位）")
  void invalidPayloadRejected() throws Exception {
    String resource = resource("invalid");
    HttpResponse<String> duplicate = send("PUT", "/api/v1/column-prefs/" + resource,
        "{\"columns\":[{\"key\":\"id\",\"visible\":true,\"fixed\":null},{\"key\":\"id\",\"visible\":false,\"fixed\":null}]}",
        adminCookie);
    assertEquals(422, duplicate.statusCode(), duplicate.body());
    assertTrue(duplicate.body().contains("42201") && duplicate.body().contains("columns"), duplicate.body());

    assertEquals(422, send("PUT", "/api/v1/column-prefs/" + resource, "{\"columns\":[]}", adminCookie).statusCode());
    assertEquals(422, send("PUT", "/api/v1/column-prefs/" + resource,
        "{\"columns\":[{\"key\":\"id\",\"visible\":true,\"fixed\":\"top\"}]}", adminCookie).statusCode());
    assertEquals(422, send("PUT", "/api/v1/column-prefs/" + resource,
        "{\"columns\":[{\"key\":\"  \",\"visible\":true,\"fixed\":null}]}", adminCookie).statusCode());
    assertEquals(422, send("GET", "/api/v1/column-prefs/Bad_Code", null, adminCookie).statusCode());
    assertEquals(401, send("GET", "/api/v1/column-prefs/" + resource, null, null).statusCode());
  }
}
