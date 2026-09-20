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
 * T-4 团队成员工时表（project 卡 §3.7/§5/§8）：全量提交 diff 增/删/改与幂等、逐项校验
 * （重复 account / days 超所在项目 days / 账号不存在 → 逐项 42201）、执行成员 days 取父项目上限，
 * 并覆盖白名单全量替换（同族 diff 语义）与「团队成员可见 private 项目」（§7）。
 */
class TeamMemberHandlerTest extends ApiTestSupport {

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  private JsonNode memberOf(JsonNode list, String account) {
    for (JsonNode item : list.at("/items")) {
      if (account.equals(item.at("/account").asText())) {
        return item;
      }
    }
    throw new AssertionError("列表中没有成员：" + account + " → " + list);
  }

  @Test
  @DisplayName("全量提交：增/改/删 diff 落库，同内容重复提交不增行不重建（幂等）")
  void submitDiffIsIdempotent() throws Exception {
    long product = createProduct(admin, "成员产品");
    long project = createProject(admin, "成员项目", product, "\"days\":20");
    accountWithPrivileges(admin, "t4-mem-a", "\"project-view\"");
    accountWithPrivileges(admin, "t4-mem-b", "\"project-view\"");

    String url = "/api/v1/projects/" + project + "/members";
    JsonNode added = data(send("POST", url,
        "{\"members\":[{\"account\":\"t4-mem-a\",\"role\":\"dev\",\"days\":10,\"hours\":8,\"sort\":1},"
            + "{\"account\":\"t4-mem-b\",\"days\":5}]}",
        admin));
    assertEquals(2, added.at("/results").size(), added.toString());
    assertTrue(added.at("/results/0/ok").asBoolean(), added.toString());
    assertTrue(added.at("/results/1/ok").asBoolean(), added.toString());

    JsonNode first = data(send("GET", url, null, admin));
    assertEquals(2, first.at("/total").asLong(), first.toString());
    JsonNode memberA = memberOf(first, "t4-mem-a");
    assertEquals("dev", memberA.at("/role").asText(), first.toString());
    assertEquals(10, memberA.at("/days").asInt(), first.toString());
    assertEquals(8.0, memberA.at("/hours").asDouble(), 0.001, first.toString());
    assertEquals("project", memberA.at("/objectType").asText(), first.toString());
    assertEquals(project, memberA.at("/objectId").asLong(), first.toString());
    long memberAId = memberA.at("/id").asLong();
    String joinDate = memberA.at("/joinDate").asText();

    // 幂等：同内容重复提交 → 行 id 与 joinDate 不变、总数不变
    data(send("POST", url,
        "{\"members\":[{\"account\":\"t4-mem-a\",\"role\":\"dev\",\"days\":10,\"hours\":8,\"sort\":1},"
            + "{\"account\":\"t4-mem-b\",\"days\":5}]}",
        admin));
    JsonNode second = data(send("GET", url, null, admin));
    assertEquals(2, second.at("/total").asLong(), second.toString());
    assertEquals(memberAId, memberOf(second, "t4-mem-a").at("/id").asLong(), second.toString());
    assertEquals(joinDate, memberOf(second, "t4-mem-a").at("/joinDate").asText(), second.toString());

    // 改：同一行（id 不变）role/days 覆盖
    data(send("POST", url,
        "{\"members\":[{\"account\":\"t4-mem-a\",\"role\":\"qa\",\"days\":15,\"hours\":8,\"sort\":1},"
            + "{\"account\":\"t4-mem-b\",\"days\":5}]}",
        admin));
    JsonNode updated = memberOf(data(send("GET", url, null, admin)), "t4-mem-a");
    assertEquals(memberAId, updated.at("/id").asLong(), updated.toString());
    assertEquals("qa", updated.at("/role").asText(), updated.toString());
    assertEquals(15, updated.at("/days").asInt(), updated.toString());

    // 删：提交表未列的成员被移除
    data(send("POST", url,
        "{\"members\":[{\"account\":\"t4-mem-a\",\"role\":\"qa\",\"days\":15,\"hours\":8,\"sort\":1}]}", admin));
    JsonNode removed = data(send("GET", url, null, admin));
    assertEquals(1, removed.at("/total").asLong(), removed.toString());
    assertEquals("t4-mem-a", removed.at("/items/0/account").asText(), removed.toString());

    // 空表提交 = 清空全部成员
    assertEquals(200, send("POST", url, "{\"members\":[]}", admin).statusCode());
    assertEquals(0, data(send("GET", url, null, admin)).at("/total").asLong());
  }

