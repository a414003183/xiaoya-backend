package net.zentao.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-4 执行需求看板（project 卡 §5/§8）：整板按 story 状态分组（列 = story.yml 状态集，空列保留）、
 * 拖拽落列委托 story 域动作（守卫以 story 状态机为准：非法迁移 42202、守卫未满足 42203 原样透传）、
 * 非本执行关联需求 40401、列 key 非法 42201。
 */
class ExecutionKanbanHandlerTest extends ApiTestSupport {

  private static final String[] LANE_KEYS = {"draft", "reviewing", "active", "changing", "changed", "closed"};

  private String admin;
  private long product;
  private long project;
  private long execution;

  @BeforeEach
  void setUp() throws Exception {
    admin = login("admin", "admin123");
    product = createProduct(admin, "看板产品");
    project = createProject(admin, "看板项目", product, null);
    execution = createExecution(admin, project, "看板执行");
  }

  /** 契约只开放项目级关联写端点，执行需求集随所属项目承接。 */
  private void link(long... storyIds) throws Exception {
    StringBuilder ids = new StringBuilder();
    for (long storyId : storyIds) {
      ids.append(ids.isEmpty() ? "" : ",").append(storyId);
    }
    data(send("POST", "/api/v1/projects/" + project + "/stories", "{\"storyIds\":[" + ids + "]}", admin));
  }

  private JsonNode lane(JsonNode board, String key) {
    for (JsonNode item : board.at("/lanes")) {
      if (key.equals(item.at("/key").asText())) {
        return item;
      }
    }
    throw new AssertionError("看板没有列：" + key + " → " + board);
  }

  @Test
  @DisplayName("整板：列 = story 状态集且空列保留，关联需求按 status 落列")
  void boardGroupsByStoryStatus() throws Exception {
    long draft = createStory(admin, product, "看板草稿", null);
    long reviewing = createStory(admin, product, "看板评审", "\"reviewers\":[\"admin\"]");
    long active = createStory(admin, product, "看板激活", "\"reviewers\":[\"admin\"]");
    data(send("POST", "/api/v1/stories/" + reviewing + "/submit-review", "{}", admin));
    data(send("POST", "/api/v1/stories/" + active + "/submit-review", "{}", admin));
    data(send("POST", "/api/v1/stories/" + active + "/pass", "{}", admin));
    link(draft, reviewing, active);

    JsonNode board = data(send("GET", "/api/v1/executions/" + execution + "/kanban", null, admin));
    assertEquals(LANE_KEYS.length, board.at("/lanes").size(), board.toString());
    for (int index = 0; index < LANE_KEYS.length; index++) {
      assertEquals(LANE_KEYS[index], board.at("/lanes/" + index + "/key").asText(), board.toString());
    }
    assertEquals(1, lane(board, "draft").at("/items").size(), board.toString());
    assertEquals(draft, lane(board, "draft").at("/items/0/id").asLong(), board.toString());
    assertEquals("看板评审", lane(board, "reviewing").at("/items/0/title").asText(), board.toString());
    assertEquals(active, lane(board, "active").at("/items/0/id").asLong(), board.toString());
    assertEquals(0, lane(board, "changing").at("/items").size(), board.toString());

    // 未关联的需求不入板
    createStory(admin, product, "看板未关联", null);
    JsonNode again = data(send("GET", "/api/v1/executions/" + execution + "/kanban", null, admin));
    assertEquals(1, lane(again, "draft").at("/items").size(), again.toString());
  }

  @Test
  @DisplayName("拖拽：active→changing 落库改状态、同列重拖幂等、非法迁移 42202、守卫未满足 42203 透传")
  void moveDelegatesToStoryActions() throws Exception {
    long story = createStory(admin, product, "看板拖拽需求", "\"reviewers\":[\"admin\"]");
    long draft = createStory(admin, product, "看板守卫需求", null);
    link(story, draft);
    String url = "/api/v1/executions/" + execution + "/kanban/cards/";

    data(send("POST", "/api/v1/stories/" + story + "/submit-review", "{}", admin));
    data(send("POST", "/api/v1/stories/" + story + "/pass", "{}", admin));

    // active → changing：change 动作
    JsonNode moved = data(send("POST", url + story + "/move", "{\"column\":\"changing\",\"comment\":\"拖拽\"}", admin));
    assertEquals("changing", moved.at("/status").asText(), moved.toString());
    assertEquals("changing", data(send("GET", "/api/v1/stories/" + story, null, admin)).at("/status").asText());

    // changing → active 无迁移 → 42202 透传
    HttpResponse<String> illegal = send("POST", url + story + "/move", "{\"column\":\"active\"}", admin);
    assertEquals(422, illegal.statusCode(), illegal.body());
    assertTrue(illegal.body().contains("42202"), illegal.body());

    // 同列重拖：无动作可派，幂等返回当前卡片
    JsonNode same = data(send("POST", url + story + "/move", "{\"column\":\"changing\"}", admin));
    assertEquals("changing", same.at("/status").asText(), same.toString());

    // draft → reviewing：submit-review 无评审人 → story 域守卫 42203 原样透传
    HttpResponse<String> guard = send("POST", url + draft + "/move", "{\"column\":\"reviewing\"}", admin);
    assertEquals(422, guard.statusCode(), guard.body());
    assertTrue(guard.body().contains("42203"), guard.body());
  }

  @Test
  @DisplayName("守卫：非本执行关联需求 40401、列 key 非法 42201、执行不存在 40401")
  void moveGuards() throws Exception {
    long linked = createStory(admin, product, "看板关联需求", null);
    long unlinked = createStory(admin, product, "看板未关联需求", null);
    link(linked);
    String url = "/api/v1/executions/" + execution + "/kanban/cards/";

    HttpResponse<String> notLinked = send("POST", url + unlinked + "/move", "{\"column\":\"active\"}", admin);
    assertEquals(404, notLinked.statusCode(), notLinked.body());
    assertTrue(notLinked.body().contains("40401"), notLinked.body());

    HttpResponse<String> badColumn = send("POST", url + linked + "/move", "{\"column\":\"ghost\"}", admin);
    assertEquals(422, badColumn.statusCode(), badColumn.body());
    assertTrue(badColumn.body().contains("column"), badColumn.body());

    HttpResponse<String> ghostExecution = send("POST", "/api/v1/executions/999999/kanban/cards/" + linked + "/move",
        "{\"column\":\"active\"}", admin);
    assertEquals(404, ghostExecution.statusCode(), ghostExecution.body());
    assertFalse(ghostExecution.body().contains("42202"), ghostExecution.body());
  }
}
