package net.zentao.org;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import net.zentao.platform.rbac.DataScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 角色 API（T23 统一实体）：一个角色 = 权限码 + 成员 + 数据权限。
 *
 * <p>覆盖迁移结果（超管角色 id=1 + 旧岗位角色成为内置角色）、新建/改名/排序的校验与乐观锁、
 * 删除守卫与级联、权限矩阵整体替换与 /me 即时生效、成员差量、复制、数据权限写入与 DataScope 联动，
 * 以及账号侧 roleIds 的写路径（不存在的角色 id → 42201）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoleApiTest {

  @Value("${local.server.port}")
  int port;

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Autowired
  DataScope dataScope;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;
  private long roleId;

  @BeforeEach
  void seed() throws Exception {
    adminCookie = cookieOf(send("POST", "/api/v1/session",
        "{\"account\":\"admin\",\"password\":\"admin123\"}", null));
    roleId = createRole("研发组-" + System.nanoTime(), "测试角色");
  }

  private String cookieOf(HttpResponse<String> response) {
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private long createRole(String name, String description) throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/roles",
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

  private JsonNode items() throws Exception {
    HttpResponse<String> response = send("GET", "/api/v1/roles", null, adminCookie);
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data/items");
  }

  private static void assertFalse2(boolean condition, String message) {
    if (condition) {
      throw new AssertionError(message);
    }
  }

  @Test
  @DisplayName("迁移结果：超管角色 id=1 + 旧岗位角色成为内置角色（名字取自中文标签）")
  void migratedRoles() throws Exception {
    JsonNode roles = items();
    JsonNode superRole = null;
    List<String> builtinNames = new ArrayList<>();
    for (JsonNode role : roles) {
      if (role.get("id").asLong() == 1L) {
        superRole = role;
      }
      if (role.get("builtin").asBoolean()) {
        builtinNames.add(role.get("name").asText());
      }
    }
    assertTrue(superRole != null, "超管角色 id=1 必须存在：" + roles);
    assertEquals("管理员", superRole.get("name").asText());
    assertTrue(superRole.get("builtin").asBoolean(), "超管角色是内置角色");
    // 超管角色不落 role_priv 行（PrivilegeChecker 短路「全过」），但权限数按编目全集回答
    assertTrue(superRole.get("privilegeCount").asInt() > 100, "超管角色权限数 = 权限编目全集：" + superRole);
    assertTrue(builtinNames.containsAll(List.of("管理员", "研发", "测试", "项目经理")),
        "旧岗位角色应成为内置角色：" + builtinNames);
  }

  @Test
  @DisplayName("新建角色：name 必填且唯一、code 可选且合法唯一；重复/非法 → 42201")
  void createValidates() throws Exception {
    HttpResponse<String> duplicate = send("POST", "/api/v1/roles", "{\"name\":\"管理员\"}", adminCookie);
    assertEquals(422, duplicate.statusCode(), duplicate.body());
    assertTrue(duplicate.body().contains("42201"), duplicate.body());

    String code = "ops-" + System.nanoTime() % 100000;
    HttpResponse<String> created = send("POST", "/api/v1/roles",
        "{\"name\":\"运维-" + code + "\",\"code\":\"" + code + "\",\"description\":\"值班\"}", adminCookie);
    assertEquals(200, created.statusCode(), created.body());
    JsonNode role = json.readTree(created.body()).at("/data");
    assertEquals(code, role.get("code").asText());
    assertFalse(role.get("builtin").asBoolean(), "新建角色不是内置角色");
    assertEquals(0, role.get("privilegeCount").asInt());
    assertEquals(0, role.get("memberCount").asInt());

    assertEquals(422, send("POST", "/api/v1/roles",
        "{\"name\":\"另一个-" + code + "\",\"code\":\"" + code + "\"}", adminCookie).statusCode(), "code 唯一");
    assertEquals(422, send("POST", "/api/v1/roles",
        "{\"name\":\"大写码-" + code + "\",\"code\":\"Bad_Code\"}", adminCookie).statusCode(), "code 形态");
    assertEquals(422, send("POST", "/api/v1/roles", "{\"name\":\"  \"}", adminCookie).statusCode(), "name 必填");
  }

  @Test
  @DisplayName("改名/排序/数据权限：按 lockVersion 更新；陈旧版本 → 40901")
  void updateFieldsAndLock() throws Exception {
    HttpResponse<String> updated = send("PATCH", "/api/v1/roles/" + roleId,
        "{\"name\":\"改名组-" + roleId + "\",\"description\":\"改过\",\"sort\":5,\"lockVersion\":0}", adminCookie);
    assertEquals(200, updated.statusCode(), updated.body());
    JsonNode view = json.readTree(updated.body()).at("/data");
    assertEquals("改过", view.get("description").asText());
    assertEquals(5, view.get("sort").asInt());
    assertEquals(1, view.get("lockVersion").asInt());

    assertEquals(409, send("PATCH", "/api/v1/roles/" + roleId,
        "{\"description\":\"过期写入\",\"lockVersion\":0}", adminCookie).statusCode());
    assertEquals(409, send("PATCH", "/api/v1/roles/" + roleId,
        "{\"description\":\"缺版本\"}", adminCookie).statusCode());
  }

  @Test
  @DisplayName("T67/DB-18 审计四件落值：建角色落 created_by/created_at，改角色落 updated_by/updated_at")
  void auditFourColumnsLandOnCreateAndUpdate() throws Exception {
    java.util.Map<String, Object> created = jdbcTemplate.queryForMap(
        "SELECT created_by, created_at, updated_by, updated_at FROM role WHERE id = ?", roleId);
    assertEquals("admin", created.get("created_by"), "建角色须落 created_by");
    assertNotNull(created.get("created_at"), "建角色须落 created_at");
    assertNull(created.get("updated_by"), "未改过前不应有 updated_by");
    assertNull(created.get("updated_at"), "未改过前不应有 updated_at");

    HttpResponse<String> updated = send("PATCH", "/api/v1/roles/" + roleId,
        "{\"description\":\"审计四件\",\"lockVersion\":0}", adminCookie);
    assertEquals(200, updated.statusCode(), updated.body());
    java.util.Map<String, Object> after = jdbcTemplate.queryForMap(
        "SELECT updated_by, updated_at FROM role WHERE id = ?", roleId);
    assertEquals("admin", after.get("updated_by"), "改角色须落 updated_by");
    assertNotNull(after.get("updated_at"), "改角色须落 updated_at");
  }

  @Test
  @DisplayName("角色权限矩阵：整体替换；未注册码 42201；成员 /me 即时生效；移出角色即失效")
  void privilegesLifecycle() throws Exception {
    HttpResponse<String> put1 = send("PUT", "/api/v1/roles/" + roleId + "/privileges",
        "{\"codes\":[\"department-edit\",\"account-create\",\"account-view\"]}", adminCookie);
    assertEquals(200, put1.statusCode(), put1.body());
    HttpResponse<String> get1 = send("GET", "/api/v1/roles/" + roleId + "/privileges", null, adminCookie);
    assertTrue(get1.body().contains("department-edit") && !get1.body().contains("role-edit"), get1.body());

    send("PUT", "/api/v1/roles/" + roleId + "/privileges", "{\"codes\":[\"account-view\"]}", adminCookie);
    HttpResponse<String> get2 = send("GET", "/api/v1/roles/" + roleId + "/privileges", null, adminCookie);
    assertFalse2(get2.body().contains("department-edit"), "整体替换应移除未勾选码: " + get2.body());

    HttpResponse<String> unregistered = send("PUT", "/api/v1/roles/" + roleId + "/privileges",
        "{\"codes\":[\"department-edit\",\"not-a-code\"]}", adminCookie);
    assertEquals(422, unregistered.statusCode(), unregistered.body());
    assertTrue(unregistered.body().contains("42201"), unregistered.body());

    String user = "role-user-" + System.nanoTime() % 100000;
    HttpResponse<String> account = send("POST", "/api/v1/accounts",
        "{\"account\":\"" + user + "\",\"password\":\"secret123\",\"realName\":\"角色成员\",\"roleIds\":[" + roleId + "]}",
        adminCookie);
    assertEquals(200, account.statusCode(), account.body());
    String userCookie = cookieOf(send("POST", "/api/v1/session",
        "{\"account\":\"" + user + "\",\"password\":\"secret123\"}", null));
    HttpResponse<String> me = send("GET", "/api/v1/me", null, userCookie);
    assertTrue(me.body().contains("account-view"), "角色成员 /me 应含码: " + me.body());

    assertEquals(200, send("PUT", "/api/v1/roles/" + roleId + "/members", "{\"accountIds\":[]}", adminCookie).statusCode());
    HttpResponse<String> meAfter = send("GET", "/api/v1/me", null, userCookie);
    assertFalse2(meAfter.body().contains("account-view"), "移出角色即失效: " + meAfter.body());
  }

  @Test
  @DisplayName("成员写：不存在账号 42201；重复去重；账号挂不存在的角色 42201；DELETE 级联；内置角色 42203")
  void membersAndDeleteGuards() throws Exception {
    HttpResponse<String> account = send("POST", "/api/v1/accounts",
        "{\"account\":\"role-m2\",\"password\":\"secret123\",\"realName\":\"成员二\"}", adminCookie);
    long accountId = json.readTree(account.body()).at("/data/id").asLong();

    assertEquals(422, send("POST", "/api/v1/accounts",
        "{\"account\":\"role-bad\",\"password\":\"secret123\",\"realName\":\"错角色\",\"roleIds\":[999999]}",
        adminCookie).statusCode(), "账号侧：不存在的角色 id 挡在创建时");

    HttpResponse<String> dup = send("PUT", "/api/v1/roles/" + roleId + "/members",
        "{\"accountIds\":[" + accountId + "," + accountId + "]}", adminCookie);
    assertEquals(200, dup.statusCode(), dup.body());
    assertEquals(1, json.readTree(dup.body()).at("/data/total").asInt(), "重复应去重");

    HttpResponse<String> missing = send("PUT", "/api/v1/roles/" + roleId + "/members",
        "{\"accountIds\":[999999]}", adminCookie);
    assertEquals(422, missing.statusCode(), missing.body());

    send("PUT", "/api/v1/roles/" + roleId + "/privileges", "{\"codes\":[\"account-view\"]}", adminCookie);
    assertEquals(200, send("DELETE", "/api/v1/roles/" + roleId, null, adminCookie).statusCode());
    assertEquals(0, jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM user_role WHERE role_id = ?", Integer.class, roleId));
    assertEquals(0, jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM role_priv WHERE role_id = ?", Integer.class, roleId));

    HttpResponse<String> superRole = send("DELETE", "/api/v1/roles/1", null, adminCookie);
    assertEquals(422, superRole.statusCode(), superRole.body());
    assertTrue(superRole.body().contains("42203"), superRole.body());
    for (JsonNode role : items()) {
      if (role.get("builtin").asBoolean() && role.get("id").asLong() != 1L) {
        assertEquals(422, send("DELETE", "/api/v1/roles/" + role.get("id").asLong(), null, adminCookie).statusCode(),
            "内置角色不可删：" + role);
        break;
      }
    }
  }

  @Test
  @DisplayName("复制角色：copyPrivileges/copyMembers 各选项生效，源角色不变")
  void copyOptions() throws Exception {
    HttpResponse<String> account = send("POST", "/api/v1/accounts",
        "{\"account\":\"role-src\",\"password\":\"secret123\",\"realName\":\"源成员\"}", adminCookie);
    long accountId = json.readTree(account.body()).at("/data/id").asLong();
    send("PUT", "/api/v1/roles/" + roleId + "/privileges", "{\"codes\":[\"account-view\",\"department-view\"]}", adminCookie);
    send("PUT", "/api/v1/roles/" + roleId + "/members", "{\"accountIds\":[" + accountId + "]}", adminCookie);

    HttpResponse<String> copied = send("POST", "/api/v1/roles/" + roleId + "/copy",
        "{\"name\":\"研发组副本-" + roleId + "\",\"copyPrivileges\":true,\"copyMembers\":false}", adminCookie);
    assertEquals(200, copied.statusCode(), copied.body());
    JsonNode view = json.readTree(copied.body()).at("/data");
    assertEquals(2, view.get("privilegeCount").asInt(), "副本应含权限码");
    assertEquals(0, view.get("memberCount").asInt(), "副本不应含成员");

    HttpResponse<String> source = send("GET", "/api/v1/roles/" + roleId, null, adminCookie);
    assertEquals(1, json.readTree(source.body()).at("/data/memberCount").asInt(), "源角色成员不变");
  }

  @Test
  @DisplayName("数据权限：写入/整体替换/子键 null 清空；DataScope 并集读取联动")
  void aclWriteReplaceClearAndDataScope() throws Exception {
    HttpResponse<String> put1 = send("PATCH", "/api/v1/roles/" + roleId,
        "{\"acl\":{\"products\":[1,2],\"projects\":[3]},\"lockVersion\":0}", adminCookie);
    assertEquals(200, put1.statusCode(), put1.body());
    JsonNode acl1 = json.readTree(put1.body()).at("/data/acl");
    assertEquals("[1,2]", acl1.get("products").toString());
    assertEquals("[3]", acl1.get("projects").toString());
    assertEquals("[]", acl1.get("views").toString(), "null 子键应归一空表");
    assertEquals("[]", acl1.get("executions").toString());

    HttpResponse<String> account = send("POST", "/api/v1/accounts",
        "{\"account\":\"acl-user\",\"password\":\"secret123\",\"realName\":\"acl成员\",\"roleIds\":[" + roleId + "]}",
        adminCookie);
    long accountId = json.readTree(account.body()).at("/data/id").asLong();
    DataScope.Acl union = dataScope.aclUnion(new net.zentao.platform.session.SessionPrincipal(accountId, "acl-user"));
    assertEquals(List.of(1L, 2L), union.products());
    assertEquals(List.of(3L), union.projects());

    int lockVersion = json.readTree(put1.body()).at("/data/lockVersion").asInt();
    HttpResponse<String> put2 = send("PATCH", "/api/v1/roles/" + roleId,
        "{\"acl\":{\"views\":[7],\"products\":null,\"executions\":[9]},\"lockVersion\":" + lockVersion + "}",
        adminCookie);
    assertEquals(200, put2.statusCode(), put2.body());
    JsonNode acl2 = json.readTree(put2.body()).at("/data/acl");
    assertEquals("[7]", acl2.get("views").toString());
    assertEquals("[]", acl2.get("products").toString(), "子键 null = 清空该键");
    assertEquals("[]", acl2.get("projects").toString(), "整体替换应移除未传键");
    assertEquals("[9]", acl2.get("executions").toString());

    String stored = jdbcTemplate.queryForObject("SELECT acl FROM role WHERE id = ?", String.class, roleId);
    assertTrue(stored.contains("\"executions\":[9]"), stored);
    DataScope.Acl union2 = dataScope.aclUnion(new net.zentao.platform.session.SessionPrincipal(accountId, "acl-user"));
    assertTrue(union2.products().isEmpty() && union2.projects().isEmpty(), "清空后并集应无残留");
  }

  @Test
  @DisplayName("角色字典与 meta：条目形 {value,label}（角色 id + 名字），meta 只声明来源 roles")
  void dictionaryAndMeta() throws Exception {
    String name = "字典角色-" + System.nanoTime() % 100000;
    long id = createRole(name, "");

    JsonNode fields = json.readTree(send("GET", "/api/v1/meta/account", null, adminCookie).body())
        .at("/data/fields");
    JsonNode roleField = null;
    for (JsonNode field : fields) {
      if ("roleId".equals(field.get("key").asText())) {
        roleField = field;
      }
    }
    assertTrue(roleField != null, "meta 应含 roleId 字段：" + fields);
    assertEquals("roles", roleField.get("source").asText(), "meta 只声明选项来源，不内置清单");

    JsonNode dictItems = json.readTree(send("GET", "/api/v1/dicts/roles", null, adminCookie).body())
        .at("/data/items");
    boolean found = false;
    for (JsonNode item : dictItems) {
      if (item.get("value").asLong() == id) {
        found = true;
        assertEquals(name, item.get("label").asText(), "字典条目的 label 是角色名");
      }
    }
    assertTrue(found, "新建角色应出现在 roles 字典里：" + dictItems);
  }
}
