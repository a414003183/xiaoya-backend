package net.zentao.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * T-11 质量域数据权限 IT（quality 卡 §7/§8，Testcontainers MySQL 8.4 真库）：
 * private 产品的 Bug/用例/测试单外人列表 0 条 + 详情 40302；超管全见；
 * Library 全员可读、写需权限码；Suite private 行级仅创建者；Report 随冗余 productId 走产品 ACL。
 *
 * <p>与 H2 单测的分工：此处验的是真 SQL 下的 DataScope 注入与跨产品 ACL 组合，
 * 而不是状态机守卫（后者在 {@link BugStateMachineTest} 等单测内已覆盖）。
 */
class QualityDataScopeIT extends MySqlContainerSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private long outsiderGroupId;

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

  /** 产品查看者组：质量域读权限齐备但**无** library-create/library-edit 之外的写权限。 */
  private void ensureOutsider(String account) throws Exception {
    if (outsiderGroupId == 0) {
      outsiderGroupId = dataId(send("POST", "/api/v1/roles",
          "{\"name\":\"IT 质量组 " + System.nanoTime() + "\"}", adminCookie));
      send("PUT", "/api/v1/roles/" + outsiderGroupId + "/privileges",
          "{\"codes\":[\"bug-view\",\"bug-create\",\"bug-edit\",\"bug-resolve\",\"testcase-view\","
              + "\"testcase-create\",\"suite-view\",\"suite-create\",\"library-view\",\"testrun-view\","
              + "\"report-view\",\"product-view\"]}",
          adminCookie);
    }
    HttpResponse<String> created = send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"" + account
            + "\",\"roleIds\":[" + outsiderGroupId + "]}",
        adminCookie);
    assertTrue(created.statusCode() == 200 || created.statusCode() == 422, created.body());
  }

  private long privateProduct() throws Exception {
    return dataId(send("POST", "/api/v1/products",
        "{\"name\":\"IT 私有产品 " + System.nanoTime() + "\",\"acl\":\"private\"}", adminCookie));
  }

  private long publicProduct() throws Exception {
    return dataId(send("POST", "/api/v1/products",
        "{\"name\":\"IT 公开产品 " + System.nanoTime() + "\",\"acl\":\"public\"}", adminCookie));
  }

  /** 执行（挂在公开产品下，用于 Report 的 productId 冗余）。 */
  private long freshExecution(long productId) throws Exception {
    long project = dataId(send("POST", "/api/v1/projects",
        "{\"name\":\"IT 质量项目 " + System.nanoTime() + "\",\"beginDate\":\"2026-09-01\","
            + "\"endDate\":\"2026-12-31\",\"acl\":\"open\",\"productIds\":[" + productId + "]}",
        adminCookie));
    return dataId(send("POST", "/api/v1/projects/" + project + "/executions",
        "{\"type\":\"sprint\",\"name\":\"IT 质量执行 " + System.nanoTime() + "\",\"beginDate\":\"2026-09-01\","
            + "\"endDate\":\"2026-09-30\"}",
        adminCookie));
  }

  /** private 产品下的 Bug/用例/测试单 + 各自的列表与详情探针。 */
  private void assertPrivateProductQualityHidden(long productId, String cookie, String tag) throws Exception {
    long bug = dataId(send("POST", "/api/v1/products/" + productId + "/bugs",
        "{\"title\":\"IT " + tag + " Bug\",\"openedBuilds\":\"1\"}", adminCookie));
    long testCase = dataId(send("POST", "/api/v1/products/" + productId + "/test-cases",
        "{\"title\":\"IT " + tag + " 用例\"}", adminCookie));
    long execution = freshExecution(publicProduct());
    long testRun = dataId(send("POST", "/api/v1/products/" + productId + "/test-runs",
        "{\"executionId\":" + execution + ",\"name\":\"IT " + tag + " 测试单\",\"priority\":3,"
            + "\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}",
        adminCookie));

    assertEquals(0, data(send("GET", "/api/v1/products/" + productId + "/bugs", null, cookie))
        .at("/total").asLong(), "外人 Bug 列表应为 0 条");
    assertEquals(0, data(send("GET", "/api/v1/products/" + productId + "/test-cases", null, cookie))
        .at("/total").asLong(), "外人用例列表应为 0 条");
    assertEquals(0, data(send("GET", "/api/v1/products/" + productId + "/test-runs", null, cookie))
        .at("/total").asLong(), "外人测试单列表应为 0 条");

    for (String path : new String[] {"/api/v1/bugs/" + bug, "/api/v1/test-cases/" + testCase,
        "/api/v1/test-runs/" + testRun}) {
      HttpResponse<String> detail = send("GET", path, null, cookie);
      assertEquals(403, detail.statusCode(), path + " 详情应答：" + detail.body());
      assertTrue(detail.body().contains("40302"), path + " 详情应答：" + detail.body());
    }

    // 超管不受限：同样三条详情可见
    assertEquals(200, send("GET", "/api/v1/bugs/" + bug, null, adminCookie).statusCode());
    assertEquals(200, send("GET", "/api/v1/test-cases/" + testCase, null, adminCookie).statusCode());
    assertEquals(200, send("GET", "/api/v1/test-runs/" + testRun, null, adminCookie).statusCode());
  }

  @Test
  @DisplayName("private 产品：外人 Bug/用例/测试单列表 0 条 + 详情 40302；超管全见")
  void privateProductQualityAcl() throws Exception {
    ensureOutsider("it-quality-guest");
    String guestCookie = loginAs("it-quality-guest", "secret123");
    assertPrivateProductQualityHidden(privateProduct(), guestCookie, "私有");
  }

  @Test
  @DisplayName("用例库：全员可读（无产品 ACL），写需 library-create/library-edit → 40301")
  void libraryReadableByAllButWriteNeedsPrivilege() throws Exception {
    ensureOutsider("it-quality-lib");
    String guestCookie = loginAs("it-quality-lib", "secret123");
    long library = dataId(send("POST", "/api/v1/libraries",
        "{\"name\":\"IT 用例库 " + System.nanoTime() + "\"}", adminCookie));

    JsonNode list = data(send("GET", "/api/v1/libraries", null, guestCookie));
    assertTrue(list.at("/total").asLong() >= 1, "库列表应全员可读：" + list);
    assertEquals(200, send("GET", "/api/v1/libraries/" + library, null, guestCookie).statusCode());

    HttpResponse<String> denied = send("POST", "/api/v1/libraries",
        "{\"name\":\"越权库 " + System.nanoTime() + "\"}", guestCookie);
    assertEquals(403, denied.statusCode(), denied.body());
    assertTrue(denied.body().contains("40301"), denied.body());
    HttpResponse<String> deniedPatch = send("PATCH", "/api/v1/libraries/" + library,
        "{\"name\":\"越权改名\",\"lockVersion\":0}", guestCookie);
    assertEquals(403, deniedPatch.statusCode(), deniedPatch.body());
  }

  @Test
  @DisplayName("套件 row-level：private 套件仅创建者可见（他人列表不含、详情 40302），public 可见")
  void privateSuiteRowLevel() throws Exception {
    ensureOutsider("it-quality-suite");
    String guestCookie = loginAs("it-quality-suite", "secret123");
    long product = publicProduct();
    // 客人自建 private 套件（创建者=自己），管理员另建一条 private 套件
    long guestSuite = dataId(send("POST", "/api/v1/products/" + product + "/suites",
        "{\"name\":\"IT 客人私有套件 " + System.nanoTime() + "\",\"type\":\"private\"}", guestCookie));
    long adminSuite = dataId(send("POST", "/api/v1/products/" + product + "/suites",
        "{\"name\":\"IT 管理员私有套件 " + System.nanoTime() + "\",\"type\":\"private\"}", adminCookie));
    long publicSuite = dataId(send("POST", "/api/v1/products/" + product + "/suites",
        "{\"name\":\"IT 公开套件 " + System.nanoTime() + "\",\"type\":\"public\"}", adminCookie));

    JsonNode guestList = data(send("GET", "/api/v1/products/" + product + "/suites", null, guestCookie));
    String guestIds = guestList.at("/items").toString();
    assertTrue(guestIds.contains("\"id\":" + guestSuite), "创建者应见自己的 private 套件：" + guestList);
    assertTrue(!guestIds.contains("\"id\":" + adminSuite), "他人 private 套件不得出现在列表：" + guestList);
    assertTrue(guestIds.contains("\"id\":" + publicSuite), "public 套件应可见：" + guestList);

    HttpResponse<String> denied = send("GET", "/api/v1/suites/" + adminSuite, null, guestCookie);
    assertEquals(403, denied.statusCode(), denied.body());
    assertTrue(denied.body().contains("40302"), denied.body());
    assertEquals(200, send("GET", "/api/v1/suites/" + guestSuite, null, guestCookie).statusCode());

    // 超管不受 private 行级限制
    assertEquals(200, send("GET", "/api/v1/suites/" + adminSuite, null, adminCookie).statusCode());
  }

  @Test
  @DisplayName("Report 随冗余 productId 走产品 ACL：外人对私有产品执行下报告 40302，超管可见")
  void reportFollowsProductAcl() throws Exception {
    ensureOutsider("it-quality-report");
    String guestCookie = loginAs("it-quality-report", "secret123");
    long privateProduct = privateProduct();
    long execution = freshExecution(privateProduct);
    long report = dataId(send("POST", "/api/v1/executions/" + execution + "/reports",
        "{\"title\":\"IT 私有报告 " + System.nanoTime() + "\",\"testRunIds\":[],"
            + "\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\",\"content\":\"正文\"}",
        adminCookie));

    JsonNode guestList = data(send("GET", "/api/v1/executions/" + execution + "/reports", null, guestCookie));
    assertEquals(0, guestList.at("/total").asLong(), "外人报告列表应为 0 条：" + guestList);
    HttpResponse<String> denied = send("GET", "/api/v1/reports/" + report, null, guestCookie);
    assertEquals(403, denied.statusCode(), denied.body());
    assertTrue(denied.body().contains("40302"), denied.body());
    assertEquals(200, send("GET", "/api/v1/reports/" + report, null, adminCookie).statusCode());
  }

  @Test
  @DisplayName("公开产品下本域实体对普通账号可见（对照组，证明上面 0 条是 ACL 而非权限码所致）")
  void publicProductVisibleToOutsider() throws Exception {
    ensureOutsider("it-quality-open");
    String guestCookie = loginAs("it-quality-open", "secret123");
    long product = publicProduct();
    dataId(send("POST", "/api/v1/products/" + product + "/bugs",
        "{\"title\":\"IT 公开 Bug\",\"openedBuilds\":\"1\"}", adminCookie));
    dataId(send("POST", "/api/v1/products/" + product + "/test-cases", "{\"title\":\"IT 公开用例\"}", adminCookie));

    assertNotEquals(0, data(send("GET", "/api/v1/products/" + product + "/bugs", null, guestCookie))
        .at("/total").asLong(), "公开产品 Bug 列表应可见");
    assertNotEquals(0, data(send("GET", "/api/v1/products/" + product + "/test-cases", null, guestCookie))
        .at("/total").asLong(), "公开产品用例列表应可见");
  }
}
