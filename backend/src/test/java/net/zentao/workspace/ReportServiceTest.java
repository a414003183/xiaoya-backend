package net.zentao.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 三个固定报表口径（workspace 卡 §5/§8）+ 燃尽报表结构、可见性 40302。 */
class ReportServiceTest extends ApiTestSupport {

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  @Test
  @DisplayName("story-summary：按状态/优先级/阶段/类型分组，排除已删")
  void storySummary() throws Exception {
    long product = createProduct(admin, "报表产品" + System.nanoTime());
    long active = createStory(admin, product, "报表需求A", "\"needNotReview\":true");
    assertEquals(200, send("POST", "/api/v1/stories/" + active + "/submit-review", "{}", admin).statusCode());
    createStory(admin, product, "报表需求B", null);

    JsonNode report = data(send("GET", "/api/v1/products/" + product + "/reports/story-summary", null, admin));
    assertEquals(2, report.at("/total").asLong(), report.toString());
    assertTrue(report.at("/byStatus").toString().contains("active"), report.toString());
    assertTrue(report.at("/byStatus").toString().contains("draft"), report.toString());
    assertTrue(report.at("/byPriority").size() >= 1, report.toString());
    assertTrue(report.has("byStage") && report.has("byType"), report.toString());
  }

  @Test
  @DisplayName("bug-distribution：severity/status 分组，resolution 空计入 unresolved 桶")
  void bugDistribution() throws Exception {
    long product = createProduct(admin, "缺陷报表产品" + System.nanoTime());
    long bug = dataId(send("POST", "/api/v1/products/" + product + "/bugs",
        "{\"title\":\"待解决缺陷\",\"severity\":1}", admin));
    assertEquals(200, send("POST", "/api/v1/bugs/" + bug + "/resolve",
        "{\"resolution\":\"fixed\",\"resolvedBuild\":\"v1\"}", admin).statusCode());
    dataId(send("POST", "/api/v1/products/" + product + "/bugs", "{\"title\":\"未解决缺陷\",\"severity\":2}", admin));

    JsonNode report = data(send("GET", "/api/v1/products/" + product + "/reports/bug-distribution", null, admin));
    assertEquals(2, report.at("/total").asLong(), report.toString());
    assertTrue(report.at("/byResolution").toString().contains("fixed"), report.toString());
    assertTrue(report.at("/byResolution").toString().contains("unresolved"), "空 resolution 计 unresolved：" + report);
    assertTrue(report.at("/bySeverity").toString().contains("\"severity\":1"), report.toString());
    assertTrue(report.at("/byStatus").toString().contains("active"), report.toString());
  }

  @Test
  @DisplayName("case-pass-rate：passRate=passed/(total−na)×100；全 n/a 时 passRate=null")
  void casePassRate() throws Exception {
    long product = createProduct(admin, "通过率产品" + System.nanoTime());
    long project = createProject(admin, "通过率项目" + System.nanoTime(), product, null);
    long execution = createExecution(admin, project, "通过率执行");
    long caseOne = dataId(send("POST", "/api/v1/products/" + product + "/test-cases",
        "{\"title\":\"用例一\"}", admin));
    long caseTwo = dataId(send("POST", "/api/v1/products/" + product + "/test-cases",
        "{\"title\":\"用例二\"}", admin));
    long testRun = dataId(send("POST", "/api/v1/products/" + product + "/test-runs",
        "{\"executionId\":" + execution + ",\"name\":\"通过率单\",\"beginDate\":\"2026-09-01\","
            + "\"endDate\":\"2026-09-30\"}", admin));
    assertEquals(200, send("POST", "/api/v1/test-runs/" + testRun + "/cases",
        "{\"caseIds\":[" + caseOne + "," + caseTwo + "]}", admin).statusCode());
    assertEquals(200, send("POST", "/api/v1/test-runs/" + testRun + "/start", null, admin).statusCode(),
        "record-result 仅在 doing 态可用（test-run.yml）");
    assertEquals(200, send("POST", "/api/v1/test-runs/" + testRun + "/cases/" + caseOne + "/result",
        "{\"result\":\"pass\"}", admin).statusCode());
    assertEquals(200, send("POST", "/api/v1/test-runs/" + testRun + "/cases/" + caseTwo + "/result",
        "{\"result\":\"fail\"}", admin).statusCode());

    JsonNode report = data(send("GET", "/api/v1/test-runs/" + testRun + "/reports/case-pass-rate", null, admin));
    assertEquals(2, report.at("/total").asLong(), report.toString());
    assertEquals(1, report.at("/passed").asLong(), report.toString());
    assertEquals(1, report.at("/failed").asLong(), report.toString());
    assertEquals(0, report.at("/na").asLong(), report.toString());
    assertEquals(50.0, report.at("/passRate").asDouble(), 0.001, "1/(2−0)×100：" + report);

    assertEquals(200, send("POST", "/api/v1/test-runs/" + testRun + "/cases/" + caseOne + "/result",
        "{\"result\":\"n/a\"}", admin).statusCode());
    assertEquals(200, send("POST", "/api/v1/test-runs/" + testRun + "/cases/" + caseTwo + "/result",
        "{\"result\":\"n/a\"}", admin).statusCode());
    JsonNode allNa = data(send("GET", "/api/v1/test-runs/" + testRun + "/reports/case-pass-rate", null, admin));
    assertTrue(allNa.at("/passRate").isNull(), "分母 total−na = 0 → passRate=null：" + allNa);
    assertNull(allNa.at("/passRate").asText(null), allNa.toString());
  }

