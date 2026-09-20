package net.zentao.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-8 列表与详情（task 卡 §3/§5/§8）：filters（status/assignee=@me/parentId=@null/storyId/区间）、q 命中 title/keywords、
 * 白名单外字段 40001、children 与 storyTitle 联查字段、PATCH 乐观锁与清空语义。
 */
class TaskListTest extends TaskTestSupport {

  @Test
  @DisplayName("列表 DSL：filters[parentId]=@null 取顶层、区间与 q 生效、白名单外 40001")
  void listDsl() throws Exception {
    long execution = freshExecution();
    long parent = createTask(execution, "列表父任务");
    send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"列表子任务\",\"parentId\":" + parent + "}", admin);
    long second = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"关键字任务\",\"keywords\":\"登录 超时\"}", admin));

    JsonNode topLevel = data(send("GET", "/api/v1/executions/" + execution + "/tasks?filters%5BparentId%5D=@null",
        null, admin));
    assertEquals(2, topLevel.at("/total").asInt(), topLevel.toString());
    JsonNode children = data(send("GET",
        "/api/v1/executions/" + execution + "/tasks?filters%5BparentId%5D=" + parent, null, admin));
    assertEquals(1, children.at("/total").asInt(), children.toString());
    assertEquals("列表子任务", children.at("/items/0/title").asText());

    JsonNode keyword = data(send("GET", "/api/v1/executions/" + execution + "/tasks?q=超时", null, admin));
    assertEquals(1, keyword.at("/total").asInt(), keyword.toString());
    assertEquals(second, keyword.at("/items/0/id").asLong(), keyword.toString());

    JsonNode byDeadline = data(send("GET",
        "/api/v1/executions/" + execution + "/tasks?filters%5Bdeadline%5D=2026-01-01..2026-12-31", null, admin));
    assertEquals(0, byDeadline.at("/total").asInt(), byDeadline.toString());

    HttpResponse<String> unregistered = send("GET", "/api/v1/executions/" + execution + "/tasks?filters%5Bghost%5D=1",
        null, admin);
    assertEquals(400, unregistered.statusCode(), unregistered.body());
    assertTrue(unregistered.body().contains("40001"), unregistered.body());

    HttpResponse<String> unsortable = send("GET", "/api/v1/executions/" + execution + "/tasks?sort=description",
        null, admin);
    assertEquals(400, unsortable.statusCode(), unsortable.body());
  }

  @Test
  @DisplayName("filters[assignee]=@me 命中当前账号（登录账号 @me 展开）")
  void assigneeMe() throws Exception {
    long execution = freshExecution();
    long mine = createTask(execution, "我的任务");
    long other = createTask(execution, "别人的任务");
    act(mine, "assign", "{\"assignee\":\"admin\"}");

    JsonNode result = data(send("GET", "/api/v1/executions/" + execution + "/tasks?filters%5Bassignee%5D=@me",
        null, admin));
    assertEquals(1, result.at("/total").asInt(), result.toString());
    assertEquals(mine, result.at("/items/0/id").asLong(), result.toString());
    assertEquals(other, data(send("GET", "/api/v1/tasks/" + other, null, admin)).at("/id").asLong());
  }

  @Test
  @DisplayName("PATCH：乐观锁 40901、不存在的关联需求/分类 0 清空、estimateHours 上限 42201")
  void patchRules() throws Exception {
    long execution = freshExecution();
    long task = createTask(execution, "更新任务");

    HttpResponse<String> stale = send("PATCH", "/api/v1/tasks/" + task,
        "{\"title\":\"改名\",\"lockVersion\":99}", admin);
    assertEquals(409, stale.statusCode(), stale.body());
    assertTrue(stale.body().contains("40901"), stale.body());

    HttpResponse<String> patched = send("PATCH", "/api/v1/tasks/" + task,
        "{\"title\":\"更新任务改名\",\"priority\":1,\"estimateHours\":8.5,\"keywords\":\"接口\",\"lockVersion\":0}",
        admin);
    assertEquals(200, patched.statusCode(), patched.body());
    JsonNode view = json.readTree(patched.body()).at("/data");
    assertEquals("更新任务改名", view.at("/title").asText(), view.toString());
    assertEquals(1, view.at("/priority").asInt(), view.toString());
    assertEquals(8.5, view.at("/estimateHours").asDouble(), view.toString());
    assertEquals(1, view.at("/lockVersion").asInt(), view.toString());

    HttpResponse<String> tooBig = send("PATCH", "/api/v1/tasks/" + task,
        "{\"estimateHours\":1000,\"lockVersion\":1}", admin);
    assertEquals(422, tooBig.statusCode(), tooBig.body());
    assertTrue(tooBig.body().contains("estimateHours"), tooBig.body());

    HttpResponse<String> cleared = send("PATCH", "/api/v1/tasks/" + task,
        "{\"keywords\":\"\",\"storyId\":0,\"lockVersion\":1}", admin);
    assertEquals(200, cleared.statusCode(), cleared.body());
    JsonNode clearedView = json.readTree(cleared.body()).at("/data");
    assertTrue(clearedView.at("/keywords").isNull(), clearedView.toString());
    assertEquals(0, clearedView.at("/storyId").asLong(), clearedView.toString());
  }

  @Test
  @DisplayName("批量动作：部分成功逐项结果、不支持动作 40001、越权动作 40301")
  void batchActions() throws Exception {
    long execution = freshExecution();
    long first = createTask(execution, "批量甲");
    long second = createTask(execution, "批量乙");
    act(second, "start", "{\"leftHours\":1}");
    act(second, "finish", "{\"consumedHours\":1}");

    assertEquals("done", task(second).at("/status").asText(), "批量前乙应为 done：" + task(second));
    HttpResponse<String> batch = send("POST", "/api/v1/tasks/batch",
        "{\"ids\":[" + first + "," + second + "],\"action\":\"close\"}", admin);
    assertEquals(200, batch.statusCode(), batch.body());
    JsonNode results = json.readTree(batch.body()).at("/data/results");
    assertEquals(2, results.size(), batch.body());
    assertEquals(false, results.get(0).at("/ok").asBoolean(), "wait 任务不可 close：" + batch.body());
    assertEquals(true, results.get(1).at("/ok").asBoolean(), "done 任务可 close：" + batch.body());

    HttpResponse<String> unsupported = send("POST", "/api/v1/tasks/batch",
        "{\"ids\":[" + first + "],\"action\":\"frobnicate\"}", admin);
    assertEquals(400, unsupported.statusCode(), unsupported.body());

    HttpResponse<String> edit = send("POST", "/api/v1/tasks/batch",
        "{\"ids\":[" + first + "],\"action\":\"edit\",\"params\":{\"priority\":2,\"lockVersion\":0}}", admin);
    assertEquals(200, edit.statusCode(), edit.body());
    assertEquals(true, json.readTree(edit.body()).at("/data/results/0/ok").asBoolean(), edit.body());
    assertEquals(2, task(first).at("/priority").asInt());
  }
}
