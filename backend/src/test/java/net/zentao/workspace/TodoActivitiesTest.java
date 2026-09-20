package net.zentao.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * B-WKS-05 待办动态流读取口（workspace 卡 §5 GET /todos/{id}/activities）：
 * 与 story/task 域 activities 端点同范式（platform ActivityQueryService，游标 limit+beforeId）；
 * start 产生的动态可见且 object_type=todo；待办不存在 → 40401。
 */
class TodoActivitiesTest extends ApiTestSupport {

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  @Test
  @DisplayName("待办动态流：start 后 items 含 started、hasMore=false；不存在待办 40401")
  void todoActivities() throws Exception {
    long todo = dataId(send("POST", "/api/v1/todos", "{\"title\":\"动态流待办\"}", admin));
    assertEquals(200, send("POST", "/api/v1/todos/" + todo + "/start", null, admin).statusCode());

    JsonNode activities = data(send("GET", "/api/v1/todos/" + todo + "/activities?limit=10", null, admin));
    assertTrue(activities.at("/items").size() >= 1, activities.toString());
    boolean hasStarted = false;
    for (JsonNode item : activities.at("/items")) {
      if ("started".equals(item.at("/action").asText())) {
        hasStarted = true;
        assertEquals("todo", item.at("/objectType").asText(), item.toString());
        assertEquals(todo, item.at("/objectId").asLong(), item.toString());
      }
    }
    assertTrue(hasStarted, "start 动作入流：" + activities);
    assertTrue(!activities.at("/hasMore").asBoolean(), activities.toString());

    var missing = send("GET", "/api/v1/todos/99999999/activities", null, admin);
    assertEquals(404, missing.statusCode(), missing.body());
    assertTrue(missing.body().contains("40401"), missing.body());
  }
}