  @Test
  @DisplayName("burn：首访落当日快照（含执行级日行）并给出 ideal/remaining 双线，重复访问行数不增")
  void burnReport() throws Exception {
    long product = createProduct(admin, "燃尽产品" + System.nanoTime());
    long project = createProject(admin, "燃尽项目" + System.nanoTime(), product, null);
    long execution = createExecution(admin, project, "燃尽执行");
    long task = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"燃尽任务\",\"estimateHours\":16}", admin));
    assertEquals(200, send("POST", "/api/v1/tasks/" + task + "/start",
        "{\"leftHours\":12}", admin).statusCode());

    JsonNode report = data(send("GET", "/api/v1/executions/" + execution + "/reports/burn", null, admin));
    assertEquals("2026-09-01", report.at("/beginDate").asText(), report.toString());
    assertEquals("2026-09-30", report.at("/endDate").asText(), report.toString());
    assertEquals(report.at("/dates").size(), report.at("/ideal").size(), "ideal 与日期轴等长");
    assertEquals(report.at("/dates").size(), report.at("/remaining").size(), "remaining 与日期轴等长");
    assertTrue(report.at("/remaining").get(report.at("/remaining").size() - 1).asDouble() > 0, report.toString());

    JsonNode again = data(send("GET", "/api/v1/executions/" + execution + "/reports/burn", null, admin));
    assertEquals(report.at("/remaining").toString(), again.at("/remaining").toString(), "同日重算幂等");
  }

  @Test
  @DisplayName("不可见产品/执行 → 40302")
  void reportPermissions() throws Exception {
    long product = createProduct(admin, "报表权限产品" + System.nanoTime());
    String outsider = accountWithPrivileges(admin, "rpout" + System.nanoTime(), "\"report-view\"");
    assertEquals(200, send("GET", "/api/v1/products/" + product + "/reports/story-summary", null, outsider)
        .statusCode(), "公共产品报表可见");

    assertEquals(200, send("PATCH", "/api/v1/products/" + product,
        "{\"acl\":\"private\",\"lockVersion\":0}", admin).statusCode());
    HttpResponse<String> story =
        send("GET", "/api/v1/products/" + product + "/reports/story-summary", null, outsider);
    assertEquals(403, story.statusCode(), story.body());
    assertTrue(story.body().contains("40302"), story.body());

    long project = createProject(admin, "报表权限项目" + System.nanoTime(), product, "\"acl\":\"private\"");
    long execution = createExecution(admin, project, "报表权限执行");
    HttpResponse<String> burn = send("GET", "/api/v1/executions/" + execution + "/reports/burn", null, outsider);
    assertEquals(403, burn.statusCode(), burn.body());
    assertTrue(burn.body().contains("40302"), burn.body());
  }
}
