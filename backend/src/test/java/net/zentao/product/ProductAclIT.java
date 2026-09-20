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
 * T-2 数据权限 IT（product 卡 §8，Testcontainers MySQL 8.4 真库）：
 * private 外人列表 0 条 / 详情 40302；custom 仅白名单；超管全见；乐观锁 40901；区间过滤。
 */
class ProductAclIT extends MySqlContainerSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private long productGroupId;

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
    if (productGroupId != 0) {
      return productGroupId;
    }
    HttpResponse<String> group = send("POST", "/api/v1/groups", "{\"name\":\"IT 产品组\"}", adminCookie);
    assertEquals(200, group.statusCode(), group.body());
    productGroupId = json.readTree(group.body()).at("/data/id").asLong();
    send("PUT", "/api/v1/groups/" + productGroupId + "/privileges",
        "{\"codes\":[\"product-view\",\"product-create\",\"product-edit\",\"product-close\",\"product-activate\"]}",
        adminCookie);
    return productGroupId;
  }

  private void ensureAccount(String account) throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"" + account
            + "\",\"groupIds\":[" + ensureGroup() + "]}",
        adminCookie);
    assertTrue(created.statusCode() == 200 || created.statusCode() == 422, created.body());
  }

  private long createProduct(String name, String acl, String whitelistJson, String createdAt) throws Exception {
    String body = "{\"name\":\"" + name + "\",\"acl\":\"" + acl + "\""
        + (whitelistJson == null ? "" : ",\"whitelist\":" + whitelistJson) + "}";
    HttpResponse<String> response = send("POST", "/api/v1/products", body, adminCookie);
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data/id").asLong();
  }

  @Test
  @DisplayName("private/custom 可见集 + 详情 40302 + 超管全见 + 乐观锁 40901 + createdAt 区间过滤")
  void aclOnRealDatabase() throws Exception {
    ensureAccount("it-dev1");
    ensureAccount("it-guest");
    long privateId = createProduct("IT 私有", "private", "[\"it-dev1\"]", null);
    long customId = createProduct("IT 定制", "custom", "[\"it-dev1\"]", null);
    String devCookie = loginAs("it-dev1", "secret123");
    String guestCookie = loginAs("it-guest", "secret123");

    // 白名单成员：列表 2 条、详情可读
    HttpResponse<String> devList = send("GET",
        "/api/v1/products?filters%5Bid%5D=" + privateId + "," + customId, null, devCookie);
    assertEquals(2, json.readTree(devList.body()).at("/data/items").size(), devList.body());
    assertEquals(200, send("GET", "/api/v1/products/" + customId, null, devCookie).statusCode());

    // 外人：列表 0 条、详情 40302
    HttpResponse<String> guestList = send("GET",
        "/api/v1/products?filters%5Bid%5D=" + privateId + "," + customId, null, guestCookie);
    assertEquals(0, json.readTree(guestList.body()).at("/data/items").size(), guestList.body());
    HttpResponse<String> guestDetail = send("GET", "/api/v1/products/" + privateId, null, guestCookie);
    assertEquals(403, guestDetail.statusCode(), guestDetail.body());
    assertTrue(guestDetail.body().contains("40302"), guestDetail.body());

    // 超管：全见
    JsonNode adminList = json.readTree(send("GET", "/api/v1/products", null, adminCookie).body());
    assertTrue(adminList.at("/data/total").asLong() >= 2, adminList.toString());

    // 乐观锁：版本不符 40901
    HttpResponse<String> stale = send("PATCH", "/api/v1/products/" + privateId,
        "{\"name\":\"IT 私有改名\",\"lockVersion\":42}", adminCookie);
    assertEquals(409, stale.statusCode(), stale.body());
    assertTrue(stale.body().contains("40901"), stale.body());

    // 区间过滤：createdAt 两端（MySQL 日期列真值）
    String today = java.time.LocalDate.now().toString();
    HttpResponse<String> ranged = send("GET",
        "/api/v1/products?format=json&filters%5BcreatedAt%5D=" + today + ".." + today, null, adminCookie);
    assertEquals(200, ranged.statusCode(), ranged.body());
    assertTrue(json.readTree(ranged.body()).at("/data/total").asLong() >= 2, ranged.body());

    // 区间过滤命中为 0 的边界：过去区间
    HttpResponse<String> empty = send("GET",
        "/api/v1/products?filters%5BcreatedAt%5D=2000-01-01..2000-01-02", null, adminCookie);
    assertEquals(0, json.readTree(empty.body()).at("/data/total").asLong(), empty.body());
  }
}
