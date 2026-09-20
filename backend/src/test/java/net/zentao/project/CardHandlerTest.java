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
 * T-6 通用看板卡片（project 卡 §3.6/§5/§8）：拖拽 move 的 laneId+sort 改写、目标 lane 不属本看板 42201、
 * wipLimit 超限 42203、删列有卡 42203、归档不改 status、整板一次下发、PATCH 乐观锁 40901。
 */
class CardHandlerTest extends net.zentao.ApiTestSupport {

  private String adminCookie;

  @BeforeEach
  void login() throws Exception {
    adminCookie = login("admin", "admin123");
  }

  private long createSpace(String name) throws Exception {
    return dataId(send("POST", "/api/v1/board-spaces",
        "{\"name\":\"" + name + "\",\"type\":\"cooperation\",\"acl\":\"open\"}", adminCookie));
  }

  private long createBoard(long spaceId, String name) throws Exception {
    return dataId(send("POST", "/api/v1/board-spaces/" + spaceId + "/boards",
        "{\"name\":\"" + name + "\",\"acl\":\"extend\"}", adminCookie));
  }

  private long createLane(long boardId, String name, int wipLimit, int sort) throws Exception {
    return dataId(send("POST", "/api/v1/boards/" + boardId + "/lanes",
        "{\"name\":\"" + name + "\",\"wipLimit\":" + wipLimit + ",\"sort\":" + sort + "}", adminCookie));
  }

  private long createCard(long boardId, long laneId, String name) throws Exception {
    return dataId(send("POST", "/api/v1/boards/" + boardId + "/cards",
        "{\"laneId\":" + laneId + ",\"name\":\"" + name + "\"}", adminCookie));
  }

  @Test
  @DisplayName("拖拽矩阵：move 改 laneId+sort、跨看板 lane → 42201、归档不改 status、PATCH 乐观锁 40901")
  void moveMatrix() throws Exception {
    long space = createSpace("拖拽空间");
    long board = createBoard(space, "拖拽看板");
    long laneTodo = createLane(board, "待办", -1, 1);
    long laneDoing = createLane(board, "进行中", -1, 2);
    long card = createCard(board, laneTodo, "卡片甲");

    JsonNode moved = data(send("POST", "/api/v1/cards/" + card + "/move",
        "{\"laneId\":" + laneDoing + ",\"sort\":7}", adminCookie));
    assertEquals(laneDoing, moved.at("/laneId").asLong(), moved.toString());
    assertEquals(7, moved.at("/sort").asInt(), moved.toString());

    long otherBoard = createBoard(space, "另一个看板");
    long otherLane = createLane(otherBoard, "别家列", -1, 1);
    HttpResponse<String> wrongLane = send("POST", "/api/v1/cards/" + card + "/move",
        "{\"laneId\":" + otherLane + ",\"sort\":1}", adminCookie);
    assertEquals(422, wrongLane.statusCode(), wrongLane.body());
    assertTrue(wrongLane.body().contains("laneId"), wrongLane.body());

    JsonNode archived = data(send("POST", "/api/v1/cards/" + card + "/archive", null, adminCookie));
    assertTrue(archived.at("/archived").asBoolean(), archived.toString());
    assertEquals("doing", archived.at("/status").asText(), archived.toString());
    assertEquals(laneDoing, archived.at("/laneId").asLong(), archived.toString());

    JsonNode whole = data(send("GET", "/api/v1/boards/" + board, null, adminCookie));
    assertEquals(2, whole.at("/lanes").size(), whole.toString());
    assertEquals(laneTodo, whole.at("/lanes/0/id").asLong(), whole.toString());
    assertEquals(laneDoing, whole.at("/lanes/1/id").asLong(), whole.toString());
    assertEquals(1, whole.at("/cards").size(), whole.toString());
    assertEquals(laneDoing, whole.at("/cards/0/laneId").asLong(), whole.toString());
    assertEquals(7, whole.at("/cards/0/sort").asInt(), whole.toString());

    JsonNode cards = data(send("GET", "/api/v1/boards/" + board + "/cards", null, adminCookie));
    assertEquals(1, cards.at("/total").asLong(), cards.toString());
    assertEquals("卡片甲", cards.at("/items/0/name").asText(), cards.toString());

    HttpResponse<String> stale = send("PATCH", "/api/v1/cards/" + card,
        "{\"name\":\"改名\",\"lockVersion\":99}", adminCookie);
    assertEquals(409, stale.statusCode(), stale.body());
    assertTrue(stale.body().contains("40901"), stale.body());

    int version = archived.at("/lockVersion").asInt();
    JsonNode patched = data(send("PATCH", "/api/v1/cards/" + card,
        "{\"name\":\"卡片甲改\",\"progress\":30,\"status\":\"done\",\"lockVersion\":" + version + "}", adminCookie));
    assertEquals("卡片甲改", patched.at("/name").asText(), patched.toString());
    assertEquals("done", patched.at("/status").asText(), patched.toString());
    assertEquals(30, patched.at("/progress").asInt(), patched.toString());
  }

