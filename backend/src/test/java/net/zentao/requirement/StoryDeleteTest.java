package net.zentao.requirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A-07 需求软删（requirement 卡 §5 DELETE）：任务引用/子需求引用 → 42203；
 * 删净守卫后可删，软删 + 动态流 deleted，删后详情与重复删 40401。
 */
class StoryDeleteTest extends net.zentao.ApiTestSupport {

  private String admin;
  private long productId;

  @BeforeEach
  void prepare() throws Exception {
    admin = login("admin", "admin123");
    productId = dataId(send("POST", "/api/v1/products",
        "{\"name\":\"需求删除产品-" + System.nanoTime() + "\"}", admin));
  }

  private long createStory(String extraJson) throws Exception {
    return dataId(send("POST", "/api/v1/products/" + productId + "/stories",
        "{\"title\":\"删除需求\"" + (extraJson == null ? "" : "," + extraJson) + "}", admin));
  }

  @Test
  @DisplayName("守卫：存在未删任务引用 storyId → 42203；删任务后可删，落动态流 deleted，重复删 40401")
  void taskGuardAndDelete() throws Exception {
    long story = createStory(null);
    long execution = createExecution(admin,
        dataId(send("POST", "/api/v1/projects",
            "{\"name\":\"需求删除项目-" + System.nanoTime() + "\",\"beginDate\":\"2026-09-01\","
                + "\"endDate\":\"2026-12-31\",\"productIds\":[" + productId + "]}",
            admin)),
        "需求删除执行");
    long task = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"引用需求任务\",\"storyId\":" + story + "}", admin));

    HttpResponse<String> blocked = send("DELETE", "/api/v1/stories/" + story, null, admin);
    assertEquals(422, blocked.statusCode(), blocked.body());
    assertTrue(blocked.body().contains("42203"), blocked.body());

    assertEquals(200, send("DELETE", "/api/v1/tasks/" + task, null, admin).statusCode());
    HttpResponse<String> ok = send("DELETE", "/api/v1/stories/" + story, null, admin);
    assertEquals(200, ok.statusCode(), ok.body());

    HttpResponse<String> gone = send("GET", "/api/v1/stories/" + story, null, admin);
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());

    HttpResponse<String> activities = send("GET", "/api/v1/stories/" + story + "/activities", null, admin);
    assertTrue(activities.body().contains("\"deleted\""), "软删落动态流 deleted：" + activities.body());

    HttpResponse<String> again = send("DELETE", "/api/v1/stories/" + story, null, admin);
    assertEquals(404, again.statusCode(), again.body());
    assertTrue(again.body().contains("40401"), again.body());
  }

  @Test
  @DisplayName("守卫：存在未删子需求 parentId 指向 → 42203；先删子再删父成功")
  void childGuardAndDelete() throws Exception {
    long epic = createStory("\"type\":\"epic\"");
    long child = createStory("\"parentId\":" + epic);

    HttpResponse<String> blocked = send("DELETE", "/api/v1/stories/" + epic, null, admin);
    assertEquals(422, blocked.statusCode(), blocked.body());
    assertTrue(blocked.body().contains("42203"), blocked.body());

    assertEquals(200, send("DELETE", "/api/v1/stories/" + child, null, admin).statusCode());
    HttpResponse<String> ok = send("DELETE", "/api/v1/stories/" + epic, null, admin);
    assertEquals(200, ok.statusCode(), ok.body());
  }

  @Test
  @DisplayName("任务守卫只认未删任务：软删任务后需求立即可删")
  void taskGuardCountsActiveOnly() throws Exception {
    long story = createStory(null);
    long execution = createExecution(admin,
        dataId(send("POST", "/api/v1/projects",
            "{\"name\":\"已删任务项目-" + System.nanoTime() + "\",\"beginDate\":\"2026-09-01\","
                + "\"endDate\":\"2026-12-31\",\"productIds\":[" + productId + "]}",
            admin)),
        "已删任务执行");
    long task = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"先删的任务\",\"storyId\":" + story + "}", admin));

    assertEquals(200, send("DELETE", "/api/v1/tasks/" + task, null, admin).statusCode());
    HttpResponse<String> ok = send("DELETE", "/api/v1/stories/" + story, null, admin);
    assertEquals(200, ok.statusCode(), ok.body());
  }
}
