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
 * B-QUA-01（BugUpdateRequest.testCaseId 来源用例关联）+ B-PRJ-16/17（列表过滤白名单补
 * bug.projectId 与 test-case.projectId/executionId——用例无本表列经测试单子查询联查）。
 */
class BugLinkAndDimensionFilterTest extends net.zentao.ApiTestSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private long productId;
  private long projectId;
  private long executionId;

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
    productId = id(send("POST", "/api/v1/products",
        "{\"name\":\"链接过滤产品-" + System.nanoTime() + "\"}"));
    projectId = id(send("POST", "/api/v1/projects",
        "{\"name\":\"链接过滤项目-" + System.nanoTime() + "\",\"beginDate\":\"2026-01-01\","
            + "\"endDate\":\"2026-12-31\",\"acl\":\"private\",\"productIds\":[" + productId + "]}",
        adminCookie));
    executionId = id(send("POST", "/api/v1/projects/" + projectId + "/executions",
        "{\"type\":\"sprint\",\"name\":\"链接过滤迭代\",\"beginDate\":\"2026-01-01\",\"endDate\":\"2026-06-30\"}",
        adminCookie));
  }

  private HttpResponse<String> send(String method, String path, String body) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Content-Type", "application/json")
        .header("Cookie", adminCookie);
    builder.method(method,
        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private long id(HttpResponse<String> response) throws Exception {
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data/id").asLong();
  }

  @Test
  @DisplayName("B-QUA-01：PATCH testCaseId 关联同产品用例成功；跨产品/库用例 42201；0 清空")
  void bugTestCaseIdLink() throws Exception {
    long caseId = id(send("POST", "/api/v1/products/" + productId + "/test-cases",
        "{\"title\":\"来源用例\"}"));
    long otherProduct = id(send("POST", "/api/v1/products",
        "{\"name\":\"他品-" + System.nanoTime() + "\"}"));
    long crossCaseId = id(send("POST", "/api/v1/products/" + otherProduct + "/test-cases",
        "{\"title\":\"他品用例\"}"));
    long libraryId = id(send("POST", "/api/v1/libraries", "{\"name\":\"来源库-" + System.nanoTime() + "\"}"));
    long libraryCaseId = id(send("POST", "/api/v1/libraries/" + libraryId + "/test-cases",
        "{\"title\":\"库内用例\"}"));
    long bugId = id(send("POST", "/api/v1/products/" + productId + "/bugs",
        "{\"title\":\"来源链 Bug\",\"openedBuilds\":\"1\"}"));

    JsonNode linked = json.readTree(send("PATCH", "/api/v1/bugs/" + bugId,
        "{\"testCaseId\":" + caseId + ",\"lockVersion\":0}").body()).at("/data");
    assertEquals(caseId, linked.at("/testCaseId").asLong(), linked.toString());

    HttpResponse<String> cross = send("PATCH", "/api/v1/bugs/" + bugId,
        "{\"testCaseId\":" + crossCaseId + ",\"lockVersion\":1}");
    assertEquals(422, cross.statusCode(), cross.body());
    assertTrue(cross.body().contains("42201"), cross.body());

    HttpResponse<String> fromLibrary = send("PATCH", "/api/v1/bugs/" + bugId,
        "{\"testCaseId\":" + libraryCaseId + ",\"lockVersion\":1}");
    assertEquals(422, fromLibrary.statusCode(), fromLibrary.body());
    assertTrue(fromLibrary.body().contains("42201"), fromLibrary.body());

    JsonNode cleared = json.readTree(send("PATCH", "/api/v1/bugs/" + bugId,
        "{\"testCaseId\":0,\"lockVersion\":1}").body()).at("/data");
    assertTrue(cleared.at("/testCaseId").isNull() || cleared.at("/testCaseId").asLong() == 0, cleared.toString());
  }

  @Test
  @DisplayName("B-PRJ-16：bugs filters[projectId] 过滤生效（bug 表 project_id 列）")
  void bugProjectIdFilter() throws Exception {
    long bugId = id(send("POST", "/api/v1/products/" + productId + "/bugs",
        "{\"title\":\"项目维度 Bug\",\"openedBuilds\":\"1\"}"));

    JsonNode matched = json.readTree(send("GET",
        "/api/v1/products/" + productId + "/bugs?filters%5BprojectId%5D=0", null).body()).at("/data");
    assertTrue(matched.at("/total").asLong() >= 1, matched.toString());
    assertTrue(containsId(matched.at("/items"), bugId), matched.toString());

    assertEquals(0, json.readTree(send("GET",
        "/api/v1/products/" + productId + "/bugs?filters%5BprojectId%5D=999999", null).body())
        .at("/data/total").asLong());
  }

  @Test
  @DisplayName("B-PRJ-17：test-cases filters[executionId]/[projectId] 经测试单子查询联查生效")
  void testCaseDimensionFilters() throws Exception {
    long caseId = id(send("POST", "/api/v1/products/" + productId + "/test-cases",
        "{\"title\":\"执行维度用例\"}"));
    long otherCaseId = id(send("POST", "/api/v1/products/" + productId + "/test-cases",
        "{\"title\":\"未入执行用例\"}"));
    long runId = id(send("POST", "/api/v1/products/" + productId + "/test-runs",
        "{\"executionId\":" + executionId + ",\"name\":\"维度联查测试单\","
            + "\"beginDate\":\"2026-01-10\",\"endDate\":\"2026-01-20\"}"));
    assertEquals(200, send("POST", "/api/v1/test-runs/" + runId + "/cases",
        "{\"caseIds\":[" + caseId + "]}").statusCode());

    JsonNode byExecution = json.readTree(send("GET",
        "/api/v1/products/" + productId + "/test-cases?filters%5BexecutionId%5D=" + executionId, null).body())
        .at("/data");
    assertEquals(1, byExecution.at("/total").asLong(), byExecution.toString());
    assertTrue(containsId(byExecution.at("/items"), caseId), byExecution.toString());

    JsonNode byProject = json.readTree(send("GET",
        "/api/v1/products/" + productId + "/test-cases?filters%5BprojectId%5D=" + projectId, null).body())
        .at("/data");
    assertEquals(1, byProject.at("/total").asLong(), byProject.toString());

    assertEquals(0, json.readTree(send("GET",
        "/api/v1/products/" + productId + "/test-cases?filters%5BexecutionId%5D=999999", null).body())
        .at("/data/total").asLong(), "未入执行的用例不得命中");

    JsonNode stillVisible = json.readTree(send("GET",
        "/api/v1/products/" + productId + "/test-cases", null).body()).at("/data");
    assertTrue(containsId(stillVisible.at("/items"), otherCaseId), stillVisible.toString());
  }

  private static boolean containsId(JsonNode items, long id) {
    for (JsonNode item : items) {
      if (item.at("/id").asLong() == id) {
        return true;
      }
    }
    return false;
  }
}
