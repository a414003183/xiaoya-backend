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
 * T-2 产品后端（product 卡 §8）：状态机 close/activate 迁移矩阵、ACL 数据权限、
 * ACL 校验（custom 必填白名单）、乐观锁 40901、白名单外过滤 40001、批量部分成功。
 */
class ProductWorkflowTest extends net.zentao.H2TestSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;

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

  /** 创建账号（挂产品测试组，否则非超管账号没有 product-* 权限码）。 */
  private String ensureAccount(String account) throws Exception {
    send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"" + account
            + "\",\"groupIds\":[" + productGroupId() + "]}",
        adminCookie);
    return account;
  }

  /** 非超管账号需要功能权限码才可访问产品端点：建一个只含产品码的测试组（不动种子组）。 */
  private long productGroupId;

  private long productGroupId() throws Exception {
    if (productGroupId != 0) {
      return productGroupId;
    }
    HttpResponse<String> group = send("POST", "/api/v1/groups", "{\"name\":\"产品测试组\"}", adminCookie);
    assertEquals(200, group.statusCode(), group.body());
    productGroupId = json.readTree(group.body()).at("/data/id").asLong();
    HttpResponse<String> privileges = send("PUT", "/api/v1/groups/" + productGroupId + "/privileges",
        "{\"codes\":[\"product-view\",\"product-create\",\"product-edit\",\"product-close\",\"product-activate\"]}",
        adminCookie);
    assertEquals(200, privileges.statusCode(), privileges.body());
    return productGroupId;
  }

  private long createProduct(String name, String acl, String whitelistJson, String owner) throws Exception {
    String body = "{\"name\":\"" + name + "\",\"type\":\"normal\",\"acl\":\"" + acl + "\""
        + (whitelistJson == null ? "" : ",\"whitelist\":" + whitelistJson)
        + (owner == null ? "" : ",\"po\":\"" + owner + "\"") + "}";
    HttpResponse<String> response = send("POST", "/api/v1/products", body, adminCookie);
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data/id").asLong();
  }

  @Test
  @DisplayName("创建→详情→PATCH 乐观锁 40901→PATCH 生效→activities 落 created")
  void lifecycleAndOptimisticLock() throws Exception {
    long id = createProduct("产品生命周期", "public", null, null);

    HttpResponse<String> detail = send("GET", "/api/v1/products/" + id, null, adminCookie);
    assertEquals(200, detail.statusCode(), detail.body());
    assertTrue(detail.body().contains("\"status\":\"normal\""), detail.body());

    HttpResponse<String> stale = send("PATCH", "/api/v1/products/" + id,
        "{\"name\":\"改名\",\"lockVersion\":99}", adminCookie);
    assertEquals(409, stale.statusCode(), stale.body());
    assertTrue(stale.body().contains("40901"), stale.body());

    HttpResponse<String> patched = send("PATCH", "/api/v1/products/" + id,
        "{\"name\":\"产品生命周期改名\",\"sort\":5,\"lockVersion\":0}", adminCookie);
    assertEquals(200, patched.statusCode(), patched.body());
    assertTrue(patched.body().contains("产品生命周期改名"), patched.body());
    assertTrue(patched.body().contains("\"sort\":5"), patched.body());

    HttpResponse<String> activities = send("GET", "/api/v1/products/" + id + "/activities", null, adminCookie);
    assertEquals(200, activities.statusCode(), activities.body());
    assertTrue(activities.body().contains("\"action\":\"created\""), activities.body());
  }

  @Test
  @DisplayName("close/activate 状态机：迁移落 closedAt、清 closedAt、非法 from → 42202、动态流同源")
  void stateMachine() throws Exception {
    long id = createProduct("产品状态机", "public", null, null);

    HttpResponse<String> activateTooEarly = send("POST", "/api/v1/products/" + id + "/activate", "{}", adminCookie);
    assertEquals(422, activateTooEarly.statusCode(), activateTooEarly.body());
    assertTrue(activateTooEarly.body().contains("42202"), activateTooEarly.body());

    HttpResponse<String> closed = send("POST", "/api/v1/products/" + id + "/close", "{\"comment\":\"结项\"}", adminCookie);
    assertEquals(200, closed.statusCode(), closed.body());
    assertTrue(closed.body().contains("\"status\":\"closed\""), closed.body());
    assertTrue(closed.body().contains("\"closedAt\":\""), closed.body());

    HttpResponse<String> reclose = send("POST", "/api/v1/products/" + id + "/close", "{}", adminCookie);
    assertEquals(422, reclose.statusCode(), reclose.body());

    HttpResponse<String> activated = send("POST", "/api/v1/products/" + id + "/activate", "{}", adminCookie);
    assertEquals(200, activated.statusCode(), activated.body());
    assertTrue(activated.body().contains("\"status\":\"normal\""), activated.body());
    assertTrue(activated.body().contains("\"closedAt\":null"), activated.body());

    HttpResponse<String> activities = send("GET", "/api/v1/products/" + id + "/activities", null, adminCookie);
    assertTrue(activities.body().contains("\"action\":\"closed\""), activities.body());
    assertTrue(activities.body().contains("\"action\":\"activated\""), activities.body());
    assertTrue(activities.body().contains("结项"), activities.body());
  }

  @Test
  @DisplayName("ACL：private 外人列表 0 条/详情 40302、白名单可见、custom 仅白名单、超管全见")
  void productAcl() throws Exception {
    ensureAccount("acl-dev1");
    ensureAccount("acl-guest");
    long privateId = createProduct("私有产品", "private", "[\"acl-dev1\"]", "acl-dev1");
    long customId = createProduct("定制产品", "custom", "[\"acl-dev1\"]", null);

    String devCookie = loginAs("acl-dev1", "secret123");
    String guestCookie = loginAs("acl-guest", "secret123");

    // 白名单成员：列表可见两个产品，详情 200
    HttpResponse<String> devList = send("GET", "/api/v1/products?filters%5Bid%5D=" + privateId + "," + customId,
        null, devCookie);
    assertEquals(2, json.readTree(devList.body()).at("/data/items").size(), devList.body());
    assertEquals(200, send("GET", "/api/v1/products/" + customId, null, devCookie).statusCode());

    // 外人：列表 0 条、详情 40302
    HttpResponse<String> guestList = send("GET", "/api/v1/products?filters%5Bid%5D=" + privateId + "," + customId,
        null, guestCookie);
    assertEquals(0, json.readTree(guestList.body()).at("/data/items").size(), guestList.body());
    HttpResponse<String> guestDetail = send("GET", "/api/v1/products/" + privateId, null, guestCookie);
    assertEquals(403, guestDetail.statusCode(), guestDetail.body());
    assertTrue(guestDetail.body().contains("40302"), guestDetail.body());

    // 超管：全见
    JsonNode adminList = json.readTree(send("GET", "/api/v1/products", null, adminCookie).body());
    assertTrue(adminList.at("/data/total").asLong() >= 2, adminList.toString());
  }

  @Test
  @DisplayName("ACL 校验：custom 白名单必填 42201、超 100 42201、引用不存在账号 42201、过滤字段白名单外 40001")
  void validationRules() throws Exception {
    HttpResponse<String> customEmpty = send("POST", "/api/v1/products",
        "{\"name\":\"定制缺白名单\",\"acl\":\"custom\"}", adminCookie);
    assertEquals(422, customEmpty.statusCode(), customEmpty.body());
    assertTrue(customEmpty.body().contains("whitelist"), customEmpty.body());

    StringBuilder many = new StringBuilder();
    for (int i = 0; i < 101; i++) {
      many.append(i == 0 ? "" : ",").append("\"ghost").append(i).append("\"");
    }
    HttpResponse<String> tooMany = send("POST", "/api/v1/products",
        "{\"name\":\"白名单超限\",\"acl\":\"custom\",\"whitelist\":[" + many + "]}", adminCookie);
    assertEquals(422, tooMany.statusCode(), tooMany.body());

    HttpResponse<String> ghostPo = send("POST", "/api/v1/products",
        "{\"name\":\"幽灵负责人\",\"po\":\"no-such-account\"}", adminCookie);
    assertEquals(422, ghostPo.statusCode(), ghostPo.body());
    assertTrue(ghostPo.body().contains("po"), ghostPo.body());

    HttpResponse<String> unregistered = send("GET", "/api/v1/products?filters%5Bghost%5D=1", null, adminCookie);
    assertEquals(400, unregistered.statusCode(), unregistered.body());
    assertTrue(unregistered.body().contains("40001"), unregistered.body());
  }

  @Test
  @DisplayName("批量动作：close 部分成功逐项结果、不支持的动作 40001 落 error 列")
  void batchActions() throws Exception {
    long first = createProduct("批量甲", "public", null, null);
    long second = createProduct("批量乙", "public", null, null);
    send("POST", "/api/v1/products/" + second + "/close", "{}", adminCookie);

    HttpResponse<String> batch = send("POST", "/api/v1/products/batch",
        "{\"ids\":[" + first + "," + second + "],\"action\":\"close\"}", adminCookie);
    assertEquals(200, batch.statusCode(), batch.body());
    JsonNode results = json.readTree(batch.body()).at("/data/results");
    assertEquals(2, results.size(), batch.body());
    assertTrue(results.get(0).at("/ok").asBoolean(), batch.body());
    assertEquals(false, results.get(1).at("/ok").asBoolean(), batch.body());
    assertTrue(results.get(1).at("/error").asText().startsWith("42202"), batch.body());

    HttpResponse<String> unsupported = send("POST", "/api/v1/products/batch",
        "{\"ids\":[" + first + "],\"action\":\"frobnicate\"}", adminCookie);
    assertEquals(400, unsupported.statusCode(), unsupported.body());
  }
}
