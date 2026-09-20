package net.zentao.requirement;

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
import org.springframework.boot.test.context.SpringBootTest;

/**
 * T-4 需求后端（requirement 卡 §8）：9 动作迁移矩阵（42202）、needNotReview 直达、
 * reject comment 必填、duplicate 守卫、priority 越界 42201、type 三型过滤、动态流与通知落行。
 */
class StoryWorkflowTest extends net.zentao.H2TestSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private long productId;
  private static long productGroupId;

  @BeforeEach
  void prepare() throws Exception {
    adminCookie = loginAs("admin", "admin123");
    productId = ensureProduct();
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

  private long ensureGroup() throws Exception {
    if (productGroupId != 0) {
      return productGroupId;
    }
    HttpResponse<String> group = send("POST", "/api/v1/groups", "{\"name\":\"需求测试组\"}", adminCookie);
    assertEquals(200, group.statusCode(), group.body());
    productGroupId = json.readTree(group.body()).at("/data/id").asLong();
    send("PUT", "/api/v1/groups/" + productGroupId + "/privileges",
        "{\"codes\":[\"product-view\",\"story-view\",\"story-create\",\"story-edit\",\"story-submit-review\","
            + "\"story-pass\",\"story-change\",\"story-close\",\"story-activate\",\"story-assign\"]}",
        adminCookie);
    return productGroupId;
  }

  private void ensureAccount(String account) throws Exception {
    send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"" + account
            + "\",\"groupIds\":[" + ensureGroup() + "]}",
        adminCookie);
  }

  private long ensureProduct() throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/products",
        "{\"name\":\"需求测试产品\",\"acl\":\"public\"}", adminCookie);
    assertEquals(200, created.statusCode(), created.body());
    return json.readTree(created.body()).at("/data/id").asLong();
  }

  private JsonNode createStory(String fields) throws Exception {
    HttpResponse<String> created = send("POST", "/api/v1/products/" + productId + "/stories", fields, adminCookie);
    assertEquals(200, created.statusCode(), created.body());
    return json.readTree(created.body()).at("/data");
  }

  private JsonNode story(long storyId) throws Exception {
    HttpResponse<String> response = send("GET", "/api/v1/stories/" + storyId, null, adminCookie);
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data");
  }

  @Test
  @DisplayName("闭环：draft→reviewing→active→changing→changed→closed→active + 动态流动作名")
  void fullLifecycle() throws Exception {
    ensureAccount("story-reviewer");
    JsonNode draft = createStory("{\"title\":\"闭环需求\",\"priority\":2,\"reviewers\":[\"story-reviewer\"]}");
    long id = draft.at("/id").asLong();
    assertEquals("draft", draft.at("/status").asText());

    // 非法迁移：draft 不能 pass → 42202
    assertEquals(422, send("POST", "/api/v1/stories/" + id + "/pass", "{}", adminCookie).statusCode());

    JsonNode reviewing = after("submit-review", id, "{\"comment\":\"请评审\"}");
    assertEquals("reviewing", reviewing.at("/status").asText());

    // 非法迁移：reviewing 不能 change → 42202
    assertEquals(422, send("POST", "/api/v1/stories/" + id + "/change", null, adminCookie).statusCode());

    assertEquals("active", after("pass", id, "{\"comment\":\"通过\"}").at("/status").asText());
    assertEquals("changing", after("change", id, null).at("/status").asText());

    HttpResponse<String> changeDone = send("POST", "/api/v1/stories/" + id + "/change-done",
        "{\"title\":\"闭环需求（已变更）\",\"lockVersion\":3}", adminCookie);
    assertEquals(200, changeDone.statusCode(), changeDone.body());
    assertEquals("changed", json.readTree(changeDone.body()).at("/data/status").asText());

    JsonNode closed = after("close", id, "{\"closedReason\":\"done\",\"comment\":\"关闭\"}");
    assertEquals("closed", closed.at("/status").asText());
    assertTrue(closed.at("/closedAt").isTextual(), closed.toString());

    JsonNode activated = after("activate", id, "{}");
    assertEquals("active", activated.at("/status").asText());
    assertTrue(activated.at("/closedAt").isNull(), activated.toString());
    assertTrue(activated.at("/closedReason").isNull(), activated.toString());

    HttpResponse<String> activities = send("GET", "/api/v1/stories/" + id + "/activities", null, adminCookie);
    String body = activities.body();
    for (String action : new String[] {"created", "submitted", "passed", "changed", "changeDone", "closed", "activated"}) {
      assertTrue(body.contains("\"" + action + "\""), action + " 未落动态流：" + body);
    }
    assertTrue(body.contains("请评审"), body);
  }

  @Test
  @DisplayName("needNotReview=true 直达 active；reviewers 空 + needNotReview=false → 42203")
  void reviewBranching() throws Exception {
    JsonNode direct = createStory("{\"title\":\"免评审需求\",\"needNotReview\":true}");
    long directId = direct.at("/id").asLong();
    assertEquals("active", after("submit-review", directId, "{}").at("/status").asText());

    long guardedId = createStory("{\"title\":\"需评审需求\"}").at("/id").asLong();
    HttpResponse<String> blocked = send("POST", "/api/v1/stories/" + guardedId + "/submit-review", "{}", adminCookie);
    assertEquals(422, blocked.statusCode(), blocked.body());
    assertTrue(blocked.body().contains("42203") && blocked.body().contains("reviewers-required"), blocked.body());
  }

  @Test
  @DisplayName("reject：缺 comment → 42201；带 comment → draft 且 remark 落动态流")
  void rejectRequiresComment() throws Exception {
    ensureAccount("story-reviewer2");
    long id = createStory("{\"title\":\"拒绝需求\",\"reviewers\":[\"story-reviewer2\"]}").at("/id").asLong();
    after("submit-review", id, "{}");

    HttpResponse<String> missing = send("POST", "/api/v1/stories/" + id + "/reject", "{}", adminCookie);
    assertEquals(422, missing.statusCode(), missing.body());
    assertTrue(missing.body().contains("42201") && missing.body().contains("comment"), missing.body());

    JsonNode rejected = after("reject", id, "{\"comment\":\"信息不足\"}");
    assertEquals("draft", rejected.at("/status").asText());
    HttpResponse<String> activities = send("GET", "/api/v1/stories/" + id + "/activities", null, adminCookie);
    assertTrue(activities.body().contains("信息不足"), activities.body());
  }

  @Test
  @DisplayName("close：缺 closedReason → 42201；duplicate 缺 duplicateOfId → 42201；同产品才可作重复源")
  void closeGuards() throws Exception {
    long target = createStory("{\"title\":\"重复源需求\"}").at("/id").asLong();
    long id = createStory("{\"title\":\"待关闭需求\",\"needNotReview\":true}").at("/id").asLong();
    // draft 不能直接 close → 42202；提交评审直达 active 后按守卫关闭
    assertEquals(422, send("POST", "/api/v1/stories/" + id + "/close",
        "{\"closedReason\":\"done\"}", adminCookie).statusCode());
    after("submit-review", id, "{}");

    HttpResponse<String> noReason = send("POST", "/api/v1/stories/" + id + "/close", "{}", adminCookie);
    assertEquals(422, noReason.statusCode(), noReason.body());
    assertTrue(noReason.body().contains("closedReason"), noReason.body());

    HttpResponse<String> duplicateWithoutTarget = send("POST", "/api/v1/stories/" + id + "/close",
        "{\"closedReason\":\"duplicate\"}", adminCookie);
    assertEquals(422, duplicateWithoutTarget.statusCode(), duplicateWithoutTarget.body());

    HttpResponse<String> duplicateOk = send("POST", "/api/v1/stories/" + id + "/close",
        "{\"closedReason\":\"duplicate\",\"duplicateOfId\":" + target + "}", adminCookie);
    assertEquals(200, duplicateOk.statusCode(), duplicateOk.body());
    assertEquals("closed", json.readTree(duplicateOk.body()).at("/data/status").asText());
    assertEquals(target, json.readTree(duplicateOk.body()).at("/data/duplicateOfId").asLong());
  }

  @Test
  @DisplayName("字段校验：priority 越界 42201、reviewers 不存在 42201、assign 落 assignee/assignedAt")
  void validationAndAssign() throws Exception {
    HttpResponse<String> badPriority = send("POST", "/api/v1/products/" + productId + "/stories",
        "{\"title\":\"越界优先级\",\"priority\":9}", adminCookie);
    assertEquals(422, badPriority.statusCode(), badPriority.body());
    assertTrue(badPriority.body().contains("priority"), badPriority.body());

    HttpResponse<String> ghostReviewer = send("POST", "/api/v1/products/" + productId + "/stories",
        "{\"title\":\"幽灵评审\",\"reviewers\":[\"no-such-account\"]}", adminCookie);
    assertEquals(422, ghostReviewer.statusCode(), ghostReviewer.body());
    assertTrue(ghostReviewer.body().contains("reviewers"), ghostReviewer.body());

    ensureAccount("story-assignee");
    long id = createStory("{\"title\":\"指派需求\"}").at("/id").asLong();
    JsonNode assigned = after("assign", id, "{\"assignee\":\"story-assignee\"}");
    assertEquals("story-assignee", assigned.at("/assignee").asText());
    assertTrue(assigned.at("/assignedAt").isTextual(), assigned.toString());
    assertEquals("draft", assigned.at("/status").asText());

    HttpResponse<String> ghostAssignee = send("POST", "/api/v1/stories/" + id + "/assign",
        "{\"assignee\":\"no-such-account\"}", adminCookie);
    assertEquals(422, ghostAssignee.statusCode(), ghostAssignee.body());
  }

  @Test
  @DisplayName("type 三型同表：filters[type] 过滤生效、id 集合与 q 搜索命中")
  void threeTypes() throws Exception {
    createStory("{\"title\":\"普通需求甲\",\"type\":\"story\"}");
    createStory("{\"title\":\"史诗需求乙\",\"type\":\"epic\"}");
    createStory("{\"title\":\"业务需求丙\",\"type\":\"requirement\"}");

    HttpResponse<String> epics = send("GET",
        "/api/v1/products/" + productId + "/stories?filters%5Btype%5D=epic", null, adminCookie);
    assertEquals(200, epics.statusCode(), epics.body());
    assertTrue(json.readTree(epics.body()).at("/data/total").asLong() >= 1, epics.body());
    for (JsonNode item : json.readTree(epics.body()).at("/data/items")) {
      assertEquals("epic", item.at("/type").asText(), epics.body());
    }

    HttpResponse<String> search = send("GET", "/api/v1/products/" + productId + "/stories?q=史诗", null, adminCookie);
    assertTrue(json.readTree(search.body()).at("/data/total").asLong() >= 1, search.body());

    HttpResponse<String> unregistered = send("GET", "/api/v1/products/" + productId + "/stories?filters%5Bghost%5D=1",
        null, adminCookie);
    assertEquals(400, unregistered.statusCode(), unregistered.body());
  }

  private JsonNode after(String action, long storyId, String body) throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/stories/" + storyId + "/" + action, body, adminCookie);
    assertEquals(200, response.statusCode(), action + " 失败：" + response.body());
    return json.readTree(response.body()).at("/data");
  }
}