  @Test
  @DisplayName("wipLimit：目标列已满 → 42203；同列内重拖不把自身计入 WIP")
  void wipLimitGuard() throws Exception {
    long space = createSpace("限流空间");
    long board = createBoard(space, "限流看板");
    long free = createLane(board, "自由列", -1, 1);
    long limited = createLane(board, "限量列", 1, 2);
    long cardA = createCard(board, free, "卡A");
    long cardB = createCard(board, free, "卡B");

    JsonNode first = data(send("POST", "/api/v1/cards/" + cardA + "/move",
        "{\"laneId\":" + limited + ",\"sort\":1}", adminCookie));
    assertEquals(limited, first.at("/laneId").asLong(), first.toString());

    HttpResponse<String> full = send("POST", "/api/v1/cards/" + cardB + "/move",
        "{\"laneId\":" + limited + ",\"sort\":2}", adminCookie);
    assertEquals(422, full.statusCode(), full.body());
    assertTrue(full.body().contains("42203"), full.body());

    JsonNode again = data(send("POST", "/api/v1/cards/" + cardA + "/move",
        "{\"laneId\":" + limited + ",\"sort\":5}", adminCookie));
    assertEquals(5, again.at("/sort").asInt(), again.toString());
    assertEquals(limited, again.at("/laneId").asLong(), again.toString());

    HttpResponse<String> ghost = send("POST", "/api/v1/cards/" + cardB + "/move",
        "{\"laneId\":999999,\"sort\":1}", adminCookie);
    assertEquals(422, ghost.statusCode(), ghost.body());
    assertTrue(ghost.body().contains("laneId"), ghost.body());
  }

  @Test
  @DisplayName("删列：列内有卡 → 42203，空列软删后整板不再出现；错挂列 40401")
  void deleteLaneGuard() throws Exception {
    long space = createSpace("删列空间");
    long board = createBoard(space, "删列看板");
    long occupied = createLane(board, "有卡列", -1, 1);
    long empty = createLane(board, "空列", -1, 2);
    createCard(board, occupied, "占位列卡");

    HttpResponse<String> blocked = send("DELETE", "/api/v1/boards/" + board + "/lanes/" + occupied, null, adminCookie);
    assertEquals(422, blocked.statusCode(), blocked.body());
    assertTrue(blocked.body().contains("42203"), blocked.body());

    assertEquals(200, send("DELETE", "/api/v1/boards/" + board + "/lanes/" + empty, null, adminCookie).statusCode());
    JsonNode whole = data(send("GET", "/api/v1/boards/" + board, null, adminCookie));
    assertEquals(1, whole.at("/lanes").size(), whole.toString());
    assertEquals(occupied, whole.at("/lanes/0/id").asLong(), whole.toString());

    long otherBoard = createBoard(space, "别家看板");
    long otherLane = createLane(otherBoard, "别家列", -1, 1);
    assertEquals(404,
        send("DELETE", "/api/v1/boards/" + board + "/lanes/" + otherLane, null, adminCookie).statusCode());
  }

  @Test
  @DisplayName("卡片列表 DSL：filters[archived] 1/0、filters[laneId] 命中，白名单外字段 40001")
  void cardListDsl() throws Exception {
    long space = createSpace("卡片列表空间");
    long board = createBoard(space, "卡片列表看板");
    long lane = createLane(board, "列表列", -1, 1);
    long card = createCard(board, lane, "归档卡");
    createCard(board, lane, "在列卡");

    assertEquals(0, cardList(board, "filters%5Barchived%5D=1").at("/total").asLong());
    assertEquals(2, cardList(board, "filters%5Barchived%5D=0").at("/total").asLong());
    assertEquals(2, cardList(board, "filters%5BlaneId%5D=" + lane).at("/total").asLong());
    assertEquals(1, cardList(board, "q=归档").at("/total").asLong());

    data(send("POST", "/api/v1/cards/" + card + "/archive", null, adminCookie));
    assertEquals(1, cardList(board, "filters%5Barchived%5D=0").at("/total").asLong());
    assertEquals(1, cardList(board, "filters%5Barchived%5D=1").at("/total").asLong());

    HttpResponse<String> unregistered =
        send("GET", "/api/v1/boards/" + board + "/cards?filters%5Bghost%5D=1", null, adminCookie);
    assertEquals(400, unregistered.statusCode(), unregistered.body());
    assertTrue(unregistered.body().contains("40001"), unregistered.body());
  }

