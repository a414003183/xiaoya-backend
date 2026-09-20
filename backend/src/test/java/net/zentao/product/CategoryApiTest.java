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

/** T-6 分类树（product 卡 §8）：filters[type] 必选 40001、跨产品/跨 type 42203、DELETE 级联子树、树响应不含他产品节点。 */
class CategoryApiTest extends net.zentao.H2TestSupport {

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

  private long createProduct(String name) throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/products",
        "{\"name\":\"" + name + "-" + System.nanoTime() + "\"}", adminCookie);
    assertEquals(200, created.statusCode(), created.body());
    return json.readTree(created.body()).at("/data/id").asLong();
  }

  private long createNode(long productId, String body) throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/products/" + productId + "/categories", body, adminCookie);
    assertEquals(200, created.statusCode(), created.body());
    return json.readTree(created.body()).at("/data/id").asLong();
  }

  @Test
  @DisplayName("整树：filters[type] 必选（缺 40001）、树序父先于子、他产品节点不出现")
  void treeResponse() throws Exception {
    long productId = createProduct("分类产品甲");
    long other = createProduct("分类产品乙");
    long root = createNode(productId, "{\"type\":\"story\",\"name\":\"需求分类根\"}");
    long child = createNode(productId, "{\"type\":\"story\",\"name\":\"子分类\",\"parentId\":" + root + "}");
    createNode(other, "{\"type\":\"story\",\"name\":\"他产品节点\"}");
    createNode(productId, "{\"type\":\"bug\",\"name\":\"缺陷分类\"}");

    HttpResponse<String> missingType = send("GET", "/api/v1/products/" + productId + "/categories", null, adminCookie);
    assertEquals(400, missingType.statusCode(), missingType.body());
    assertTrue(missingType.body().contains("40001"), missingType.body());

    HttpResponse<String> tree = send("GET", "/api/v1/products/" + productId + "/categories?filters%5Btype%5D=story",
        null, adminCookie);
    assertEquals(200, tree.statusCode(), tree.body());
    JsonNode items = json.readTree(tree.body()).at("/data/items");
    assertEquals(2, items.size(), tree.body());
    assertEquals(root, items.get(0).at("/id").asLong(), tree.body());
    assertEquals(child, items.get(1).at("/id").asLong(), tree.body());
    for (JsonNode item : items) {
      assertEquals(productId, item.at("/productId").asLong(), tree.body());
      assertEquals("story", item.at("/type").asText(), tree.body());
    }
  }

  @Test
  @DisplayName("跨产品/跨 type 父级 → 42203；DELETE 级联软删子树且树内不再出现")
  void parentRulesAndCascade() throws Exception {
    long productId = createProduct("分类产品丙");
    long other = createProduct("分类产品丁");
    long root = createNode(productId, "{\"type\":\"story\",\"name\":\"根\"}");
    long child = createNode(productId, "{\"type\":\"story\",\"name\":\"子\",\"parentId\":" + root + "}");
    long grandChild = createNode(productId, "{\"type\":\"story\",\"name\":\"孙\",\"parentId\":" + child + "}");
    long bugNode = createNode(productId, "{\"type\":\"bug\",\"name\":\"缺陷节点\"}");
    long otherNode = createNode(other, "{\"type\":\"story\",\"name\":\"他产品节点\"}");

    HttpResponse<String> crossType = send("POST", "/api/v1/products/" + productId + "/categories",
        "{\"type\":\"story\",\"name\":\"跨类型\",\"parentId\":" + bugNode + "}", adminCookie);
    assertEquals(422, crossType.statusCode(), crossType.body());
    assertTrue(crossType.body().contains("42203"), crossType.body());

    HttpResponse<String> crossProduct = send("POST", "/api/v1/products/" + productId + "/categories",
        "{\"type\":\"story\",\"name\":\"跨产品\",\"parentId\":" + otherNode + "}", adminCookie);
    assertEquals(422, crossProduct.statusCode(), crossProduct.body());

    HttpResponse<String> cycle = send("PATCH", "/api/v1/categories/" + root,
        "{\"parentId\":" + grandChild + ",\"lockVersion\":0}", adminCookie);
    assertEquals(422, cycle.statusCode(), cycle.body());
    assertTrue(cycle.body().contains("42203"), cycle.body());

    HttpResponse<String> deleted = send("DELETE", "/api/v1/categories/" + root, null, adminCookie);
    assertEquals(200, deleted.statusCode(), deleted.body());

    HttpResponse<String> tree = send("GET", "/api/v1/products/" + productId + "/categories?filters%5Btype%5D=story",
        null, adminCookie);
    assertEquals(0, json.readTree(tree.body()).at("/data/items").size(), tree.body());

    JsonNode bugTree = json.readTree(send("GET",
        "/api/v1/products/" + productId + "/categories?filters%5Btype%5D=bug", null, adminCookie).body());
    assertEquals(1, bugTree.at("/data/items").size(), bugTree.toString());
  }
}
