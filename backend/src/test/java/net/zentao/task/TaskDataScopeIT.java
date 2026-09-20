package net.zentao.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.zentao.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * T-12 task 数据权限与并发 IT（task 卡 §7/§8，Testcontainers MySQL 8.4 真库）：
 * 不可见执行的任务列表 0 条/详情与动作 40302；执行已关闭写端点 42203；
 * 两人依次完成同一任务，后至者 42202（状态即并发闸门）。
 */
class TaskDataScopeIT extends MySqlContainerSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private long viewerGroupId;

  @BeforeEach
  void login() throws Exception {
    adminCookie = loginAs("admin", "admin123");
  }

  private String loginAs(String account, String password) throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/session",
        "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}", null);
    assertEquals(200, response.statusCode(), response.body());
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private HttpResponse<String> send(String method, String path, String body, String cookie) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Content-Type", "application/json");
    if (cookie != null) {
      builder.header("Cookie", cookie);
    }
    builder.method(method,
        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private long dataId(HttpResponse<String> response) throws Exception {
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data/id").asLong();
  }

  private void ensureViewer(String account) throws Exception {
    if (viewerGroupId == 0) {
      viewerGroupId = dataId(send("POST", "/api/v1/groups",
          "{\"name\":\"IT 任务组 " + System.nanoTime() + "\"}", adminCookie));
      send("PUT", "/api/v1/groups/" + viewerGroupId + "/privileges",
          "{\"codes\":[\"task-view\",\"task-create\",\"task-start\",\"task-finish\",\"task-effort\","
              + "\"execution-view\",\"project-view\"]}",
          adminCookie);
    }
    HttpResponse<String> created = send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"" + account
            + "\",\"groupIds\":[" + viewerGroupId + "]}",
        adminCookie);
    assertTrue(created.statusCode() == 200 || created.statusCode() == 422, created.body());
  }

  private long freshExecution() throws Exception {
    long product = dataId(send("POST", "/api/v1/products",
        "{\"name\":\"IT 任务产品 " + System.nanoTime() + "\",\"acl\":\"public\"}", adminCookie));
    long project = dataId(send("POST", "/api/v1/projects",
        "{\"name\":\"IT 任务项目 " + System.nanoTime() + "\",\"beginDate\":\"2026-09-01\","
            + "\"endDate\":\"2026-12-31\",\"acl\":\"private\",\"productIds\":[" + product + "]}",
        adminCookie));
    return dataId(send("POST", "/api/v1/projects/" + project + "/executions",
        "{\"type\":\"sprint\",\"name\":\"IT 执行 " + System.nanoTime() + "\",\"beginDate\":\"2026-09-01\","
            + "\"endDate\":\"2026-09-30\"}",
        adminCookie));
  }

  private long createTask(long executionId, String title) throws Exception {
    return dataId(send("POST", "/api/v1/executions/" + executionId + "/tasks",
        "{\"title\":\"" + title + "\"}", adminCookie));
  }

  @Test
  @DisplayName("不可见执行：列表 0 条、详情 40302、动作 40302；执行关闭后写端点 42203")
  void executionAclAndClosedGate() throws Exception {
    ensureViewer("it-task-guest");
    long execution = freshExecution();
    long task = createTask(execution, "IT 私有任务");
    String guestCookie = loginAs("it-task-guest", "secret123");

    JsonNode guestList = json.readTree(send("GET", "/api/v1/executions/" + execution + "/tasks", null, guestCookie)
        .body()).at("/data");
    assertEquals(0, guestList.at("/total").asLong(), guestList.toString());

    HttpResponse<String> guestDetail = send("GET", "/api/v1/tasks/" + task, null, guestCookie);
    assertEquals(403, guestDetail.statusCode(), guestDetail.body());
    assertTrue(guestDetail.body().contains("40302"), guestDetail.body());
    HttpResponse<String> guestAction = send("POST", "/api/v1/tasks/" + task + "/start", "{}", guestCookie);
    assertEquals(403, guestAction.statusCode(), guestAction.body());

    // 执行关闭 → 其下任务写端点整体只读（42203）
    send("POST", "/api/v1/executions/" + execution + "/start", "{}", adminCookie);
    send("POST", "/api/v1/executions/" + execution + "/close", "{}", adminCookie);
    HttpResponse<String> closedWrite = send("POST", "/api/v1/tasks/" + task + "/start", "{}", adminCookie);
    assertEquals(422, closedWrite.statusCode(), closedWrite.body());
    assertTrue(closedWrite.body().contains("42203"), closedWrite.body());
    HttpResponse<String> closedEffort = send("POST", "/api/v1/tasks/" + task + "/efforts",
        "{\"consumedHours\":1}", adminCookie);
    assertEquals(422, closedEffort.statusCode(), closedEffort.body());
    assertTrue(closedEffort.body().contains("42203"), closedEffort.body());
  }

  @Test
  @DisplayName("并发完成：先至者 done，后至者 42202（任务状态即并发闸门）")
  void concurrentFinish() throws Exception {
    long execution = freshExecution();
    long task = createTask(execution, "IT 并发任务");
    send("POST", "/api/v1/tasks/" + task + "/start", "{\"leftHours\":2}", adminCookie);

    HttpResponse<String> first = send("POST", "/api/v1/tasks/" + task + "/finish",
        "{\"consumedHours\":2,\"comment\":\"先到\"}", adminCookie);
    assertEquals(200, first.statusCode(), first.body());
    assertEquals("done", json.readTree(first.body()).at("/data/status").asText(), first.body());

    HttpResponse<String> second = send("POST", "/api/v1/tasks/" + task + "/finish",
        "{\"consumedHours\":1,\"comment\":\"后到\"}", adminCookie);
    assertEquals(422, second.statusCode(), second.body());
    assertTrue(second.body().contains("42202"), second.body());

    JsonNode detail = json.readTree(send("GET", "/api/v1/tasks/" + task, null, adminCookie).body()).at("/data");
    assertEquals("done", detail.at("/status").asText(), detail.toString());
    assertEquals(2, detail.at("/consumedHours").asInt(), "后至者不得产生半更新：" + detail);
  }
}