  @Test
  @DisplayName("空间/看板二态机：close 落 closedBy/closedAt，重复 close → 42202，activate 清空回 active")
  void spaceAndBoardTwoState() throws Exception {
    long space = createSpace("二态空间");
    long board = createBoard(space, "二态看板");

    JsonNode closed = data(send("POST", "/api/v1/boards/" + board + "/close", "{\"comment\":\"归档\"}", adminCookie));
    assertEquals("closed", closed.at("/status").asText(), closed.toString());
    assertEquals("admin", closed.at("/closedBy").asText(), closed.toString());
    assertTrue(closed.hasNonNull("closedAt"), closed.toString());

    HttpResponse<String> again = send("POST", "/api/v1/boards/" + board + "/close", null, adminCookie);
    assertEquals(422, again.statusCode(), again.body());
    assertTrue(again.body().contains("42202"), again.body());

    JsonNode activated = data(send("POST", "/api/v1/boards/" + board + "/activate", null, adminCookie));
    assertEquals("active", activated.at("/status").asText(), activated.toString());
    assertTrue(activated.at("/closedAt").isNull(), activated.toString());

    JsonNode spaceDetail = data(send("GET", "/api/v1/board-spaces/" + space, null, adminCookie));
    assertEquals(1, spaceDetail.at("/boards").size(), spaceDetail.toString());
    assertEquals(board, spaceDetail.at("/boards/0/id").asLong(), spaceDetail.toString());
    JsonNode spaces = data(send("GET", "/api/v1/board-spaces?filters%5Bid%5D=" + space, null, adminCookie));
    assertEquals(1, spaces.at("/total").asLong(), spaces.toString());
    assertEquals(0, spaces.at("/items/0/boards").size(), spaces.toString());

    JsonNode closedSpace = data(send("POST", "/api/v1/board-spaces/" + space + "/close", null, adminCookie));
    assertEquals("closed", closedSpace.at("/status").asText(), closedSpace.toString());
    assertEquals(200, send("POST", "/api/v1/board-spaces/" + space + "/activate", null, adminCookie).statusCode());
  }

  @Test
  @DisplayName("meta：board_space/board 的 actions[].allowedStatus 与 workflow/board.yml 同源")
  void metaSourcesFromWorkflow() throws Exception {
    JsonNode spaceMeta = data(send("GET", "/api/v1/meta/board_space", null, adminCookie));
    assertEquals("board_space", spaceMeta.at("/domain").asText(), spaceMeta.toString());
    assertEquals("close", spaceMeta.at("/actions/0/action").asText(), spaceMeta.toString());
    assertEquals("active", spaceMeta.at("/actions/0/allowedStatus/0").asText(), spaceMeta.toString());
    assertEquals("activate", spaceMeta.at("/actions/1/action").asText(), spaceMeta.toString());
    assertEquals("closed", spaceMeta.at("/actions/1/allowedStatus/0").asText(), spaceMeta.toString());

    JsonNode boardMeta = data(send("GET", "/api/v1/meta/board", null, adminCookie));
    assertEquals("board", boardMeta.at("/domain").asText(), boardMeta.toString());
    assertEquals(2, boardMeta.at("/actions").size(), boardMeta.toString());
    assertEquals("extend", boardMeta.at("/fields/4/options/2/value").asText(), boardMeta.toString());

    JsonNode stageMeta = data(send("GET", "/api/v1/meta/stage", null, adminCookie));
    assertEquals(0, stageMeta.at("/actions").size(), stageMeta.toString());
    assertEquals("sort", stageMeta.at("/list/defaultSort").asText(), stageMeta.toString());
    assertEquals(2, data(send("GET", "/api/v1/meta/card", null, adminCookie)).at("/statusVisuals").size());
  }

  private JsonNode cardList(long boardId, String query) throws Exception {
    return data(send("GET", "/api/v1/boards/" + boardId + "/cards?" + query, null, adminCookie));
  }
}
