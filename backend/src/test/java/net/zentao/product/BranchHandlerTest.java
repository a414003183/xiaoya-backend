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

/** T-6 分支（product 卡 §8）：type=normal 拒挂分支、同产品重名 42201、set-default 排他、close/activate 42202 矩阵。 */
class BranchHandlerTest extends net.zentao.H2TestSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;

  @BeforeEach
  void login() throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/session",
        "{\"account\":\"admin\",\"password\":\"admin123\"}", null);
    assertEquals(200, response.statusCode(), response.body());
    adminCookie = response.headers().allValues("set-cookie").stream()
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

  private long createProduct(String type) throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/products",
        "{\"name\":\"分支产品-" + type + "-" + System.nanoTime() + "\",\"type\":\"" + type + "\"}", adminCookie);
    assertEquals(200, created.statusCode(), created.body());
    return json.readTree(created.body()).at("/data/id").asLong();
  }

  @Test
  @DisplayName("normal 产品挂分支 → 42201；branch 产品首个分支自动 isDefault=true；重名 → 42201")
  void createRules() throws Exception {
    long normal = createProduct("normal");
    HttpResponse<String> rejected = send("POST", "/api/v1/products/" + normal + "/branches",
        "{\"name\":\"不应存在\"}", adminCookie);
    assertEquals(422, rejected.statusCode(), rejected.body());
    assertTrue(rejected.body().contains("42201"), rejected.body());

    long branchProduct = createProduct("branch");
    HttpResponse<String> first = send("POST", "/api/v1/products/" + branchProduct + "/branches",
        "{\"name\":\"主干\",\"description\":\"默认分支\"}", adminCookie);
    assertEquals(200, first.statusCode(), first.body());
    assertTrue(json.readTree(first.body()).at("/data/isDefault").asBoolean(), first.body());

    HttpResponse<String> duplicate = send("POST", "/api/v1/products/" + branchProduct + "/branches",
        "{\"name\":\"主干\"}", adminCookie);
    assertEquals(422, duplicate.statusCode(), duplicate.body());
    assertTrue(duplicate.body().contains("duplicate"), duplicate.body());
  }

  @Test
  @DisplayName("set-default 排他：其余分支 isDefault 置 false；close/activate 迁移矩阵与 42202")
  void defaultAndStateMachine() throws Exception {
    long productId = createProduct("branch");
    long first = json.readTree(send("POST", "/api/v1/products/" + productId + "/branches",
        "{\"name\":\"主干\"}", adminCookie).body()).at("/data/id").asLong();
    long second = json.readTree(send("POST", "/api/v1/products/" + productId + "/branches",
        "{\"name\":\"发布分支\"}", adminCookie).body()).at("/data/id").asLong();

    HttpResponse<String> setDefault = send("POST", "/api/v1/branches/" + second + "/set-default", null, adminCookie);
    assertEquals(200, setDefault.statusCode(), setDefault.body());
    assertTrue(json.readTree(setDefault.body()).at("/data/isDefault").asBoolean(), setDefault.body());

    HttpResponse<String> list = send("GET", "/api/v1/products/" + productId + "/branches", null, adminCookie);
    JsonNode items = json.readTree(list.body()).at("/data/items");
    for (JsonNode item : items) {
      boolean expected = item.at("/id").asLong() == second;
      assertEquals(expected, item.at("/isDefault").asBoolean(), list.body());
    }
    long defaults = 0;
    for (JsonNode item : items) {
      if (item.at("/isDefault").asBoolean()) {
        defaults += 1;
      }
    }
    assertEquals(1, defaults, list.body());

    assertEquals(422, send("POST", "/api/v1/branches/" + first + "/activate", "{}", adminCookie).statusCode());
    HttpResponse<String> closed = send("POST", "/api/v1/branches/" + first + "/close", "{}", adminCookie);
    assertEquals(200, closed.statusCode(), closed.body());
    assertTrue(closed.body().contains("\"status\":\"closed\""), closed.body());
    assertTrue(closed.body().contains("\"closedAt\":\""), closed.body());
    HttpResponse<String> activated = send("POST", "/api/v1/branches/" + first + "/activate", "{}", adminCookie);
    assertEquals(200, activated.statusCode(), activated.body());
    assertTrue(activated.body().contains("\"closedAt\":null"), activated.body());

    HttpResponse<String> stale = send("PATCH", "/api/v1/branches/" + first,
        "{\"name\":\"主干改名\",\"lockVersion\":9}", adminCookie);
    assertEquals(409, stale.statusCode(), stale.body());
  }
}
