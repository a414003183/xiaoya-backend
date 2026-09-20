package net.zentao.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * T-1 Bug 状态机（quality 卡 §8）：confirm/resolve/activate/close/assign 合法与非法迁移（42202 矩阵）、
 * resolve/activate 守卫（42201）、activate 回派原解决人且 activatedCount+1、tostory 建需求并回填 storyId。
 */
class BugStateMachineTest extends net.zentao.H2TestSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private long productId;

  @BeforeEach
  void prepare() throws Exception {
    HttpResponse<String> login = send("POST", "/api/v1/session",
        "{\"account\":\"admin\",\"password\":\"admin123\"}", null);
    assertEquals(200, login.statusCode(), login.body());
    adminCookie = login.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
    HttpResponse<String> created = send("POST", "/api/v1/products",
        "{\"name\":\"Bug状态机-" + System.nanoTime() + "\"}", adminCookie);
    assertEquals(200, created.statusCode(), created.body());
    productId = json.readTree(created.body()).at("/data/id").asLong();
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

  private HttpResponse<String> send(String method, String path, String body) throws Exception {
    return send(method, path, body, adminCookie);
  }

  private long createBug(String title) throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/products/" + productId + "/bugs",
        "{\"title\":\"" + title + "\"}");
    assertEquals(200, created.statusCode(), created.body());
    return json.readTree(created.body()).at("/data/id").asLong();
  }

  private JsonNode bug(long id) throws Exception {
    return json.readTree(send("GET", "/api/v1/bugs/" + id, null).body()).at("/data");
  }

  @Test
  @DisplayName("主链路：提→confirm（顺带改派落 assignedAt）→resolve(fixed)→close；动态流/字段落痕")
  void happyPath() throws Exception {
    long id = createBug("主链路");
    HttpResponse<String> confirmed = send("POST", "/api/v1/bugs/" + id + "/confirm",
        "{\"assignee\":\"admin\",\"comment\":\"确认\"}");
    assertEquals(200, confirmed.statusCode(), confirmed.body());
    JsonNode afterConfirm = bug(id);
    assertTrue(afterConfirm.at("/confirmed").asBoolean(), afterConfirm.toString());
    assertTrue(!afterConfirm.at("/assignedAt").isNull(), afterConfirm.toString());

    HttpResponse<String> resolved = send("POST", "/api/v1/bugs/" + id + "/resolve",
        "{\"resolution\":\"fixed\",\"resolvedBuild\":\"b1\"}");
    assertEquals(200, resolved.statusCode(), resolved.body());
    JsonNode afterResolve = bug(id);
    assertEquals("resolved", afterResolve.at("/status").asText(), afterResolve.toString());
    assertEquals("admin", afterResolve.at("/resolvedBy").asText(), afterResolve.toString());
    assertEquals("b1", afterResolve.at("/resolvedBuild").asText(), afterResolve.toString());

    HttpResponse<String> closed = send("POST", "/api/v1/bugs/" + id + "/close", "{\"comment\":\"关闭\"}");
    assertEquals(200, closed.statusCode(), closed.body());
    JsonNode afterClose = bug(id);
    assertEquals("closed", afterClose.at("/status").asText(), afterClose.toString());
    assertEquals("admin", afterClose.at("/closedBy").asText(), afterClose.toString());

    JsonNode activities = json.readTree(send("GET", "/api/v1/bugs/" + id + "/activities", null).body())
        .at("/data/items");
    assertTrue(activities.size() >= 4, activities.toString());
    assertEquals("resolved", activities.get(1).at("/action").asText(), activities.toString());
    assertEquals("fixed", activities.get(1).at("/detail/0/newValue").asText(), activities.toString());
  }

  @Test
  @DisplayName("42202 矩阵：active 不可 close/activate；resolved 不可 confirm/resolve/assign 越界后 closed 拒 assign")
  void illegalTransitions() throws Exception {
    long id = createBug("矩阵");
    assertEquals(422, send("POST", "/api/v1/bugs/" + id + "/close", "{}").statusCode());
    assertEquals(422, send("POST", "/api/v1/bugs/" + id + "/activate",
        "{\"openedBuilds\":\"b1\"}").statusCode());

    assertEquals(200, send("POST", "/api/v1/bugs/" + id + "/confirm", "{}").statusCode());
    assertEquals(422, send("POST", "/api/v1/bugs/" + id + "/confirm", "{}").statusCode());
    assertTrue(send("POST", "/api/v1/bugs/" + id + "/confirm", "{}").body().contains("42202"));

    assertEquals(200, send("POST", "/api/v1/bugs/" + id + "/resolve",
        "{\"resolution\":\"fixed\",\"resolvedBuild\":\"b1\"}").statusCode());
    assertEquals(422, send("POST", "/api/v1/bugs/" + id + "/confirm", "{}").statusCode());
    assertEquals(422, send("POST", "/api/v1/bugs/" + id + "/resolve",
        "{\"resolution\":\"fixed\",\"resolvedBuild\":\"b1\"}").statusCode());

    assertEquals(200, send("POST", "/api/v1/bugs/" + id + "/close", "{}").statusCode());
    HttpResponse<String> assignClosed = send("POST", "/api/v1/bugs/" + id + "/assign",
        "{\"assignee\":\"admin\"}");
    assertEquals(422, assignClosed.statusCode(), assignClosed.body());
    assertTrue(assignClosed.body().contains("42202"), assignClosed.body());
  }

  @Test
  @DisplayName("resolve 守卫：缺 resolution/duplicate 缺 duplicateOfId/目标不存在/fixed 缺 resolvedBuild → 42201")
  void resolveGuards() throws Exception {
    long id = createBug("守卫");
    long other = createBug("重复目标");

    assertEquals(422, send("POST", "/api/v1/bugs/" + id + "/resolve", "{}").statusCode());
    assertTrue(send("POST", "/api/v1/bugs/" + id + "/resolve", "{}").body().contains("42201"));

    HttpResponse<String> duplicateMissing = send("POST", "/api/v1/bugs/" + id + "/resolve",
        "{\"resolution\":\"duplicate\"}");
    assertEquals(422, duplicateMissing.statusCode(), duplicateMissing.body());
    assertTrue(duplicateMissing.body().contains("42201"), duplicateMissing.body());

    HttpResponse<String> duplicateNotFound = send("POST", "/api/v1/bugs/" + id + "/resolve",
        "{\"resolution\":\"duplicate\",\"duplicateOfId\":99999}");
    assertEquals(422, duplicateNotFound.statusCode(), duplicateNotFound.body());

    HttpResponse<String> duplicateOk = send("POST", "/api/v1/bugs/" + id + "/resolve",
        "{\"resolution\":\"duplicate\",\"duplicateOfId\":" + other + "}");
    assertEquals(200, duplicateOk.statusCode(), duplicateOk.body());
    assertEquals(other, bug(id).at("/duplicateOfId").asLong());

    HttpResponse<String> fixedMissing = send("POST", "/api/v1/bugs/" + other + "/resolve",
        "{\"resolution\":\"fixed\"}");
    assertEquals(422, fixedMissing.statusCode(), fixedMissing.body());
    assertTrue(fixedMissing.body().contains("42201"), fixedMissing.body());
  }

  @Test
  @DisplayName("activate：openedBuilds 必填；assignee 省略回派原解决人；activatedCount+1；resolution 三字段清空")
  void activateSemantics() throws Exception {
    long id = createBug("重开");
    send("POST", "/api/v1/bugs/" + id + "/confirm", "{}");
    send("POST", "/api/v1/bugs/" + id + "/resolve",
        "{\"resolution\":\"fixed\",\"resolvedBuild\":\"b1\"}");

    HttpResponse<String> missing = send("POST", "/api/v1/bugs/" + id + "/activate", "{}");
    assertEquals(422, missing.statusCode(), missing.body());
    assertTrue(missing.body().contains("42201"), missing.body());

    HttpResponse<String> activated = send("POST", "/api/v1/bugs/" + id + "/activate",
        "{\"openedBuilds\":\"b2\"}");
    assertEquals(200, activated.statusCode(), activated.body());
    JsonNode after = bug(id);
    assertEquals("active", after.at("/status").asText(), after.toString());
    // 原解决人是 admin（resolve 由 admin 发起），省略 assignee 回派 admin
    assertEquals("admin", after.at("/assignee").asText(), after.toString());
    assertEquals(1, after.at("/activatedCount").asInt(), after.toString());
    assertTrue(after.at("/resolution").isNull(), after.toString());
    assertTrue(after.at("/resolvedBy").isNull(), after.toString());
    assertTrue(after.at("/resolvedAt").isNull(), after.toString());
    assertEquals("b2", after.at("/openedBuilds").asText(), after.toString());
  }

  @Test
  @DisplayName("resolve tostory：需求生成（source=bug/status=active）且 Bug.storyId 回填")
  void resolveToStory() throws Exception {
    long id = createBug("转需求");
    send("POST", "/api/v1/bugs/" + id + "/confirm", "{}");
    HttpResponse<String> resolved = send("POST", "/api/v1/bugs/" + id + "/resolve",
        "{\"resolution\":\"tostory\"}");
    assertEquals(200, resolved.statusCode(), resolved.body());
    long storyId = bug(id).at("/storyId").asLong();
    assertTrue(storyId > 0, "storyId 应回填");
    JsonNode story = json.readTree(send("GET", "/api/v1/stories/" + storyId, null).body()).at("/data");
    assertEquals("bug", story.at("/source").asText(), story.toString());
    assertEquals("active", story.at("/status").asText(), story.toString());
    assertEquals("转需求", story.at("/title").asText(), story.toString());
  }

  @Test
  @DisplayName("事务原子性：resolved 态再 resolve(tostory) → 42202 且不残留孤儿需求")
  void tostoryRollbackOnIllegalTransition() throws Exception {
    long id = createBug("回滚探针");
    send("POST", "/api/v1/bugs/" + id + "/confirm", "{}");
    send("POST", "/api/v1/bugs/" + id + "/resolve", "{\"resolution\":\"fixed\",\"resolvedBuild\":\"b1\"}");

    long storiesBefore = totalStories();
    HttpResponse<String> reResolve = send("POST", "/api/v1/bugs/" + id + "/resolve",
        "{\"resolution\":\"tostory\"}");
    assertEquals(422, reResolve.statusCode(), reResolve.body());
    assertTrue(reResolve.body().contains("42202"), reResolve.body());
    assertEquals(storiesBefore, totalStories(), "非法迁移整体回滚，不得残留转出的需求");
    assertTrue(bug(id).at("/storyId").isNull(), "storyId 不得回填");
  }

  private long totalStories() throws Exception {
    return json.readTree(send("GET", "/api/v1/products/" + productId + "/stories", null).body())
        .at("/data/total").asLong();
  }

  @Test
  @DisplayName("乐观锁与批量：lockVersion 不符 → 40901；批量创建 ≤50 逐条结果；batch 动作部分成功")
  void concurrencyAndBatch() throws Exception {
    long id = createBug("乐观锁");
    JsonNode current = bug(id);
    int lock = current.at("/lockVersion").asInt();
    HttpResponse<String> stale = send("PATCH", "/api/v1/bugs/" + id,
        "{\"title\":\"旧标题\",\"lockVersion\":" + (lock + 1) + "}");
    assertEquals(409, stale.statusCode(), stale.body());
    HttpResponse<String> fresh = send("PATCH", "/api/v1/bugs/" + id,
        "{\"title\":\"新标题\",\"severity\":1,\"lockVersion\":" + lock + "}");
    assertEquals(200, fresh.statusCode(), fresh.body());

    HttpResponse<String> batch = send("POST", "/api/v1/products/" + productId + "/bugs/batch",
        "{\"items\":[{\"title\":\"批1\"},{\"title\":\"\"},{\"title\":\"批2\"}]}");
    assertEquals(200, batch.statusCode(), batch.body());
    JsonNode results = json.readTree(batch.body()).at("/data/results");
    assertEquals(3, results.size(), results.toString());
    assertTrue(results.get(0).at("/ok").asBoolean());
    assertTrue(!results.get(1).at("/ok").asBoolean(), results.toString());
    assertTrue(results.get(1).at("/error").asText().startsWith("42201"), results.toString());

    long batchBug = results.get(0).at("/id").asLong();
    HttpResponse<String> actions = send("POST", "/api/v1/bugs/batch",
        "{\"ids\":[" + batchBug + ",99999],\"action\":\"confirm\",\"params\":{}}");
    assertEquals(200, actions.statusCode(), actions.body());
    JsonNode items = json.readTree(actions.body()).at("/data/results");
    assertTrue(items.get(0).at("/ok").asBoolean(), items.toString());
    assertTrue(!items.get(1).at("/ok").asBoolean(), items.toString());
  }
}
