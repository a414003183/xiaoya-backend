package net.zentao.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-1 三义实体后端（project 卡 §3.1/§7/§8）：层级与 path/grade、跨型挂载 42201、可写字段白名单、
 * 乐观锁 40901、private ACL（列表 0 条 / 详情 40302 / 白名单可见 / 超管全见）、DSL 白名单外 40001。
 */
class ProjectApiTest extends net.zentao.ApiTestSupport {

  private String adminCookie;
  private long projectGroupId;

  @BeforeEach
  void login() throws Exception {
    adminCookie = login("admin", "admin123");
  }

  /** 非超管账号需要功能权限码：建一个只含 project 域码的测试组。 */
  private String ensureAccount(String account) throws Exception {
    if (projectGroupId == 0) {
      projectGroupId = dataId(send("POST", "/api/v1/groups", "{\"name\":\"项目测试组\"}", adminCookie));
      assertEquals(200, send("PUT", "/api/v1/groups/" + projectGroupId + "/privileges",
          "{\"codes\":[\"project-view\",\"project-create\",\"project-edit\",\"program-view\",\"program-create\","
              + "\"execution-view\"]}",
          adminCookie).statusCode());
    }
    send("POST", "/api/v1/accounts", "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\""
        + account + "\",\"groupIds\":[" + projectGroupId + "]}", adminCookie);
    return account;
  }

  private long createProduct(String name) throws Exception {
    return dataId(send("POST", "/api/v1/products", "{\"name\":\"" + name + "\",\"acl\":\"public\"}", adminCookie));
  }

  private long createProgram(String name, String extraJson) throws Exception {
    return dataId(send("POST", "/api/v1/programs",
        "{\"name\":\"" + name + "\"" + (extraJson == null ? "" : "," + extraJson) + "}", adminCookie));
  }

  private long createProject(String name, long programId, long productId, String extraJson) throws Exception {
    return dataId(send("POST", "/api/v1/projects",
        "{\"name\":\"" + name + "\",\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-12-31\""
            + ",\"productIds\":[" + productId + "]"
            + (programId == 0 ? "" : ",\"parentId\":" + programId)
            + (extraJson == null ? "" : "," + extraJson) + "}",
        adminCookie));
  }

  @Test
  @DisplayName("层级：项目集→项目→执行 path/grade 正确，子资源列表按 parentId 过滤")
  void hierarchy() throws Exception {
    long product = createProduct("层级产品");
    long program = createProgram("层级项目集", null);
    long project = createProject("层级项目", program, product, null);
    long execution = dataId(send("POST", "/api/v1/projects/" + project + "/executions",
        "{\"type\":\"sprint\",\"name\":\"S1\",\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}",
        adminCookie));

    JsonNode programView = json.readTree(send("GET", "/api/v1/programs/" + program, null, adminCookie).body())
        .at("/data");
    assertEquals(1, programView.at("/grade").asInt(), programView.toString());
    assertEquals("," + program + ",", programView.at("/path").asText(), programView.toString());

    JsonNode projectView = json.readTree(send("GET", "/api/v1/projects/" + project, null, adminCookie).body())
        .at("/data");
    assertEquals(2, projectView.at("/grade").asInt(), projectView.toString());
    assertEquals("," + program + "," + project + ",", projectView.at("/path").asText(), projectView.toString());

    JsonNode executionView = json.readTree(send("GET", "/api/v1/executions/" + execution, null, adminCookie).body())
        .at("/data");
    assertEquals("sprint", executionView.at("/type").asText(), executionView.toString());
    assertEquals(3, executionView.at("/grade").asInt(), executionView.toString());

    JsonNode children = json.readTree(
        send("GET", "/api/v1/programs/" + program + "/projects", null, adminCookie).body()).at("/data");
    assertEquals(project, children.at("/items/0/id").asLong(), children.toString());
    JsonNode executions = json.readTree(
        send("GET", "/api/v1/projects/" + project + "/executions", null, adminCookie).body()).at("/data");
    assertEquals(execution, executions.at("/items/0/id").asLong(), executions.toString());
  }

  @Test
  @DisplayName("挂载与字段校验：执行挂项目集 42201、项目缺产品 42201、执行类型非法 42201、日期倒挂 42201")
  void validation() throws Exception {
    long product = createProduct("校验产品");
    long program = createProgram("校验项目集", null);

    HttpResponse<String> wrongMount = send("POST", "/api/v1/projects/" + program + "/executions",
        "{\"type\":\"sprint\",\"name\":\"错挂\",\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}", adminCookie);
    assertEquals(404, wrongMount.statusCode(), wrongMount.body());

    HttpResponse<String> noProduct = send("POST", "/api/v1/projects",
        "{\"name\":\"无产品\",\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}", adminCookie);
    assertEquals(422, noProduct.statusCode(), noProduct.body());
    assertTrue(noProduct.body().contains("productIds"), noProduct.body());

    long project = createProject("校验项目", program, product, null);
    HttpResponse<String> badType = send("POST", "/api/v1/projects/" + project + "/executions",
        "{\"type\":\"epic\",\"name\":\"错类型\",\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}", adminCookie);
    assertEquals(422, badType.statusCode(), badType.body());
    assertTrue(badType.body().contains("type"), badType.body());

    HttpResponse<String> badDates = send("POST", "/api/v1/projects",
        "{\"name\":\"日期倒挂\",\"beginDate\":\"2026-12-01\",\"endDate\":\"2026-09-30\",\"productIds\":[" + product + "]}",
        adminCookie);
    assertEquals(422, badDates.statusCode(), badDates.body());

    HttpResponse<String> unknownAccount = send("POST", "/api/v1/projects",
        "{\"name\":\"幽灵PM\",\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\",\"pm\":\"ghost\",\"productIds\":["
            + product + "]}",
        adminCookie);
    assertEquals(422, unknownAccount.statusCode(), unknownAccount.body());
  }

