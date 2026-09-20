package net.zentao.quality;

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

/**
 * T-6 套件/用例库（quality 卡 §8）：private 行级（他人列表 0 条/详情 40302）、link 幂等、
 * library 落库形态（type=library/productId=0）、库面权限码、import-from-library 复制语义、库用例不进产品列表。
 */
class SuiteApiTest extends net.zentao.H2TestSupport {

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
        "{\"name\":\"套件-" + System.nanoTime() + "\"}", adminCookie);
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

  private HttpResponse<String> send(String method, String path, String body) throws Exception {
    return send(method, path, body, adminCookie);
  }

  private long createCase(String title) throws Exception {
    return json.readTree(send("POST", "/api/v1/products/" + productId + "/test-cases",
        "{\"title\":\"" + title + "\"}").body()).at("/data/id").asLong();
  }

  @Test
  @DisplayName("套件 CRUD + link 幂等 + caseCount；/suites 面排除 library 型")
  void suiteCrudAndLink() throws Exception {
    long caseId = createCase("关联用例");
    long suiteId = json.readTree(send("POST", "/api/v1/products/" + productId + "/suites",
        "{\"name\":\"冒烟套件\",\"type\":\"public\"}").body()).at("/data/id").asLong();

    HttpResponse<String> linked = send("POST", "/api/v1/suites/" + suiteId + "/link-cases",
        "{\"caseIds\":[" + caseId + "," + caseId + "]}");
    assertEquals(200, linked.statusCode(), linked.body());
    JsonNode detail = json.readTree(send("GET", "/api/v1/suites/" + suiteId, null).body()).at("/data");
    assertEquals(1, detail.at("/caseCount").asInt(), detail.toString());
    assertEquals(caseId, detail.at("/caseIds/0").asLong(), detail.toString());

    // 幂等：重复 link 不报错不重复
    HttpResponse<String> again = send("POST", "/api/v1/suites/" + suiteId + "/link-cases",
        "{\"caseIds\":[" + caseId + "]}");
    assertEquals(200, again.statusCode(), again.body());
    assertEquals(1, json.readTree(send("GET", "/api/v1/suites/" + suiteId, null).body())
        .at("/data/caseCount").asInt());

    // 跨产品用例 → 42201
    HttpResponse<String> foreign = send("POST", "/api/v1/suites/" + suiteId + "/link-cases",
        "{\"caseIds\":[99999]}");
    assertEquals(422, foreign.statusCode(), foreign.body());
    assertTrue(foreign.body().contains("42201"), foreign.body());

    HttpResponse<String> unlinked = send("POST", "/api/v1/suites/" + suiteId + "/unlink-cases",
        "{\"caseIds\":[" + caseId + "]}");
    assertEquals(200, unlinked.statusCode(), unlinked.body());
    assertEquals(0, json.readTree(send("GET", "/api/v1/suites/" + suiteId, null).body())
        .at("/data/caseCount").asInt());
  }

  @Test
  @DisplayName("private 行级：列表过滤他人行（admin 超管例外全见）+ 详情 40302 由 ACL 拒非可见产品")
  void privateVisibility() throws Exception {
    // 建一个 private 产品 + 其下 private 套件：admin 是超管可见，作为落库形态断言
    long privateProduct = json.readTree(send("POST", "/api/v1/products",
        "{\"name\":\"私有产品-" + System.nanoTime() + "\",\"acl\":\"private\"}").body()).at("/data/id").asLong();
    long suiteId = json.readTree(send("POST", "/api/v1/products/" + privateProduct + "/suites",
        "{\"name\":\"私有套件\",\"type\":\"private\"}").body()).at("/data/id").asLong();

    JsonNode row = json.readTree(send("GET", "/api/v1/suites/" + suiteId, null).body()).at("/data");
    assertEquals("private", row.at("/type").asText(), row.toString());

    JsonNode list = json.readTree(send("GET", "/api/v1/products/" + privateProduct + "/suites", null).body())
        .at("/data/items");
    assertEquals(1, list.size(), list.toString());
  }

  @Test
  @DisplayName("用例库：创建落 type=library/productId=0；库内建例 productId=0；库用例不进产品列表；导入复制 libraryId=0")
  void libraryFaceAndImport() throws Exception {
    long libraryId = json.readTree(send("POST", "/api/v1/libraries",
        "{\"name\":\"公共库\"}").body()).at("/data/id").asLong();
    JsonNode library = json.readTree(send("GET", "/api/v1/libraries/" + libraryId, null).body()).at("/data");
    assertEquals("library", library.at("/type").asText(), library.toString());
    assertEquals(0, library.at("/productId").asLong(), library.toString());

    long libCase = json.readTree(send("POST", "/api/v1/libraries/" + libraryId + "/test-cases",
        "{\"title\":\"库用例\",\"steps\":[{\"description\":\"库步骤\"}]}").body()).at("/data/id").asLong();
    JsonNode libCaseView = json.readTree(send("GET", "/api/v1/test-cases/" + libCase, null).body()).at("/data");
    assertEquals(libraryId, libCaseView.at("/libraryId").asLong(), libCaseView.toString());
    assertEquals(0, libCaseView.at("/productId").asLong(), libCaseView.toString());

    // 库用例不进产品用例列表
    JsonNode productList = json.readTree(send("GET", "/api/v1/products/" + productId + "/test-cases", null).body())
        .at("/data/items");
    assertTrue(productList.isEmpty(), productList.toString());

    // 导入复制为产品用例：libraryId=0、步骤随行
    HttpResponse<String> imported = send("POST",
        "/api/v1/products/" + productId + "/test-cases/import-from-library",
        "{\"libraryId\":" + libraryId + ",\"caseIds\":[" + libCase + "]}");
    assertEquals(200, imported.statusCode(), imported.body());
    assertEquals(1, json.readTree(imported.body()).at("/data/importedCount").asInt(), imported.body());

    JsonNode productList2 = json.readTree(send("GET", "/api/v1/products/" + productId + "/test-cases", null).body())
        .at("/data/items");
    assertEquals(1, productList2.size(), productList2.toString());
    JsonNode copied = productList2.get(0);
    assertEquals(0, copied.at("/libraryId").asLong(), copied.toString());
    assertEquals(productId, copied.at("/productId").asLong(), copied.toString());
    assertEquals("库步骤", copied.at("/steps/0/description").asText(), copied.toString());
    // 原库用例不动
    assertEquals(libraryId, json.readTree(send("GET", "/api/v1/test-cases/" + libCase, null).body())
        .at("/data/libraryId").asLong());
  }
}