  @Test
  @DisplayName("逐项校验：重复 account / days 超项目 days / 账号不存在 → 逐项 42201，合法行照常落库")
  void perItemValidation() throws Exception {
    long product = createProduct(admin, "成员校验产品");
    long project = createProject(admin, "成员校验项目", product, "\"days\":5");
    long execution = createExecution(admin, project, "成员校验执行");
    accountWithPrivileges(admin, "t4-val-a", "\"project-view\"");
    accountWithPrivileges(admin, "t4-val-b", "\"project-view\"");

    JsonNode results = data(send("POST", "/api/v1/projects/" + project + "/members",
        "{\"members\":[{\"account\":\"t4-val-a\",\"days\":3},"
            + "{\"account\":\"t4-val-a\",\"days\":1},"
            + "{\"account\":\"t4-val-b\",\"days\":9},"
            + "{\"account\":\"t4-ghost\",\"days\":1}]}",
        admin));
    assertEquals(4, results.at("/results").size(), results.toString());
    assertTrue(results.at("/results/0/ok").asBoolean(), results.toString());
    assertFalse(results.at("/results/1/ok").asBoolean(), results.toString());
    assertTrue(results.at("/results/1/error").asText().startsWith("42201:"), results.toString());
    assertFalse(results.at("/results/2/ok").asBoolean(), results.toString());
    assertTrue(results.at("/results/2/error").asText().contains("days"), results.toString());
    assertFalse(results.at("/results/3/ok").asBoolean(), results.toString());
    assertEquals("t4-ghost", results.at("/results/3/account").asText(), results.toString());

    JsonNode list = data(send("GET", "/api/v1/projects/" + project + "/members", null, admin));
    assertEquals(1, list.at("/total").asLong(), "失败行不得落库：" + list);

    // 执行成员 days 上限取所属项目 days（=5）
    JsonNode execResults = data(send("POST", "/api/v1/executions/" + execution + "/members",
        "{\"members\":[{\"account\":\"t4-val-a\",\"days\":5},{\"account\":\"t4-val-b\",\"days\":6}]}", admin));
    assertTrue(execResults.at("/results/0/ok").asBoolean(), execResults.toString());
    assertFalse(execResults.at("/results/1/ok").asBoolean(), execResults.toString());
    assertTrue(execResults.at("/results/1/error").asText().contains("days"), execResults.toString());
    assertEquals(1, data(send("GET", "/api/v1/executions/" + execution + "/members", null, admin)).at("/total")
        .asLong());
  }

  @Test
  @DisplayName("白名单全量替换：增/删 diff 落 acl_entry，账号不存在 42201，白名单账号可见 private 项目（§7）")
  void whitelistReplace() throws Exception {
    long product = createProduct(admin, "白名单产品");
    long project = createProject(admin, "白名单项目", product, "\"acl\":\"private\"");
    String guest = accountWithPrivileges(admin, "t4-wl-a", "\"project-view\"");

    String url = "/api/v1/projects/" + project + "/whitelist";
    assertEquals(0, data(send("GET", url, null, admin)).at("/accounts").size());
    assertEquals(403, send("GET", "/api/v1/projects/" + project, null, guest).statusCode());

    JsonNode added = data(send("POST", url, "{\"accounts\":[\"t4-wl-a\"]}", admin));
    assertEquals(1, added.at("/accounts").size(), added.toString());
    assertEquals("t4-wl-a", added.at("/accounts/0").asText(), added.toString());
    assertEquals(200, send("GET", "/api/v1/projects/" + project, null, guest).statusCode());

    HttpResponse<String> unknown = send("POST", url, "{\"accounts\":[\"t4-ghost\"]}", admin);
    assertEquals(422, unknown.statusCode(), unknown.body());
    assertTrue(unknown.body().contains("42201"), unknown.body());
    assertEquals(1, data(send("GET", url, null, admin)).at("/accounts").size(), "校验失败不得改动白名单");

    JsonNode cleared = data(send("POST", url, "{\"accounts\":[]}", admin));
    assertEquals(0, cleared.at("/accounts").size(), cleared.toString());
    assertEquals(403, send("GET", "/api/v1/projects/" + project, null, guest).statusCode());
  }

  @Test
  @DisplayName("§7 可见性：团队成员可见 private 项目（详情 200 / 列表含该项目），移除成员后不可见")
  void teamMemberSeesPrivateProject() throws Exception {
    long product = createProduct(admin, "成员可见产品");
    long project = createProject(admin, "成员可见项目", product, "\"acl\":\"private\"");
    String member = accountWithPrivileges(admin, "t4-vis-a", "\"project-view\"");
    assertEquals(403, send("GET", "/api/v1/projects/" + project, null, member).statusCode());

    assertEquals(200, send("POST", "/api/v1/projects/" + project + "/members",
        "{\"members\":[{\"account\":\"t4-vis-a\"}]}", admin).statusCode());
    assertEquals(200, send("GET", "/api/v1/projects/" + project, null, member).statusCode());
    JsonNode visible = data(send("GET", "/api/v1/projects?filters%5Bid%5D=" + project, null, member));
    assertEquals(1, visible.at("/total").asLong(), visible.toString());

    assertEquals(200, send("POST", "/api/v1/projects/" + project + "/members", "{\"members\":[]}", admin).statusCode());
    assertEquals(403, send("GET", "/api/v1/projects/" + project, null, member).statusCode());
  }
}