  @Test
  @DisplayName("PATCH：乐观锁 40901、可写字段白名单型别不符 42201、更新生效且动态流落痕")
  void updateRules() throws Exception {
    long product = createProduct("更新产品");
    long program = createProgram("更新项目集", null);
    long project = createProject("更新项目", program, product, null);

    HttpResponse<String> stale = send("PATCH", "/api/v1/projects/" + project,
        "{\"name\":\"改名\",\"lockVersion\":99}", adminCookie);
    assertEquals(409, stale.statusCode(), stale.body());
    assertTrue(stale.body().contains("40901"), stale.body());

    HttpResponse<String> notWritable = send("PATCH", "/api/v1/programs/" + program,
        "{\"model\":\"waterfall\",\"lockVersion\":0}", adminCookie);
    assertEquals(422, notWritable.statusCode(), notWritable.body());
    assertTrue(notWritable.body().contains("model"), notWritable.body());

    HttpResponse<String> patched = send("PATCH", "/api/v1/projects/" + project,
        "{\"name\":\"更新项目改名\",\"days\":20,\"budget\":1000.50,\"lockVersion\":0}", adminCookie);
    assertEquals(200, patched.statusCode(), patched.body());
    JsonNode view = json.readTree(patched.body()).at("/data");
    assertEquals("更新项目改名", view.at("/name").asText(), view.toString());
    assertEquals(20, view.at("/days").asInt(), view.toString());
    assertEquals(1, view.at("/lockVersion").asInt(), view.toString());

    JsonNode activities = json.readTree(
        send("GET", "/api/v1/projects/" + project + "/activities", null, adminCookie).body()).at("/data");
    assertEquals("created", activities.at("/items/0/action").asText(), activities.toString());
  }

  @Test
  @DisplayName("ACL：private 项目外人列表 0 条/详情 40302，白名单与 pm 可见，超管全见")
  void privateAcl() throws Exception {
    long product = createProduct("权限产品");
    ensureAccount("proj-dev1");
    ensureAccount("proj-guest");
    long whitelistProject = createProject("私有项目", 0, product,
        "\"acl\":\"private\",\"whitelist\":[\"proj-dev1\"]");
    long pmProject = createProject("PM项目", 0, product, "\"acl\":\"private\",\"pm\":\"proj-dev1\"");

    String devCookie = login("proj-dev1", "secret123");
    String guestCookie = login("proj-guest", "secret123");

    JsonNode devList = json.readTree(send("GET",
        "/api/v1/projects?filters%5Bid%5D=" + whitelistProject + "," + pmProject, null, devCookie).body()).at("/data");
    assertEquals(2, devList.at("/items").size(), devList.toString());
    assertEquals(200, send("GET", "/api/v1/projects/" + pmProject, null, devCookie).statusCode());

    JsonNode guestList = json.readTree(send("GET",
        "/api/v1/projects?filters%5Bid%5D=" + whitelistProject + "," + pmProject, null, guestCookie).body())
        .at("/data");
    assertEquals(0, guestList.at("/items").size(), guestList.toString());
    HttpResponse<String> guestDetail = send("GET", "/api/v1/projects/" + whitelistProject, null, guestCookie);
    assertEquals(403, guestDetail.statusCode(), guestDetail.body());
    assertTrue(guestDetail.body().contains("40302"), guestDetail.body());

    JsonNode adminList = json.readTree(send("GET", "/api/v1/projects", null, adminCookie).body()).at("/data");
    assertTrue(adminList.at("/total").asLong() >= 2, adminList.toString());
  }

  @Test
  @DisplayName("列表 DSL：filters[status] 生效、白名单外字段 40001、q 命中 name")
  void listDsl() throws Exception {
    long product = createProduct("DSL产品");
    long program = createProgram("DSL项目集", null);
    createProject("DSL项目甲", program, product, null);
    long second = createProject("DSL项目乙", program, product, null);

    JsonNode waiting = json.readTree(send("GET", "/api/v1/projects?filters%5Bstatus%5D=wait", null, adminCookie).body())
        .at("/data");
    assertTrue(waiting.at("/total").asLong() >= 1, waiting.toString());

    HttpResponse<String> unregistered = send("GET", "/api/v1/projects?filters%5Bghost%5D=1", null, adminCookie);
    assertEquals(400, unregistered.statusCode(), unregistered.body());
    assertTrue(unregistered.body().contains("40001"), unregistered.body());

    HttpResponse<String> unsortable = send("GET", "/api/v1/projects?sort=description", null, adminCookie);
    assertEquals(400, unsortable.statusCode(), unsortable.body());

    JsonNode searched = json.readTree(send("GET", "/api/v1/projects?q=DSL项目乙", null, adminCookie).body()).at("/data");
    assertEquals(1, searched.at("/items").size(), searched.toString());
    assertEquals(second, searched.at("/items/0/id").asLong(), searched.toString());
  }
}
