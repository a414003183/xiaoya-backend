package net.zentao.org;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;

/**
 * 账号角色字典 API（org 卡 §3.4；旧禅道「后台→自定义→用户→角色列表」的等价能力）：
 * 内置角色九项/seeds 双语名、新建（code 校验与唯一）、改名与排序（乐观锁）、删除守卫（内置/占用）、
 * 账号写路径的角色码校验、meta 选项随字典实时变化。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoleApiTest {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;

  @BeforeEach
  void seed() throws Exception {
    adminCookie = cookieOf(send("POST", "/api/v1/session",
        "{\"account\":\"admin\",\"password\":\"admin123\"}", null));
  }

  private String cookieOf(HttpResponse<String> response) {
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private HttpResponse<String> send(String method, String path, String body, String cookie) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("Content-Type", "application/json")
        .header("X-Requested-With", "fetch");
    if (cookie != null) {
      builder.header("Cookie", cookie);
    }
    return http.send(builder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode data(HttpResponse<String> response) throws Exception {
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).get("data");
  }

  private List<String> codes() throws Exception {
    JsonNode items = data(send("GET", "/api/v1/roles", null, adminCookie)).get("items");
    List<String> codes = new ArrayList<>();
    items.forEach(item -> codes.add(item.get("code").asText()));
    return codes;
  }

  @Test
  @DisplayName("内置九角色随迁移种入，labels 双语且按 sort 升序")
  void builtinSeeded() throws Exception {
    JsonNode items = data(send("GET", "/api/v1/roles", null, adminCookie)).get("items");
    List<String> builtinCodes = new ArrayList<>();
    for (JsonNode item : items) {
      if (item.get("builtin").asBoolean()) {
        builtinCodes.add(item.get("code").asText());
      }
    }
    assertEquals(List.of("dev", "qa", "pm", "po", "td", "pd", "qd", "top", "others"), builtinCodes);
    JsonNode dev = items.get(0);
    assertEquals("研发", dev.get("labels").get("zh-CN").asText());
    assertEquals("Developer", dev.get("labels").get("en").asText());
    assertTrue(dev.get("builtin").asBoolean());
    assertEquals(10, dev.get("sort").asInt());
  }

  @Test
  @DisplayName("新建角色：code 合法且唯一，labels 至少一项；非法 code/重复/空 labels → 42201")
  void createValidates() throws Exception {
    String code = "ops-" + System.nanoTime() % 100000;
    HttpResponse<String> created = send("POST", "/api/v1/roles",
        "{\"code\":\"" + code + "\",\"labels\":{\"zh-CN\":\"运维\",\"en\":\"Ops\"}}", adminCookie);
    assertEquals(200, created.statusCode(), created.body());
    assertEquals("运维", data(created).get("labels").get("zh-CN").asText());
    assertFalse(data(created).get("builtin").asBoolean());
    assertTrue(codes().contains(code));

    assertEquals(422, send("POST", "/api/v1/roles",
        "{\"code\":\"" + code + "\",\"labels\":{\"zh-CN\":\"重复\"}}", adminCookie).statusCode());
    assertEquals(422, send("POST", "/api/v1/roles",
        "{\"code\":\"Bad_Code\",\"labels\":{\"zh-CN\":\"大写\"}}", adminCookie).statusCode());
    assertEquals(422, send("POST", "/api/v1/roles",
        "{\"code\":\"blanklabels\",\"labels\":{\"zh-CN\":\"   \"}}", adminCookie).statusCode());
  }

  @Test
  @DisplayName("改名与排序：按语言改名、排序位生效；陈旧 lockVersion → 40901")
  void updateLabelsAndSort() throws Exception {
    String code = "qa-" + System.nanoTime() % 100000;
    data(send("POST", "/api/v1/roles",
        "{\"code\":\"" + code + "\",\"labels\":{\"zh-CN\":\"质量\",\"en\":\"Quality\"}}", adminCookie));

    JsonNode updated = data(send("PATCH", "/api/v1/roles/" + code,
        "{\"labels\":{\"zh-CN\":\"质量保障\"},\"sort\":5,\"lockVersion\":0}", adminCookie));
    assertEquals("质量保障", updated.get("labels").get("zh-CN").asText());
    assertEquals(5, updated.get("sort").asInt());
    assertEquals(1, updated.get("lockVersion").asInt());

    // 陈旧版本 → 40901（PATCH 必带当前 lockVersion，3 §1 口径同权限组/账号）
    assertEquals(409, send("PATCH", "/api/v1/roles/" + code,
        "{\"labels\":{\"zh-CN\":\"过期写入\"},\"lockVersion\":0}", adminCookie).statusCode());
    assertEquals(409, send("PATCH", "/api/v1/roles/" + code,
        "{\"labels\":{\"zh-CN\":\"缺版本\"}}", adminCookie).statusCode());
  }

  @Test
  @DisplayName("删除守卫：内置角色 42203；被账号占用 42203；未占用自定义角色可删")
  void deleteGuards() throws Exception {
    assertEquals(422, send("DELETE", "/api/v1/roles/dev", null, adminCookie).statusCode());

    String code = "temp-" + System.nanoTime() % 100000;
    data(send("POST", "/api/v1/roles", "{\"code\":\"" + code + "\",\"labels\":{\"zh-CN\":\"临时\"}}", adminCookie));
    String account = "roletest" + System.nanoTime() % 100000;
    data(send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"角色测试\",\"role\":\"" + code + "\"}",
        adminCookie));

    assertEquals(422, send("DELETE", "/api/v1/roles/" + code, null, adminCookie).statusCode());
    JsonNode rows = data(send("GET", "/api/v1/roles", null, adminCookie)).get("items");
    long used = 0;
    for (JsonNode row : rows) {
      if (code.equals(row.get("code").asText())) {
        used = row.get("accountCount").asLong();
      }
    }
    assertEquals(1, used, "accountCount 应统计使用该角色的账号数");

    // 账号改派到内置角色后即可删除（PATCH 的 null 语义是"不修改"（03 §1），故不能靠 role:null 清空）
    JsonNode accountView = data(send("GET", "/api/v1/accounts?q=" + account, null, adminCookie)).get("items").get(0);
    long accountId = accountView.get("id").asLong();
    data(send("PATCH", "/api/v1/accounts/" + accountId,
        "{\"role\":\"dev\",\"lockVersion\":" + accountView.get("lockVersion").asInt() + "}", adminCookie));
    assertEquals(200, send("DELETE", "/api/v1/roles/" + code, null, adminCookie).statusCode());
    assertFalse(codes().contains(code));
  }

  @Test
  @DisplayName("账号写路径按字典校验角色码：未知码 42201，字典内新增的码可用")
  void accountRoleValidatedAgainstDictionary() throws Exception {
    String account = "rolechk" + System.nanoTime() % 100000;
    assertEquals(422, send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"角色校验\",\"role\":\"nosuchrole\"}",
        adminCookie).statusCode());

    String code = "sales-" + System.nanoTime() % 100000;
    data(send("POST", "/api/v1/roles", "{\"code\":\"" + code + "\",\"labels\":{\"zh-CN\":\"销售\"}}", adminCookie));
    JsonNode created = data(send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"角色校验\",\"role\":\"" + code + "\"}",
        adminCookie));
    assertEquals(code, created.get("role").asText());
  }

  @Test
  @DisplayName("role 字段选项走字典：meta 声明 source=roles，字典端点含新建角色（不再内置清单）")
  void metaOptionsFollowDictionary() throws Exception {
    String code = "meta-" + System.nanoTime() % 100000;
    data(send("POST", "/api/v1/roles", "{\"code\":\"" + code + "\",\"labels\":{\"zh-CN\":\"元数据角色\"}}", adminCookie));

    JsonNode fields = data(send("GET", "/api/v1/meta/account", null, adminCookie)).get("fields");
    JsonNode roleField = null;
    for (JsonNode field : fields) {
      if ("role".equals(field.get("key").asText())) {
        roleField = field;
      }
    }
    assertTrue(roleField != null, "meta 应含 role 字段");
    assertEquals("roles", roleField.get("source").asText(), "meta 只声明选项来源，不再内置清单");

    JsonNode dictItems = data(send("GET", "/api/v1/dicts/roles", null, adminCookie)).get("items");
    List<String> dictCodes = new ArrayList<>();
    for (JsonNode item : dictItems) {
      dictCodes.add(item.get("code").asText());
      if (code.equals(item.get("code").asText())) {
        assertEquals("元数据角色", item.get("labels").get("zh-CN").asText());
      }
    }
    assertTrue(dictCodes.contains(code), "新建角色应出现在 roles 字典里：" + dictCodes);
    assertTrue(dictCodes.contains("dev"), "内置角色仍在：" + dictCodes);
  }
}
