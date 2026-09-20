package net.zentao.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 待办生命周期与守卫（workspace 卡 §4/§8）：五动作迁移矩阵、activate 清四列、
 * assign 非本人与通知、字段守卫 42201、乐观锁 40901、批量部分成功与上限。
 */
class TodoLifecycleTest extends ApiTestSupport {

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  @Test
  @DisplayName("五动作矩阵：wait start→doing、finish 落 finished*、activate 清四列、close 落 closed*、非法迁移 42202")
  void lifecycleMatrix() throws Exception {
    long todo = createTodo("{\"title\":\"矩阵待办\"}");

    assertEquals("wait", view(todo).at("/status").asText(), "创建默认 wait");
    assertRejected(act(todo, "activate", "{}"), "42202", "wait activate");

    assertEquals("doing", view(act(todo, "start", null)).at("/status").asText(), "wait start");
    assertRejected(act(todo, "start", null), "42202", "doing start");

    JsonNode finished = view(act(todo, "finish", null));
    assertEquals("done", finished.at("/status").asText(), "doing finish");
    assertEquals("admin", finished.at("/finishedBy").asText(), "finish 落 finishedBy");
    assertFalse(finished.at("/finishedAt").isNull(), "finish 落 finishedAt");

    JsonNode activated = view(act(todo, "activate", "{\"comment\":\"重开\"}"));
    assertEquals("wait", activated.at("/status").asText(), "done activate");
    assertTrue(activated.at("/finishedBy").isNull(), "activate 清 finishedBy");
    assertTrue(activated.at("/finishedAt").isNull(), "activate 清 finishedAt");
    assertTrue(activated.at("/closedBy").isNull(), "activate 清 closedBy");
    assertTrue(activated.at("/closedAt").isNull(), "activate 清 closedAt");

    JsonNode closed = view(act(todo, "close", null));
    assertEquals("closed", closed.at("/status").asText(), "wait close");
    assertEquals("admin", closed.at("/closedBy").asText(), "close 落 closedBy");
    assertRejected(act(todo, "start", null), "42202", "closed start");
    assertRejected(act(todo, "finish", null), "42202", "closed finish");
    assertRejected(act(todo, "assign", "{\"assignee\":\"admin\"}"), "42202", "closed assign");

    JsonNode reopened = view(act(todo, "activate", null));
    assertEquals("wait", reopened.at("/status").asText(), "closed activate");
    assertTrue(reopened.at("/closedAt").isNull(), "closed activate 清 closedAt");
  }

  @Test
  @DisplayName("字段守卫：endTime 早于 beginTime / type≠custom 缺 objectId / priority 越界 → 42201 带 fields")
  void fieldGuards() throws Exception {
    HttpResponse<String> badTime =
        send("POST", "/api/v1/todos", "{\"title\":\"时间倒挂\",\"beginTime\":\"10:00\",\"endTime\":\"09:00\"}", admin);
    assertEquals(422, badTime.statusCode(), badTime.body());
    assertTrue(badTime.body().contains("\"endTime\""), badTime.body());

    HttpResponse<String> badFormat =
        send("POST", "/api/v1/todos", "{\"title\":\"格式错\",\"beginTime\":\"十点\"}", admin);
    assertEquals(422, badFormat.statusCode(), badFormat.body());

    HttpResponse<String> noObject = send("POST", "/api/v1/todos", "{\"title\":\"缺对象\",\"type\":\"task\"}", admin);
    assertEquals(422, noObject.statusCode(), noObject.body());
    assertTrue(noObject.body().contains("\"objectId\""), noObject.body());

    HttpResponse<String> badPriority =
        send("POST", "/api/v1/todos", "{\"title\":\"优先级越界\",\"priority\":5}", admin);
    assertEquals(422, badPriority.statusCode(), badPriority.body());

    HttpResponse<String> badTitle = send("POST", "/api/v1/todos", "{\"title\":\"  \"}", admin);
    assertEquals(422, badTitle.statusCode(), badTitle.body());
  }

  @Test
  @DisplayName("指派：assignee=本人 42203；指派他人落 assignedBy/At 且对方收到 todo-assign 通知")
  void assignToOther() throws Exception {
    String other = accountWithPrivileges(admin, "todofinisher", "\"todo-view\"");
    long todo = createTodo("{\"title\":\"待指派\"}");

    assertRejected(act(todo, "assign", "{\"assignee\":\"admin\"}"), "42203", "指派给自己");

    JsonNode assigned = view(act(todo, "assign", "{\"assignee\":\"todofinisher\"}"));
    assertEquals("todofinisher", assigned.at("/assignee").asText(), "assignee 更新");
    assertEquals("admin", assigned.at("/assignedBy").asText(), "assignedBy 落操作人");
    assertFalse(assigned.at("/assignedAt").isNull(), "assignedAt 落时间");

    HttpResponse<String> notifications = send("GET", "/api/v1/notifications", null, other);
    assertEquals(200, notifications.statusCode(), notifications.body());
    assertTrue(notifications.body().contains("todo-assign"), "接收人收到 todo-assign：" + notifications.body());
  }

