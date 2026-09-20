package net.zentao.org;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import net.zentao.platform.rbac.DataScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 权限组 API（org 卡 §8）：矩阵整体替换/未注册码 42201/admin 组守卫/成员差量/保存后 /me 即时生效/复制组。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GroupApiTest {

  @Value("${local.server.port}")
  int port;

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Autowired
  DataScope dataScope;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private long groupId;

  @BeforeEach
  void seed() throws Exception {
    adminCookie = cookieOf(send("POST", "/api/v1/session",
        "{\"account\":\"admin\",\"password\":\"admin123\"}", null));
    groupId = createGroup("研发组-" + System.nanoTime(), "测试组");
  }

  private String cookieOf(HttpResponse<String> response) {
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private long createGroup(String name, String description) throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/groups",
        "{\"name\":\"" + name + "\",\"description\":\"" + description + "\"}", adminCookie);
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data/id").asLong();
  }

  private HttpResponse<String> send(String method, String path, String body, String cookie) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Content-Type", "application/json");
    if (cookie != null) {
      builder.header("Cookie", cookie);
    }
    builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  @DisplayName("name 重复 42201；矩阵整体替换；未注册码 42201；保存后成员 /me 即时生效；移出组失效")
  void privilegesLifecycle() throws Exception {
    // name 重复
    HttpResponse<String> duplicate = send("POST", "/api/v1/groups",
        "{\"name\":\"管理员\"}", adminCookie);
    assertEquals(422, duplicate.statusCode(), duplicate.body());
    assertTrue(duplicate.body().contains("42201"), duplicate.body());

    // 矩阵整体替换
    HttpResponse<String> put1 = send("PUT", "/api/v1/groups/" + groupId + "/privileges",
        "{\"codes\":[\"department-edit\",\"account-create\",\"account-view\"]}", adminCookie);
    assertEquals(200, put1.statusCode(), put1.body());
    HttpResponse<String> get1 = send("GET", "/api/v1/groups/" + groupId + "/privileges", null, adminCookie);
    assertTrue(get1.body().contains("department-edit") && !get1.body().contains("group-edit"), get1.body());

    // 再替换为单码（验证"整体替换"语义：之前的码被移除）
    send("PUT", "/api/v1/groups/" + groupId + "/privileges", "{\"codes\":[\"account-view\"]}", adminCookie);
    HttpResponse<String> get2 = send("GET", "/api/v1/groups/" + groupId + "/privileges", null, adminCookie);
    assertFalse2(get2.body().contains("department-edit"), "整体替换应移除未勾选码: " + get2.body());

    // 未注册权限码 → 42201
    HttpResponse<String> unregistered = send("PUT", "/api/v1/groups/" + groupId + "/privileges",
        "{\"codes\":[\"department-edit\",\"not-a-code\"]}", adminCookie);
    assertEquals(422, unregistered.statusCode(), unregistered.body());
    assertTrue(unregistered.body().contains("42201"), unregistered.body());

    // 建账号入组 → /me 即时含码；移出组 → 失效
    HttpResponse<String> account = send("POST", "/api/v1/accounts",
        "{\"account\":\"grp-user\",\"password\":\"secret123\",\"realName\":\"组成员\",\"groupIds\":[" + groupId + "]}",
        adminCookie);
    assertEquals(200, account.statusCode(), account.body());
    long accountId = json.readTree(account.body()).at("/data/id").asLong();
    HttpResponse<String> userLogin = send("POST", "/api/v1/session",
        "{\"account\":\"grp-user\",\"password\":\"secret123\"}", null);
    String userCookie = cookieOf(userLogin);
    HttpResponse<String> me = send("GET", "/api/v1/me", null, userCookie);
    assertTrue(me.body().contains("account-view"), "组成员 /me 应含码: " + me.body());

    HttpResponse<String> removed = send("PUT", "/api/v1/groups/" + groupId + "/members",
        "{\"accountIds\":[]}", adminCookie);
    assertEquals(200, removed.statusCode(), removed.body());
    HttpResponse<String> meAfter = send("GET", "/api/v1/me", null, userCookie);
    assertFalse2(meAfter.body().contains("account-view"), "移出组即失效: " + meAfter.body());
  }

  private static void assertFalse2(boolean condition, String message) {
    if (condition) {
      throw new AssertionError(message);
    }
  }

  @Test
  @DisplayName("成员写：不存在账号 42201；重复去重；DELETE 级联；admin 组删除 42203")
  void membersAndDeleteGuards() throws Exception {
    HttpResponse<String> account = send("POST", "/api/v1/accounts",
        "{\"account\":\"grp-m2\",\"password\":\"secret123\",\"realName\":\"成员二\"}", adminCookie);
    long accountId = json.readTree(account.body()).at("/data/id").asLong();

    HttpResponse<String> dup = send("PUT", "/api/v1/groups/" + groupId + "/members",
        "{\"accountIds\":[" + accountId + "," + accountId + "]}", adminCookie);
    assertEquals(200, dup.statusCode(), dup.body());
    assertEquals(1, json.readTree(dup.body()).at("/data/total").asInt(), "重复应去重");

    HttpResponse<String> missing = send("PUT", "/api/v1/groups/" + groupId + "/members",
        "{\"accountIds\":[999999]}", adminCookie);
    assertEquals(422, missing.statusCode(), missing.body());
    assertTrue(missing.body().contains("42201"), missing.body());

    // DELETE 组级联：user_group/group_priv 清空
    send("PUT", "/api/v1/groups/" + groupId + "/privileges", "{\"codes\":[\"account-view\"]}", adminCookie);
    HttpResponse<String> deleted = send("DELETE", "/api/v1/groups/" + groupId, null, adminCookie);
    assertEquals(200, deleted.statusCode(), deleted.body());
    assertEquals(0, jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM user_group WHERE group_id = ?", Integer.class, groupId));
    assertEquals(0, jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM group_priv WHERE group_id = ?", Integer.class, groupId));

    HttpResponse<String> adminGroup = send("DELETE", "/api/v1/groups/1", null, adminCookie);
    assertEquals(422, adminGroup.statusCode(), adminGroup.body());
    assertTrue(adminGroup.body().contains("42203"), adminGroup.body());
  }

  @Test
  @DisplayName("复制组：copyPrivileges/copyMembers 各选项生效，源组不变")
  void copyGroupOptions() throws Exception {
    long accountId = 0;
    HttpResponse<String> account = send("POST", "/api/v1/accounts",
        "{\"account\":\"grp-src\",\"password\":\"secret123\",\"realName\":\"源成员\"}", adminCookie);
    accountId = json.readTree(account.body()).at("/data/id").asLong();
    send("PUT", "/api/v1/groups/" + groupId + "/privileges", "{\"codes\":[\"account-view\",\"department-view\"]}", adminCookie);
    send("PUT", "/api/v1/groups/" + groupId + "/members", "{\"accountIds\":[" + accountId + "]}", adminCookie);

    HttpResponse<String> copied = send("POST", "/api/v1/groups/" + groupId + "/copy",
        "{\"name\":\"研发组副本\",\"copyPrivileges\":true,\"copyMembers\":false}", adminCookie);
    assertEquals(200, copied.statusCode(), copied.body());
    JsonNode view = json.readTree(copied.body()).at("/data");
    long newId = view.get("id").asLong();
    assertEquals(2, view.get("privilegeCount").asInt(), "副本应含权限码");
    assertEquals(0, view.get("memberCount").asInt(), "副本不应含成员");

    HttpResponse<String> source = send("GET", "/api/v1/groups/" + groupId, null, adminCookie);
    assertEquals(1, json.readTree(source.body()).at("/data/memberCount").asInt(), "源组成员不变");
  }

  @Test
  @DisplayName("A-08 组 acl：写入/整体替换/子键 null 清空；DataScope 并集读取联动")
  void aclWriteReplaceClearAndDataScope() throws Exception {
    // 写入：null 子键归一空表
    HttpResponse<String> put1 = send("PATCH", "/api/v1/groups/" + groupId,
        "{\"acl\":{\"products\":[1,2],\"projects\":[3]},\"lockVersion\":0}", adminCookie);
    assertEquals(200, put1.statusCode(), put1.body());
    JsonNode acl1 = json.readTree(put1.body()).at("/data/acl");
    assertEquals("[1,2]", acl1.get("products").toString());
    assertEquals("[3]", acl1.get("projects").toString());
    assertEquals("[]", acl1.get("views").toString(), "null 子键应归一空表");
    assertEquals("[]", acl1.get("executions").toString());

    // DataScope 联动：成员的组 acl 并集生效
    HttpResponse<String> account = send("POST", "/api/v1/accounts",
        "{\"account\":\"acl-user\",\"password\":\"secret123\",\"realName\":\"acl成员\",\"groupIds\":[" + groupId + "]}",
        adminCookie);
    long accountId = json.readTree(account.body()).at("/data/id").asLong();
    DataScope.Acl union = dataScope.aclUnion(new net.zentao.platform.session.SessionPrincipal(accountId, "acl-user"));
    assertEquals(List.of(1L, 2L), union.products());
    assertEquals(List.of(3L), union.projects());

    // 整体替换 + 子键 null 清空（views 保留、products 清空、executions 新增）
    int lockVersion = json.readTree(put1.body()).at("/data/lockVersion").asInt();
    HttpResponse<String> put2 = send("PATCH", "/api/v1/groups/" + groupId,
        "{\"acl\":{\"views\":[7],\"products\":null,\"executions\":[9]},\"lockVersion\":" + lockVersion + "}",
        adminCookie);
    assertEquals(200, put2.statusCode(), put2.body());
    JsonNode acl2 = json.readTree(put2.body()).at("/data/acl");
    assertEquals("[7]", acl2.get("views").toString());
    assertEquals("[]", acl2.get("products").toString(), "子键 null = 清空该键");
    assertEquals("[]", acl2.get("projects").toString(), "整体替换应移除未传键");
    assertEquals("[9]", acl2.get("executions").toString());

    // 落库真值（JSON 文本列）与 DataScope 再读
    String stored = jdbcTemplate.queryForObject("SELECT acl FROM auth_group WHERE id = ?", String.class, groupId);
    assertTrue(stored.contains("\"executions\":[9]"), stored);
    DataScope.Acl union2 = dataScope.aclUnion(new net.zentao.platform.session.SessionPrincipal(accountId, "acl-user"));
    assertTrue(union2.products().isEmpty() && union2.projects().isEmpty(), "清空后并集应无残留");
  }
}
