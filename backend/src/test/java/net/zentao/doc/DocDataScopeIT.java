package net.zentao.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * T-10 A6 补齐：doc 域数据权限 IT（doc 卡 §7，Testcontainers MySQL 8.4 真库）。
 * 双层判定在真 SQL 下的注入验证：private 产品库外人列表不可见 + 详情 40302；
 * mine 库仅创建者可见（超管豁免不适用）；open 库文档 acl=private 时 readers 命中可读。
 * 其余域的 DataScope IT：ProductAclIT / StoryAclIT / ProjectDataScopeIT / TaskDataScopeIT / QualityDataScopeIT。
 */
class DocDataScopeIT extends MySqlContainerSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private long viewerGroupId;

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

  private JsonNode data(HttpResponse<String> response) throws Exception {
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data");
  }

  private long dataId(HttpResponse<String> response) throws Exception {
    return data(response).at("/id").asLong();
  }

  /** 业务码断言：40302/40401 等在响应体 error.code，不是 HTTP 状态码（平台错误信封，03 §4）。 */
  private void assertErrorCode(int expected, HttpResponse<String> response) throws Exception {
    assertTrue(response.statusCode() >= 400, response.body());
    assertEquals(expected, json.readTree(response.body()).at("/error/code").asInt(), response.body());
  }

  /** 文档查看者组：doc 读权限齐备但无数据授权——数据权限与功能权限独立（doc 卡 §7）。
   *  含 doc-space-create：mine 库创建也走 POST /doc-spaces 的该功能码（doc 卡 §5）。 */
  private String ensureViewer(String account) throws Exception {
    if (viewerGroupId == 0) {
      viewerGroupId = dataId(send("POST", "/api/v1/groups",
          "{\"name\":\"IT 文档组 " + System.nanoTime() + "\"}", adminCookie));
      send("PUT", "/api/v1/groups/" + viewerGroupId + "/privileges",
          "{\"codes\":[\"doc-space-view\",\"doc-space-create\",\"doc-view\",\"product-view\"]}", adminCookie);
    }
    HttpResponse<String> created = send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"" + account
            + "\",\"groupIds\":[" + viewerGroupId + "]}",
        adminCookie);
    assertTrue(created.statusCode() == 200 || created.statusCode() == 422, created.body());
    return loginAs(account, "secret123");
  }

  private boolean spaceVisible(long spaceId, String cookie) throws Exception {
    JsonNode items = data(send("GET", "/api/v1/doc-spaces?limit=200", null, cookie)).at("/items");
    for (JsonNode item : items) {
      if (item.at("/id").asLong() == spaceId) {
        return true;
      }
    }
    return false;
  }

  @Test
  @DisplayName("private 产品库：外人列表不可见 + 详情 40302，超管可见")
  void privateProductSpaceHiddenFromOutsider() throws Exception {
    long product = dataId(send("POST", "/api/v1/products",
        "{\"name\":\"IT 文档产品 " + System.nanoTime() + "\",\"acl\":\"public\"}", adminCookie));
    long space = dataId(send("POST", "/api/v1/doc-spaces",
        "{\"name\":\"IT 私有库\",\"type\":\"product\",\"productId\":" + product + ",\"acl\":\"private\"}",
        adminCookie));
    long doc = dataId(send("POST", "/api/v1/doc-spaces/" + space + "/docs",
        "{\"title\":\"IT 私有库文档\",\"status\":\"published\",\"content\":\"hello\"}", adminCookie));
    String viewer = ensureViewer("docviewer1");

    assertFalse(spaceVisible(space, viewer), "外人库列表不应出现 private 库");
    assertTrue(spaceVisible(space, adminCookie), "超管应可见 private 库");
    assertErrorCode(40302, send("GET", "/api/v1/doc-spaces/" + space, null, viewer));
    assertErrorCode(40401, send("GET", "/api/v1/docs/" + doc, null, viewer));

    // 修库为 open 后外人可见（ACL 变更即时生效）
    assertEquals(200, send("PATCH", "/api/v1/doc-spaces/" + space,
        "{\"acl\":\"open\",\"lockVersion\":0}", adminCookie).statusCode());
    assertTrue(spaceVisible(space, viewer), "库改 open 后外人应可见");
  }

  @Test
  @DisplayName("mine 库仅创建者可见（超管豁免不适用）；open 库 private 文档 readers 命中可读")
  void mineSpaceOwnerOnlyAndPrivateDocReaders() throws Exception {
    String viewer = ensureViewer("docviewer2");
    long mine = dataId(send("POST", "/api/v1/doc-spaces",
        "{\"name\":\"我的空间\",\"type\":\"mine\"}", viewer));
    assertTrue(spaceVisible(mine, viewer), "创建者应可见自己的 mine 库");
    assertFalse(spaceVisible(mine, adminCookie), "mine 库对超管也不可见（doc 卡 §7）");

    long product = dataId(send("POST", "/api/v1/products",
        "{\"name\":\"IT 文档产品2 " + System.nanoTime() + "\",\"acl\":\"public\"}", adminCookie));
    long space = dataId(send("POST", "/api/v1/doc-spaces",
        "{\"name\":\"IT 开放库\",\"type\":\"product\",\"productId\":" + product + ",\"acl\":\"open\"}",
        adminCookie));
    // 库 open + 文档 private：readers 命中 viewer → 可读；未命中的另一账号 → 不可见
    long doc = dataId(send("POST", "/api/v1/doc-spaces/" + space + "/docs",
        "{\"title\":\"IT 受控文档\",\"status\":\"published\",\"content\":\"secret\","
            + "\"acl\":\"private\",\"readers\":{\"accounts\":[\"docviewer2\"],\"groups\":[]}}",
        adminCookie));
    assertEquals(200, send("GET", "/api/v1/docs/" + doc, null, viewer).statusCode(), "readers 命中应可读");

    String other = ensureViewer("docviewer3");
    assertErrorCode(40401, send("GET", "/api/v1/docs/" + doc, null, other));
    JsonNode crossList = data(send("GET", "/api/v1/docs?limit=200", null, other)).at("/items");
    boolean listed = false;
    for (JsonNode item : crossList) {
      if (item.at("/id").asLong() == doc) {
        listed = true;
      }
    }
    assertFalse(listed, "跨库列表不应出现不可见文档");
  }
}
