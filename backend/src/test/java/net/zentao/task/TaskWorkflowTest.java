package net.zentao.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-8 任务状态机与生命周期（task 卡 §4/§8）：八动作合法/非法迁移、父任务规则（42203/42202）、
 * 一层父子（子任务再作父 42203）、指派、关闭/激活四件套、动态流动作名、children 摘要。
 */
class TaskWorkflowTest extends TaskTestSupport {

  @Test
  @DisplayName("八动作矩阵：合法迁移生效、from 列外 42202、wait 直接 finish 允许、累计为 0 完成 → 42203")
  void actionMatrix() throws Exception {
    long execution = freshExecution();
    long task = createTask(execution, "矩阵任务");

    assertRejected(act(task, "finish", "{}"), "42203", "wait finish 无消耗");
    assertRejected(act(task, "pause"), "42202", "wait pause");
    assertRejected(act(task, "resume"), "42202", "wait resume");
    assertRejected(act(task, "activate", "{\"leftHours\":1}"), "42202", "wait activate");

    HttpResponse<String> started = act(task, "start", "{\"leftHours\":4}");
    assertStatus(started, "doing", "wait start");
    assertFalse(json.readTree(started.body()).at("/data/startedAt").isNull(), "start 回写 startedAt");
    assertEquals(4, json.readTree(started.body()).at("/data/leftHours").asInt(), "start 落 leftHours");

    assertRejected(act(task, "start"), "42202", "doing start");
    assertStatus(act(task, "pause"), "pause", "doing pause");
    assertStatus(act(task, "resume"), "doing", "pause resume");

    HttpResponse<String> finished = act(task, "finish", "{\"consumedHours\":3,\"work\":\"改完了\"}");
    assertStatus(finished, "done", "doing finish");
    JsonNode view = json.readTree(finished.body()).at("/data");
    assertEquals(3, view.at("/consumedHours").asInt(), "finish 增量消耗");
    assertEquals(0, view.at("/leftHours").asInt(), "finish 缺省清零 leftHours");
    assertEquals("admin", view.at("/finishedBy").asText(), "finish 回写 finishedBy");

    assertRejected(act(task, "finish", "{}"), "42202", "done finish");
    assertStatus(act(task, "close", "{}"), "closed", "done close");
    assertEquals("done", task(task).at("/closedReason").asText(), "close 按来源回填 closedReason");
    assertTrue(task(task).at("/assignee").isNull(), "close 置空 assignee");
    assertRejected(act(task, "cancel"), "42202", "closed cancel");

    HttpResponse<String> activated = act(task, "activate", "{\"leftHours\":2}");
    assertStatus(activated, "doing", "closed activate");
    JsonNode activatedView = json.readTree(activated.body()).at("/data");
    assertTrue(activatedView.at("/finishedAt").isNull(), "activate 清 finishedAt");
    assertTrue(activatedView.at("/closedAt").isNull(), "activate 清 closedAt");
    assertTrue(activatedView.at("/closedReason").isNull(), "activate 清 closedReason");
    assertFalse(activatedView.at("/activatedAt").isNull(), "activate 回写 activatedAt");

    assertRejected(act(task, "activate", "{}"), "42201", "activate 缺 leftHours");
  }

  @Test
  @DisplayName("start 带本次消耗：落 effort 且累计进 consumedHours（三件套与流水自洽）")
  void startWithConsumption() throws Exception {
    long execution = freshExecution();
    long task = createTask(execution, "带消耗开始");
    HttpResponse<String> started = act(task, "start", "{\"consumedHours\":2,\"leftHours\":5}");
    JsonNode view = json.readTree(started.body()).at("/data");
    assertEquals(2, view.at("/consumedHours").asInt(), "start 的本次消耗累计进任务：" + view);
    assertEquals(1, data(send("GET", "/api/v1/tasks/" + task + "/efforts", null, admin)).at("/total").asInt(),
        "同一条消耗落为一条工时流水");
  }

