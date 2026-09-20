package net.zentao.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 周报幂等与归一（workspace 卡 §8）：同周重复 GET 只留一行、过去周幂等、日期归一到周一。 */
class WeeklyReportServiceTest extends ApiTestSupport {

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  @Test
  @DisplayName("current：date 归一到周一、weekEnd=周日；同周重复请求幂等（历史列表只留一行）")
  void currentIsIdempotentPerWeek() throws Exception {
    long product = createProduct(admin, "周报产品" + System.nanoTime());
    long project = createProject(admin, "周报项目" + System.nanoTime(), product, null);
    long execution = createExecution(admin, project, "周报执行");
    long task = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"周报任务\",\"estimateHours\":10,\"deadline\":\"2026-12-20\"}", admin));
    assertEquals(200, send("POST", "/api/v1/tasks/" + task + "/start",
        "{\"leftHours\":8}", admin).statusCode(), "任务必须有工时口径数据");

    JsonNode monday = data(send("GET",
        "/api/v1/projects/" + project + "/weekly-reports/current?date=2026-09-16", null, admin));
    assertEquals("2026-09-14", monday.at("/weekStart").asText(), "周内任意一天归一到周一");
    assertEquals("2026-09-20", monday.at("/weekEnd").asText(), "weekEnd=周日");
    assertTrue(monday.at("/weekSN").asInt() >= 1, monday.toString());
    assertTrue(monday.has("analysis"), "analysis 结论现算：" + monday);
    assertTrue(monday.has("finished") && monday.has("postponed") && monday.has("nextWeek"),
        "三张任务表齐备：" + monday);

    JsonNode again = data(send("GET", "/api/v1/projects/" + project + "/weekly-reports/current?date=2026-09-18",
        null, admin));
    assertEquals(monday.at("/pv").asText(), again.at("/pv").asText(), "同周重算数字不变（幂等）");
    assertEquals(monday.at("/ev").asText(), again.at("/ev").asText(), "同周重算数字不变（幂等）");

    JsonNode history = data(send("GET", "/api/v1/projects/" + project + "/weekly-reports", null, admin));
    assertEquals(1, history.at("/total").asLong(), "同周只留一行：" + history);
  }

  @Test
  @DisplayName("过去周幂等：两次请求同一过去周，历史行数不增且数字一致")
  void pastWeekIdempotent() throws Exception {
    long product = createProduct(admin, "过去周产品" + System.nanoTime());
    long project = createProject(admin, "过去周项目" + System.nanoTime(), product, null);

    JsonNode first = data(send("GET",
        "/api/v1/projects/" + project + "/weekly-reports/current?date=2026-08-05", null, admin));
    JsonNode second = data(send("GET",
        "/api/v1/projects/" + project + "/weekly-reports/current?date=2026-08-07", null, admin));
    assertEquals(first.at("/weekStart").asText(), second.at("/weekStart").asText(), "同一周归一");
    assertEquals(first.at("/sv").asText(), second.at("/sv").asText(), "过去周重算结果一致");

    JsonNode history = data(send("GET", "/api/v1/projects/" + project + "/weekly-reports", null, admin));
    assertEquals(1, history.at("/total").asLong(), history.toString());
  }

  @Test
  @DisplayName("不可见项目 → 40302；无 weekly-report-view 码 → 40301")
  void permissions() throws Exception {
    long product = createProduct(admin, "周报权限产品" + System.nanoTime());
    long project = createProject(admin, "周报权限项目" + System.nanoTime(), product, "\"acl\":\"private\"");

    String outsider = accountWithPrivileges(admin, "wrout" + System.nanoTime(), "\"weekly-report-view\"");
    HttpResponse<String> denied =
        send("GET", "/api/v1/projects/" + project + "/weekly-reports/current", null, outsider);
    assertEquals(403, denied.statusCode(), denied.body());
    assertTrue(denied.body().contains("40302"), denied.body());

    String noCode = accountWithPrivileges(admin, "wrnone" + System.nanoTime(), "\"project-view\"");
    HttpResponse<String> forbidden =
        send("GET", "/api/v1/projects/" + project + "/weekly-reports/current", null, noCode);
    assertEquals(403, forbidden.statusCode(), forbidden.body());
    assertTrue(forbidden.body().contains("40301"), forbidden.body());
  }
}
