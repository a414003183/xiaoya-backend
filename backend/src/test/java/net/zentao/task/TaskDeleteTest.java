package net.zentao.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A-07 任务软删（task 卡 §5 DELETE）：父任务存在未删子任务 → 42203；删子任务后兄弟全删 →
 * 复位父 isParent=false（不产生动态流）；软删落动态流 deleted，删后详情与重复删 40401。
 */
class TaskDeleteTest extends TaskTestSupport {

  @org.springframework.beans.factory.annotation.Autowired
  org.springframework.jdbc.core.JdbcTemplate jdbc;

  private HttpResponse<String> delete(long taskId) throws Exception {
    return send("DELETE", "/api/v1/tasks/" + taskId, null, admin);
  }

  @Test
  @DisplayName("父任务守卫：存在未删子任务 → 42203；删净后父任务可删")
  void parentGuard() throws Exception {
    long execution = freshExecution();
    long parent = createTask(execution, "父任务");
    long child = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"子任务\",\"parentId\":" + parent + "}", admin));

    HttpResponse<String> blocked = delete(parent);
    assertEquals(422, blocked.statusCode(), blocked.body());
    assertTrue(blocked.body().contains("42203"), blocked.body());

    assertEquals(200, delete(child).statusCode());
    HttpResponse<String> ok = delete(parent);
    assertEquals(200, ok.statusCode(), ok.body());
  }

  @Test
  @DisplayName("isParent 复位：删最后一个子任务 → 父 isParent=false；删后详情 40401、重复删 40401")
  void isParentResetOnChildless() throws Exception {
    long execution = freshExecution();
    long parent = createTask(execution, "复位父");
    long child = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"独子\",\"parentId\":" + parent + "}", admin));
    assertTrue(task(parent).at("/isParent").asBoolean(), "首子创建 → isParent=true");

    HttpResponse<String> ok = delete(child);
    assertEquals(200, ok.statusCode(), ok.body());
    assertEquals(404, send("GET", "/api/v1/tasks/" + child, null, admin).statusCode(), "删后详情 404");
    assertFalse(task(parent).at("/isParent").asBoolean(), "全部子任务删除 → 复位 isParent=false");

    HttpResponse<String> activities = send("GET", "/api/v1/tasks/" + parent + "/activities", null, admin);
    assertFalse(activities.body().contains("\"deleted\""), "复位不产生动态流：" + activities.body());

    HttpResponse<String> again = delete(child);
    assertEquals(404, again.statusCode(), again.body());
    assertTrue(again.body().contains("40401"), again.body());
  }

  @Test
  @DisplayName("兄弟仍在：删其一父 isParent 保持 true，删净才复位")
  void siblingKeepsParent() throws Exception {
    long execution = freshExecution();
    long parent = createTask(execution, "多子父");
    long first = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"子一\",\"parentId\":" + parent + "}", admin));
    long second = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"子二\",\"parentId\":" + parent + "}", admin));

    assertEquals(200, delete(first).statusCode());
    assertTrue(task(parent).at("/isParent").asBoolean(), "仍有子任务 → isParent 保持 true");
    assertEquals(200, delete(second).statusCode());
    assertFalse(task(parent).at("/isParent").asBoolean(), "删净 → isParent 复位");
  }

  @Test
  @DisplayName("普通任务软删：落动态流 deleted，删后详情与重复删 40401")
  void plainTaskDelete() throws Exception {
    long execution = freshExecution();
    long task = createTask(execution, "普通任务");

    HttpResponse<String> ok = delete(task);
    assertEquals(200, ok.statusCode(), ok.body());

    HttpResponse<String> gone = send("GET", "/api/v1/tasks/" + task, null, admin);
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());

    // 软删落动态流 deleted：对象已删走不了 HTTP 时间线（删后详情 40401 是正确语义），直查 activity 表
    Integer deletedRows = jdbc.queryForObject(
        "SELECT COUNT(*) FROM activity WHERE object_type = 'task' AND object_id = ? AND action = 'deleted'",
        Integer.class, task);
    assertEquals(1, deletedRows, "软删落动态流 deleted");

    HttpResponse<String> again = delete(task);
    assertEquals(404, again.statusCode(), again.body());
    assertTrue(again.body().contains("40401"), again.body());
  }
}
