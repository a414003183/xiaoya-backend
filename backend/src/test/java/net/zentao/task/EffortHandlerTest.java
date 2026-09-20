package net.zentao.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-9 工时端点与联动（task 卡 §3b/§4/§8）：登记累计与覆写、leftHours=0 自动完成、wait 自动 doing、
 * closed/cancel 拒绝 42202、workDate 晚于今天 42201、编辑/删除回算与状态不回退、非本人 40301、父子三件套联动。
 */
class EffortHandlerTest extends TaskTestSupport {

  private long recordEffort(long taskId, String body) throws Exception {
    return dataId(send("POST", "/api/v1/tasks/" + taskId + "/efforts", body, admin));
  }

  @Test
  @DisplayName("登记：consumedHours 累计、leftHours 覆写与缺省不动、wait 自动 doing、动态流 effortRecorded 先于 started")
  void recordRules() throws Exception {
    long execution = freshExecution();
    long task = createTask(execution, "工时任务");
    send("PATCH", "/api/v1/tasks/" + task, "{\"estimateHours\":10,\"lockVersion\":0}", admin);

    recordEffort(task, "{\"consumedHours\":2,\"work\":\"第一天\"}");
    JsonNode afterFirst = task(task);
    assertEquals(2, afterFirst.at("/consumedHours").asInt(), afterFirst.toString());
    assertEquals("wait", afterFirst.at("/status").asText(), "未传 leftHours 不自动 doing");
    assertTrue(afterFirst.at("/leftHours").isNull(), "leftHours 缺省不覆写");

    recordEffort(task, "{\"consumedHours\":1,\"leftHours\":4}");
    JsonNode afterSecond = task(task);
    assertEquals(3, afterSecond.at("/consumedHours").asInt(), "增量累计");
    assertEquals(4, afterSecond.at("/leftHours").asInt(), "leftHours 覆写");
    assertEquals("doing", afterSecond.at("/status").asText(), "wait + 剩余 → 自动 doing");
    assertFalse(afterSecond.at("/startedAt").isNull(), "自动 doing 回写 startedAt");

    JsonNode activities = data(send("GET", "/api/v1/tasks/" + task + "/activities?limit=50", null, admin));
    int effortIndex = -1;
    int startedIndex = -1;
    for (int i = 0; i < activities.at("/items").size(); i++) {
      String action = activities.at("/items/" + i + "/action").asText();
      if ("started".equals(action) && startedIndex < 0) {
        startedIndex = i;
      }
      if ("effortRecorded".equals(action) && effortIndex < 0) {
        effortIndex = i;
      }
    }
    assertTrue(startedIndex >= 0 && effortIndex >= 0, "动态流含 started 与 effortRecorded：" + activities);
    assertTrue(startedIndex < effortIndex, "倒序时间线：started 更新（idx 更小）= 先落 effort 后落 started：" + activities);
  }

  @Test
  @DisplayName("leftHours=0 自动完成：finishedBy/At 回写、动态流 finished；已关闭任务登记 42202")
  void autoFinishOnZeroLeft() throws Exception {
    long execution = freshExecution();
    long task = createTask(execution, "自动完成任务");
    act(task, "start", "{\"leftHours\":3}");

    recordEffort(task, "{\"consumedHours\":3,\"leftHours\":0}");
    JsonNode finished = task(task);
    assertEquals("done", finished.at("/status").asText(), finished.toString());
    assertFalse(finished.at("/finishedAt").isNull(), "自动完成回写 finishedAt");
    assertEquals("admin", finished.at("/finishedBy").asText(), "自动完成回写 finishedBy");
    assertEquals(3, finished.at("/consumedHours").asInt(), finished.toString());

    HttpResponse<String> rejected = send("POST", "/api/v1/tasks/" + task + "/efforts",
        "{\"consumedHours\":1}", admin);
    assertEquals(200, rejected.statusCode(), rejected.body());

    act(task, "close", "{}");
    HttpResponse<String> onClosed = send("POST", "/api/v1/tasks/" + task + "/efforts", "{\"consumedHours\":1}", admin);
    assertRejected(onClosed, "42202", "closed 任务登记工时");
  }

  @Test
  @DisplayName("编辑/删除工时回算：consumedHours=未删合计、leftHours 取最近覆写、状态不回退、非本人 40301")
  void editAndDelete() throws Exception {
    long execution = freshExecution();
    long task = createTask(execution, "回算任务");
    act(task, "start", "{\"leftHours\":5}");
    long first = recordEffort(task, "{\"consumedHours\":2,\"leftHours\":5}");
    long second = recordEffort(task, "{\"consumedHours\":3,\"leftHours\":2}");
    assertEquals(5, task(task).at("/consumedHours").asInt(), "累计 5");
    assertEquals(2, task(task).at("/leftHours").asInt(), "最近覆写值 2");

    HttpResponse<String> edited = send("PATCH", "/api/v1/efforts/" + second,
        "{\"consumedHours\":4,\"leftHours\":1}", admin);
    assertEquals(200, edited.statusCode(), edited.body());
    assertEquals(6, task(task).at("/consumedHours").asInt(), "编辑后回算 2+4=6");
    assertEquals(1, task(task).at("/leftHours").asInt(), "编辑后 leftHours 取覆写 1");

    HttpResponse<String> deleted = send("DELETE", "/api/v1/efforts/" + first, null, admin);
    assertEquals(200, deleted.statusCode(), deleted.body());
    JsonNode afterDelete = task(task);
    assertEquals(4, afterDelete.at("/consumedHours").asInt(), "删除后回算 4");
    assertEquals("doing", afterDelete.at("/status").asText(), "状态不回退");

    HttpResponse<String> future = send("POST", "/api/v1/tasks/" + task + "/efforts",
        "{\"consumedHours\":1,\"workDate\":\"2099-01-01\"}", admin);
    assertRejected(future, "42201", "未来日期登记");

    HttpResponse<String> missing = send("POST", "/api/v1/tasks/" + task + "/efforts", "{}", admin);
    assertRejected(missing, "42201", "缺 consumedHours");
  }

  @Test
  @DisplayName("父子联动：子任务登记工时 → 父三件套=子合计（cancel 子排除）")
  void parentRollupOnEffort() throws Exception {
    long execution = freshExecution();
    long parent = createTask(execution, "工时父任务");
    long childA = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"工时子甲\",\"parentId\":" + parent + "}", admin));
    long childB = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"工时子乙\",\"parentId\":" + parent + "}", admin));

    HttpResponse<String> parentEffort = send("POST", "/api/v1/tasks/" + parent + "/efforts",
        "{\"consumedHours\":1}", admin);
    assertRejected(parentEffort, "42202", "父任务登记工时");

    recordEffort(childA, "{\"consumedHours\":2,\"leftHours\":3}");
    recordEffort(childB, "{\"consumedHours\":1,\"leftHours\":1}");
    JsonNode rolled = task(parent);
    assertEquals(3, rolled.at("/consumedHours").asInt(), rolled.toString());
    assertEquals(4, rolled.at("/leftHours").asInt(), rolled.toString());
    assertEquals("doing", rolled.at("/status").asText(), rolled.toString());

    act(childB, "cancel");
    JsonNode afterCancel = task(parent);
    assertEquals(2, afterCancel.at("/consumedHours").asInt(), "cancel 子排除合计：" + afterCancel);
    assertEquals(3, afterCancel.at("/leftHours").asInt(), afterCancel.toString());
  }
}