  @Test
  @DisplayName("乐观锁：PATCH 带错 lockVersion → 40901；正确版本可续写")
  void optimisticLock() throws Exception {
    long todo = createTodo("{\"title\":\"乐观锁\"}");
    HttpResponse<String> patched = send("PATCH", "/api/v1/todos/" + todo,
        "{\"priority\":2,\"lockVersion\":0}", admin);
    assertEquals(200, patched.statusCode(), patched.body());
    assertEquals(1, data(patched).at("/lockVersion").asInt(), "响应 lockVersion 必须回读（决策⑺）");

    HttpResponse<String> stale =
        send("PATCH", "/api/v1/todos/" + todo, "{\"priority\":1,\"lockVersion\":0}", admin);
    assertEquals(409, stale.statusCode(), stale.body());
    assertTrue(stale.body().contains("40901"), stale.body());

    assertEquals(200, send("PATCH", "/api/v1/todos/" + todo, "{\"priority\":1,\"lockVersion\":1}", admin)
        .statusCode(), "新版本可续写");
  }

  @Test
  @DisplayName("批量：创建逐项部分成功、动作逐项互不影响、items 超 50 → 40001")
  void batch() throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/todos/batch",
        "{\"items\":[{\"title\":\"批量一\"},{\"title\":\"\"},{\"title\":\"批量三\"}]}", admin);
    assertEquals(200, created.statusCode(), created.body());
    JsonNode results = data(created).at("/results");
    assertEquals(3, results.size(), created.body());
    assertTrue(results.get(0).at("/ok").asBoolean(), created.body());
    assertFalse(results.get(1).at("/ok").asBoolean(), created.body());
    assertTrue(results.get(1).at("/error").asText().startsWith("42201"), created.body());
    assertTrue(results.get(2).at("/ok").asBoolean(), created.body());

    long first = results.get(0).at("/id").asLong();
    long third = results.get(2).at("/id").asLong();
    HttpResponse<String> acted = send("POST", "/api/v1/todos/batch",
        "{\"ids\":[" + first + "," + third + "],\"action\":\"start\"}", admin);
    assertEquals(200, acted.statusCode(), acted.body());
    assertEquals(2, data(acted).at("/results").size(), acted.body());
    assertEquals("doing", view(first).at("/status").asText(), "批量动作生效");

    StringBuilder many = new StringBuilder("{\"items\":[");
    for (int i = 0; i < 51; i++) {
      many.append(i > 0 ? "," : "").append("{\"title\":\"超量").append(i).append("\"}");
    }
    many.append("]}");
    assertEquals(400, send("POST", "/api/v1/todos/batch", many.toString(), admin).statusCode(), "items 超 50 → 40001");

    String noCode = accountWithPrivileges(admin, "todoviewer", "\"todo-view\"");
    assertEquals(403, send("POST", "/api/v1/todos/batch",
        "{\"ids\":[" + first + "],\"action\":\"finish\"}", noCode).statusCode(), "无动作码 → 40301");
  }

  @Test
  @DisplayName("列表归属与过滤：date 区间/@null、status 过滤、q 命中标题、排序白名单之外的字段 40001")
  void listFilters() throws Exception {
    long dated = createTodo("{\"title\":\"带日期\",\"date\":\"2026-09-10\"}");
    long undated = createTodo("{\"title\":\"待定日期\"}");

    JsonNode open = data(send("GET", "/api/v1/todos?filters%5Bdate%5D=%40null", null, admin));
    assertTrue(open.at("/items").toString().contains("\"待定日期\""), open.toString());
    assertFalse(open.at("/items").toString().contains("带日期"), "已定日期不入待定页签：" + open);

    JsonNode ranged =
        data(send("GET", "/api/v1/todos?filters%5Bdate%5D=2026-09-01..2026-09-30", null, admin));
    assertTrue(ranged.at("/items").toString().contains("带日期"), ranged.toString());

    assertTrue(data(send("GET", "/api/v1/todos?q=%E5%BE%85%E5%AE%9A", null, admin)).at("/items").toString()
        .contains("待定日期"), "q LIKE title");
    assertEquals(400, send("GET", "/api/v1/todos?sort=assignee", null, admin).statusCode(), "非白名单排序 → 40001");
    assertEquals(400, send("GET", "/api/v1/todos?filters%5BobjectId%5D=1", null, admin).statusCode(),
        "非白名单过滤 → 40001");

    assertEquals("wait", view(undated).at("/status").asText(), "id 查询可达");
    assertNotEquals(0, dated);
  }

  private long createTodo(String body) throws Exception {
    return dataId(send("POST", "/api/v1/todos", body, admin));
  }

  private HttpResponse<String> act(long todoId, String action, String body) throws Exception {
    return send("POST", "/api/v1/todos/" + todoId + "/" + action, body, admin);
  }

  private JsonNode view(long todoId) throws Exception {
    return data(send("GET", "/api/v1/todos/" + todoId, null, admin));
  }

  private JsonNode view(HttpResponse<String> response) throws Exception {
    return data(response);
  }

  private void assertRejected(HttpResponse<String> response, String code, String message) {
    assertEquals(422, response.statusCode(), message + "：" + response.body());
    assertTrue(response.body().contains(code), message + "：" + response.body());
  }
}
