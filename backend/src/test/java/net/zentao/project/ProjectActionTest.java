package net.zentao.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-2 三型状态机与动作端点（project 卡 §4/§8）：project 型全矩阵 + program/execution 关键边、
 * activate 日期守卫 42203、delay 落库可查、realBeganDate/realEndDate/firstEndDate 回填、close/activate 通知 pm。
 */
class ProjectActionTest extends net.zentao.ApiTestSupport {

  private String adminCookie;
  private long productId;

  @BeforeEach
  void setUp() throws Exception {
    adminCookie = login("admin", "admin123");
    productId = dataId(send("POST", "/api/v1/products",
        "{\"name\":\"动作产品" + System.nanoTime() + "\",\"acl\":\"public\"}", adminCookie));
  }

  private long createProject(String name, String extraJson) throws Exception {
    return dataId(send("POST", "/api/v1/projects",
        "{\"name\":\"" + name + "\",\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-12-31\",\"productIds\":["
            + productId + "]" + (extraJson == null ? "" : "," + extraJson) + "}",
        adminCookie));
  }

  private long createProgram(String name) throws Exception {
    return dataId(send("POST", "/api/v1/programs", "{\"name\":\"" + name + "\"}", adminCookie));
  }

  private long createExecution(long projectId) throws Exception {
    return dataId(send("POST", "/api/v1/projects/" + projectId + "/executions",
        "{\"type\":\"sprint\",\"name\":\"S-" + System.nanoTime() + "\",\"beginDate\":\"2026-09-01\","
            + "\"endDate\":\"2026-09-30\"}",
        adminCookie));
  }

  private HttpResponse<String> act(String resource, long id, String action, String body) throws Exception {
    return send("POST", "/api/v1/" + resource + "/" + id + "/" + action, body == null ? "{}" : body, adminCookie);
  }

  private void assertStatus(HttpResponse<String> response, String status, String action) throws Exception {
    assertEquals(200, response.statusCode(), action + " → " + response.body());
    assertEquals(status, json.readTree(response.body()).at("/data/status").asText(), action);
  }

  private void assertRejected(HttpResponse<String> response, String code, String action) throws Exception {
    assertEquals(422, response.statusCode(), action + " → " + response.body());
    assertTrue(response.body().contains(code), action + " → " + response.body());
  }

  @Test
  @DisplayName("project 型六动作 × 五状态矩阵：合法迁移生效、from 列外 42202、activate 日期倒挂 42203")
  void projectStateMatrix() throws Exception {
    long id = createProject("矩阵项目", null);

    // wait：只可 start
    assertRejected(act("projects", id, "suspend", null), "42202", "wait suspend");
    assertRejected(act("projects", id, "resume", null), "42202", "wait resume");
    assertRejected(act("projects", id, "delay", null), "42202", "wait delay");
    assertRejected(act("projects", id, "close", null), "42202", "wait close");
    assertRejected(act("projects", id, "activate", "{\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-12-31\"}"),
        "42202", "wait activate");

    HttpResponse<String> started = act("projects", id, "start", "{}");
    assertStatus(started, "doing", "wait start");
    JsonNode view = json.readTree(started.body()).at("/data");
    assertNotNull(view.at("/realBeganDate").asText(), view.toString());
    assertFalse(view.at("/realBeganDate").isNull(), "start 回填 realBeganDate");
    assertEquals("2026-12-31", view.at("/firstEndDate").asText(), "start 记住首次计划完成日");

    assertRejected(act("projects", id, "start", null), "42202", "doing start");
    assertRejected(act("projects", id, "resume", null), "42202", "doing resume");

    assertStatus(act("projects", id, "suspend", "{\"comment\":\"挂起\"}"), "suspended", "doing suspend");
    assertRejected(act("projects", id, "suspend", null), "42202", "suspended suspend");
    assertRejected(act("projects", id, "delay", null), "42202", "suspended delay");
    assertStatus(act("projects", id, "resume", null), "doing", "suspended resume");
    assertStatus(act("projects", id, "delay", null), "delay", "doing delay");
    assertRejected(act("projects", id, "delay", null), "42202", "delay delay");
    assertRejected(act("projects", id, "suspend", null), "42202", "delay suspend");

    HttpResponse<String> closed = act("projects", id, "close", "{\"comment\":\"结项\"}");
    assertStatus(closed, "closed", "delay close");
    JsonNode closedView = json.readTree(closed.body()).at("/data");
    assertFalse(closedView.at("/realEndDate").isNull(), "close 回填 realEndDate");
    assertFalse(closedView.at("/closedAt").isNull(), "close 落 closedAt");
    assertRejected(act("projects", id, "close", null), "42202", "closed close");
    assertRejected(act("projects", id, "start", null), "42202", "closed start");

    HttpResponse<String> badDates = act("projects", id, "activate",
        "{\"beginDate\":\"2026-12-01\",\"endDate\":\"2026-09-01\"}");
    assertRejected(badDates, "42203", "activate 日期倒挂");

    HttpResponse<String> activated = act("projects", id, "activate",
        "{\"beginDate\":\"2026-09-05\",\"endDate\":\"2026-12-20\"}");
    assertStatus(activated, "doing", "closed activate");
    JsonNode activatedView = json.readTree(activated.body()).at("/data");
    assertTrue(activatedView.at("/closedAt").isNull(), "activate 清 closedAt");
    assertTrue(activatedView.at("/realEndDate").isNull(), "activate 清 realEndDate");
    assertEquals("2026-09-05", activatedView.at("/beginDate").asText(), "activate 接受新区间");

    JsonNode activities = json.readTree(
        send("GET", "/api/v1/projects/" + id + "/activities?limit=50", null, adminCookie).body()).at("/data");
    String actions = activities.at("/items").toString();
    for (String expected : new String[] {"started", "suspended", "resumed", "delayed", "closed", "activated"}) {
      assertTrue(actions.contains("\"action\":\"" + expected + "\""), expected + " 未落动态流：" + actions);
    }
    assertTrue(actions.contains("结项"), "动作备注落动态流：" + actions);
  }

