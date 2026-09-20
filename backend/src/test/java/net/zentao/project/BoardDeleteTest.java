package net.zentao.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A-07 看板族软删 + B-PRJ-13 取消归档（project 卡 §5）：
 * 卡片删除后详情 40401、重复删 40401；看板有卡 → 42203、空板可删（列不拦截）；
 * 空间有板 → 42203、板清空后可删；unarchive 把 archived 置回 false 且不动 status。
 */
class BoardDeleteTest extends net.zentao.ApiTestSupport {

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  @Test
  @DisplayName("卡片软删：删除后详情 40401、重复删除 40401；归档可逆：unarchive 置回 archived=false 不动 status")
  void cardDeleteAndUnarchive() throws Exception {
    long board = createBoard("卡片删除看板");
    long lane = createLane(board);
    long card = dataId(send("POST", "/api/v1/boards/" + board + "/cards",
        "{\"laneId\":" + lane + ",\"name\":\"删除卡片\",\"status\":\"done\"}", admin));

    JsonNode archived = data(send("POST", "/api/v1/cards/" + card + "/archive", null, admin));
    assertTrue(archived.at("/archived").asBoolean(), archived.toString());

    JsonNode unarchived = data(send("POST", "/api/v1/cards/" + card + "/unarchive",
        "{\"comment\":\"取消归档\"}", admin));
    assertFalse(unarchived.at("/archived").asBoolean(), unarchived.toString());
    assertEquals("done", unarchived.at("/status").asText(), "取消归档不改 status");
    assertEquals(200,
        send("POST", "/api/v1/cards/" + card + "/unarchive", null, admin).statusCode(), "未归档卡再取消归档幂等");

    assertEquals(200, send("DELETE", "/api/v1/cards/" + card, null, admin).statusCode());
    HttpResponse<String> gone = send("GET", "/api/v1/cards/" + card, null, admin);
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());
    HttpResponse<String> again = send("DELETE", "/api/v1/cards/" + card, null, admin);
    assertEquals(404, again.statusCode(), again.body());
    assertTrue(again.body().contains("40401"), again.body());
  }

  @Test
  @DisplayName("看板软删：有卡 → 42203；空板（仅列）→ 删除成功且详情 40401")
  void boardDeleteGuard() throws Exception {
    long space = createSpace("看板删除空间");
    long occupied = dataId(send("POST", "/api/v1/board-spaces/" + space + "/boards",
        "{\"name\":\"有卡看板\",\"acl\":\"extend\"}", admin));
    long lane = createLane(occupied);
    send("POST", "/api/v1/boards/" + occupied + "/cards",
        "{\"laneId\":" + lane + ",\"name\":\"占位卡\"}", admin);

    HttpResponse<String> blocked = send("DELETE", "/api/v1/boards/" + occupied, null, admin);
    assertEquals(422, blocked.statusCode(), blocked.body());
    assertTrue(blocked.body().contains("42203"), blocked.body());

    long empty = dataId(send("POST", "/api/v1/board-spaces/" + space + "/boards",
        "{\"name\":\"空看板\",\"acl\":\"extend\"}", admin));
    createLane(empty);
    assertEquals(200, send("DELETE", "/api/v1/boards/" + empty, null, admin).statusCode());
    HttpResponse<String> gone = send("GET", "/api/v1/boards/" + empty, null, admin);
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());
  }

  @Test
  @DisplayName("空间软删：有看板 → 42203；看板清空后 → 删除成功且详情 40401")
  void boardSpaceDeleteGuard() throws Exception {
    long space = createSpace("空间删除空间");
    long board = dataId(send("POST", "/api/v1/board-spaces/" + space + "/boards",
        "{\"name\":\"挡删看板\",\"acl\":\"extend\"}", admin));

    HttpResponse<String> blocked = send("DELETE", "/api/v1/board-spaces/" + space, null, admin);
    assertEquals(422, blocked.statusCode(), blocked.body());
    assertTrue(blocked.body().contains("42203"), blocked.body());

    assertEquals(200, send("DELETE", "/api/v1/boards/" + board, null, admin).statusCode(), "先清看板");
    assertEquals(200, send("DELETE", "/api/v1/board-spaces/" + space, null, admin).statusCode());
    HttpResponse<String> gone = send("GET", "/api/v1/board-spaces/" + space, null, admin);
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());
  }

  private long createSpace(String name) throws Exception {
    return dataId(send("POST", "/api/v1/board-spaces",
        "{\"name\":\"" + name + "\",\"type\":\"cooperation\",\"acl\":\"open\"}", admin));
  }

  private long createBoard(String name) throws Exception {
    return dataId(send("POST", "/api/v1/board-spaces/" + createSpace(name + "空间") + "/boards",
        "{\"name\":\"" + name + "\",\"acl\":\"extend\"}", admin));
  }

  private long createLane(long boardId) throws Exception {
    return dataId(send("POST", "/api/v1/boards/" + boardId + "/lanes",
        "{\"name\":\"列\",\"wipLimit\":-1,\"sort\":1}", admin));
  }
}
