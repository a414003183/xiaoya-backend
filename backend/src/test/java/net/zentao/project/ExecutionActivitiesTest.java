package net.zentao.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * B-PRJ-07 执行动态流读取口（project 卡 §5 GET /executions/{id}/activities）：
 * 与 project 域既有 activities 端点同范式（platform ActivityQueryService，游标 limit+beforeId）；
 * start 执行产生的动态可见；执行不存在 → 40401。
 */
class ExecutionActivitiesTest extends net.zentao.ApiTestSupport {

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  @Test
  @DisplayName("执行动态流：start 后 items 含 started 动作、hasMore=false；不存在执行 40401")
  void executionActivities() throws Exception {
    long product = createProduct(admin, "动态流产品");
    long project = createProject(admin, "动态流项目", product, null);
    long execution = createExecution(admin, project, "动态流执行");
    assertEquals(200, send("POST", "/api/v1/executions/" + execution + "/start", "{}", admin).statusCode());

    JsonNode activities = data(send("GET", "/api/v1/executions/" + execution + "/activities?limit=10", null, admin));
    assertTrue(activities.at("/items").size() >= 1, activities.toString());
    boolean hasStarted = false;
    for (JsonNode item : activities.at("/items")) {
      if ("started".equals(item.at("/action").asText())) {
        hasStarted = true;
        assertEquals("execution", item.at("/objectType").asText(), item.toString());
        assertEquals(execution, item.at("/objectId").asLong(), item.toString());
      }
    }
    assertTrue(hasStarted, "start 动作入流：" + activities);
    assertTrue(!activities.at("/hasMore").asBoolean(), activities.toString());

    var missing = send("GET", "/api/v1/executions/99999999/activities", null, admin);
    assertEquals(404, missing.statusCode(), missing.body());
    assertTrue(missing.body().contains("40401"), missing.body());
  }
}
