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
import net.zentao.platform.activity.ActivityRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 账号 CRUD/批量/动态流（org 卡 §8）：重复 42201、批量逐项、PATCH 40901/不可改 account、@myDepartment、meta 同源。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountApiTest {

  @Value("${local.server.port}")
  int port;

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Autowired
  ActivityRecorder activityRecorder;


  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String cookie;

  @BeforeEach
  void login() throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/session",
        "{\"account\":\"admin\",\"password\":\"admin123\"}", null, null);
    assertEquals(200, response.statusCode(), response.body());
    cookie = response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private HttpResponse<String> send(String method, String path, String body, String cookie, String put) throws Exception {
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
  @DisplayName("创建→详情→PATCH→重复 42201→@myDepartment→activities→批量→meta 同源")
  void accountLifecycle() throws Exception {
    // 创建
    HttpResponse<String> created = send("POST", "/api/v1/accounts",
        "{\"account\":\"alice\",\"password\":\"secret123\",\"realName\":\"爱丽丝\",\"roleIds\":[2]}",
        cookie, null);
    assertEquals(200, created.statusCode(), created.body());
    long aliceId = json.readTree(created.body()).at("/data/id").asLong();
    assertFalse(created.body().contains("secret123"), "password 不得回显");

    // 重复创建 → 42201 fields.account
    HttpResponse<String> duplicate = send("POST", "/api/v1/accounts",
        "{\"account\":\"alice\",\"password\":\"secret123\",\"realName\":\"爱丽丝2\"}", cookie, null);
    assertEquals(422, duplicate.statusCode(), duplicate.body());
    assertTrue(duplicate.body().contains("42201") && duplicate.body().contains("account"), duplicate.body());

    // 详情含 roleIds
    HttpResponse<String> detail = send("GET", "/api/v1/accounts/" + aliceId, null, cookie, null);
    assertTrue(detail.body().contains("\"roleIds\":[2]"), detail.body());

    // PATCH 改 realName + roleIds 全量替换
    HttpResponse<String> patched = send("PATCH", "/api/v1/accounts/" + aliceId,
        "{\"realName\":\"爱丽丝王\",\"roleIds\":[],\"lockVersion\":0}", cookie, null);
    assertEquals(200, patched.statusCode(), patched.body());
    assertTrue(patched.body().contains("爱丽丝王"), patched.body());
    assertTrue(patched.body().contains("\"roleIds\":[]"), patched.body());

    // lockVersion 不符 → 40901
    HttpResponse<String> conflict = send("PATCH", "/api/v1/accounts/" + aliceId,
        "{\"realName\":\"再改\",\"lockVersion\":0}", cookie, null);
    assertEquals(409, conflict.statusCode(), conflict.body());
    assertTrue(conflict.body().contains("40901"), conflict.body());

    // account 字段入更新体 → 42201
    HttpResponse<String> readonly = send("PATCH", "/api/v1/accounts/" + aliceId,
        "{\"account\":\"newname\",\"lockVersion\":1}", cookie, null);
    assertEquals(422, readonly.statusCode(), readonly.body());
    assertTrue(readonly.body().contains("42201"), readonly.body());

    // 动态流：created 已记录（PATCH 不记）
    HttpResponse<String> activities = send("GET", "/api/v1/accounts/" + aliceId + "/activities?limit=10", null, cookie, null);
    assertEquals(200, activities.statusCode(), activities.body());
    assertTrue(activities.body().contains("created"), activities.body());
    assertTrue(activities.body().contains("hasMore"), activities.body());

    // @myDepartment 展开：建部门，把 alice 挂进去，再用挂同部门的账号过滤
    HttpResponse<String> departmentResponse = send("POST", "/api/v1/departments", "{\"name\":\"研发部-my\"}", cookie, null);
    long deptId = json.readTree(departmentResponse.body()).at("/data/id").asLong();
    send("PATCH", "/api/v1/accounts/" + aliceId, "{\"departmentId\":" + deptId + ",\"lockVersion\":1}", cookie, null);
    HttpResponse<String> me = send("GET", "/api/v1/me", null, cookie, null);
    // admin 无部门 → @myDepartment 应为空集
    HttpResponse<String> myDept = send("GET", "/api/v1/accounts?filters%5BdepartmentId%5D=@myDepartment", null, cookie, null);
    assertEquals(200, myDept.statusCode(), myDept.body());
    assertTrue(myDept.body().contains("\"total\":0"), "admin 无部门 → 空集: " + myDept.body());

    // q 搜索命中 realName
    HttpResponse<String> search = send("GET", "/api/v1/accounts?q=%E7%88%B1%E4%B8%BD%E4%B8%9D", null, cookie, null);
    assertTrue(search.body().contains("alice"), search.body());
  }

  @Test
  @DisplayName("批量创建：部分成功逐项 {index,ok,id,error}；格式错 42201")
  void batchCreatePartialSuccess() throws Exception {
    send("POST", "/api/v1/accounts",
        "{\"account\":\"batch-dup\",\"password\":\"secret123\",\"realName\":\"批量一\"}", cookie, null);
    HttpResponse<String> batch = send("POST", "/api/v1/accounts/batch",
        "{\"items\":["
            + "{\"account\":\"batch-ok1\",\"password\":\"secret123\",\"realName\":\"批量A\"},"
            + "{\"account\":\"batch-dup\",\"password\":\"secret123\",\"realName\":\"重复\"},"
            + "{\"account\":\"ab\",\"password\":\"secret123\",\"realName\":\"格式错\"}"
            + "]}", cookie, null);
    assertEquals(200, batch.statusCode(), batch.body());
    JsonNode results = json.readTree(batch.body()).at("/data/results");
    assertEquals(3, results.size());
    assertEquals(true, results.get(0).get("ok").asBoolean());
    assertEquals(false, results.get(1).get("ok").asBoolean());
    assertTrue(results.get(1).get("error").asText().contains("42201"));
    assertEquals(false, results.get(2).get("ok").asBoolean());
  }

  @Test
  @DisplayName("meta：actions[].allowedStatus 与 workflow/account.yml 同源")
  void metaActionsFromWorkflow() throws Exception {
    HttpResponse<String> meta = send("GET", "/api/v1/meta/account", null, cookie, null);
    assertEquals(200, meta.statusCode(), meta.body());
    JsonNode actions = json.readTree(meta.body()).at("/data/actions");
    boolean disableForActiveOnly = false;
    for (JsonNode action : actions) {
      if ("disable".equals(action.get("action").asText())) {
        disableForActiveOnly = action.get("allowedStatus").toString().contains("active")
            && !action.get("allowedStatus").toString().contains("disabled");
      }
    }
    assertTrue(disableForActiveOnly, "disable 应只允许 active: " + actions);
    assertTrue(meta.body().contains("account-disable"), meta.body());
  }
}