  @Test
  @DisplayName("program / execution 关键边：同构迁移、执行 close 与 realEndDate 回填")
  void programAndExecutionEdges() throws Exception {
    long program = createProgram("矩阵项目集");
    assertStatus(act("programs", program, "start", null), "doing", "program start");
    assertStatus(act("programs", program, "suspend", null), "suspended", "program suspend");
    assertStatus(act("programs", program, "close", null), "closed", "program 从 suspended 关闭");
    assertStatus(act("programs", program, "activate",
        "{\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-12-31\"}"), "doing", "program activate");

    long project = createProject("矩阵执行所属", null);
    long execution = createExecution(project);
    assertStatus(act("executions", execution, "start", null), "doing", "execution start");
    HttpResponse<String> closed = act("executions", execution, "close", null);
    assertStatus(closed, "closed", "execution close");
    assertFalse(json.readTree(closed.body()).at("/data/realEndDate").isNull(), "执行 close 回填 realEndDate");
  }

  @Test
  @DisplayName("delay 落库：filters[status]=delay 可查（区别于旧库派生展示态）")
  void delayIsPersisted() throws Exception {
    long id = createProject("延期项目", null);
    act("projects", id, "start", null);
    assertStatus(act("projects", id, "delay", "{\"comment\":\"需求变更\"}"), "delay", "delay");

    JsonNode delayed = data(send("GET", "/api/v1/projects?filters%5Bstatus%5D=delay", null, adminCookie));
    boolean found = false;
    for (JsonNode item : delayed.at("/items")) {
      if (item.at("/id").asLong() == id) {
        found = true;
      }
    }
    assertTrue(found, "delay 项目应在 filters[status]=delay 列表中：" + delayed);
  }

  @Test
  @DisplayName("close/activate 通知 pm：type=project-closed/activated 且标题为项目名")
  void pmIsNotified() throws Exception {
    String account = "proj-pm-" + System.nanoTime();
    long groupId = dataId(send("POST", "/api/v1/groups", "{\"name\":\"PM通知组" + System.nanoTime() + "\"}",
        adminCookie));
    send("POST", "/api/v1/accounts", "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\""
        + account + "\",\"groupIds\":[" + groupId + "]}", adminCookie);
    long id = createProject("通知项目", "\"pm\":\"" + account + "\"");
    act("projects", id, "start", null);
    act("projects", id, "close", "{}");

    String pmCookie = login(account, "secret123");
    JsonNode notifications = data(send("GET", "/api/v1/notifications", null, pmCookie));
    String body = notifications.toString();
    assertTrue(body.contains("project-close"), "pm 应收到 project-close 通知：" + body);
    assertTrue(body.contains("通知项目"), "通知标题为项目名：" + body);
  }
}