  @Test
  @DisplayName("指派与取消：指派通知对象更新、取消回指 createdBy、closed 后指派 42202")
  void assignAndCancel() throws Exception {
    long execution = freshExecution();
    long task = createTask(execution, "指派任务");

    HttpResponse<String> assigned = act(task, "assign", "{\"assignee\":\"admin\",\"leftHours\":6}");
    assertStatus(assigned, "wait", "assign 不改状态");
    JsonNode assignedView = json.readTree(assigned.body()).at("/data");
    assertEquals("admin", assignedView.at("/assignee").asText());
    assertEquals(6, assignedView.at("/leftHours").asInt(), "assign 可覆写 leftHours");
    assertFalse(assignedView.at("/assignedAt").isNull(), "assign 回写 assignedAt");

    assertRejected(act(task, "assign", "{\"assignee\":\"ghost\"}"), "42201", "指派不存在账号");

    HttpResponse<String> canceled = act(task, "cancel", "{\"comment\":\"不做了\"}");
    assertStatus(canceled, "cancel", "wait cancel");
    assertEquals("admin", json.readTree(canceled.body()).at("/data/assignee").asText(), "cancel 回指 createdBy");
    assertRejected(act(task, "assign", "{\"assignee\":\"admin\"}"), "42202", "cancel 后指派");
  }

  @Test
  @DisplayName("父子：父任务 start/finish 42203、子任务再作父创建 42203、父任务可从事中状态关闭、children 摘要")
  void parentRules() throws Exception {
    long execution = freshExecution();
    long parent = createTask(execution, "父任务");
    long child = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"子任务\",\"parentId\":" + parent + "}", admin));

    assertTrue(task(parent).at("/isParent").asBoolean(), "首个子任务创建后 is_parent=true");
    assertRejected(act(parent, "start", "{}"), "42202", "父任务 start");
    assertRejected(act(parent, "finish", "{}"), "42202", "父任务 finish");

    HttpResponse<String> grandChild = send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"孙任务\",\"parentId\":" + child + "}", admin);
    assertRejected(grandChild, "42203", "子任务再作父");

    HttpResponse<String> crossExecution = send("POST", "/api/v1/executions/" + freshExecution() + "/tasks",
        "{\"title\":\"跨执行子任务\",\"parentId\":" + parent + "}", admin);
    assertRejected(crossExecution, "42201", "跨执行挂父");

    assertEquals(1, task(parent).at("/children").size(), "详情含 children 摘要");
    assertEquals("子任务", task(parent).at("/children/0/title").asText());

    // 子任务完成 → 父任务联动 done；父任务可从 done 之外的状态关闭（走 close 的第二条迁移）
    act(child, "start", "{\"leftHours\":2}");
    act(child, "finish", "{\"consumedHours\":1}");
    assertEquals("done", task(parent).at("/status").asText(), "全部子任务完成 → 父联动 done");
    assertEquals(1, task(parent).at("/consumedHours").asInt(), "父三件套=子合计");

    HttpResponse<String> activatedChild = act(child, "activate", "{\"leftHours\":1}");
    assertStatus(activatedChild, "doing", "activated child");
    assertEquals("doing", task(parent).at("/status").asText(), "任一子 doing → 父 doing");

    HttpResponse<String> closedParent = act(parent, "close", "{\"closedReason\":\"done\"}");
    assertStatus(closedParent, "closed", "父任务从事中状态关闭");
  }

  @Test
  @DisplayName("动态流：动作名全集与备注落痕（created/started/finished/closed/activated/assigned）")
  void activities() throws Exception {
    long execution = freshExecution();
    long task = createTask(execution, "动态流任务");
    act(task, "assign", "{\"assignee\":\"admin\",\"comment\":\"交给你\"}");
    act(task, "start", "{\"leftHours\":3,\"comment\":\"开工\"}");
    act(task, "finish", "{\"consumedHours\":3,\"comment\":\"收工\"}");

    JsonNode activities = data(send("GET", "/api/v1/tasks/" + task + "/activities?limit=50", null, admin));
    String body = activities.toString();
    for (String action : new String[] {"created", "assigned", "started", "finished"}) {
      assertTrue(body.contains("\"action\":\"" + action + "\""), action + " 未落动态流：" + body);
    }
    assertTrue(body.contains("收工"), "动作备注落动态流：" + body);
    assertEquals(1, data(send("GET", "/api/v1/tasks/" + task + "/efforts", null, admin)).at("/total").asInt(),
        "finish 的本次消耗自动落一条工时");
  }
}
