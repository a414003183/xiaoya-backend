package net.zentao.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T50：`/api/**` 默认拒绝匿名（SEC-02 / BE-04）。
 *
 * <p>三态：`@RequirePrivilege` 查码；`@Anonymous` 真匿名；**两者都没有 = 要求已认证会话**。
 * 本类守两件事：四个"handler 内不解析会话"的端点（路由表 / 字典 / 元数据 / 部门树）匿名不再 200；
 * 默认态是整片白名单生效而不是只补这四个洞（抽样三个裸端点）。
 *
 * <p>同时守 `@Anonymous` 这个唯一放行口：登录仍匿名可达（错体 42201 说明进了 handler，不是被拦截器拦下），
 * 且带会话访问四个端点仍是 200——本卡收的是"匿名"，不是"一刀切 401"。
 */
class AnonymousAccessTest extends ApiTestSupport {

  /** SEC-02 的四个裸奔端点（handler 内不 resolve 会话，此前靠"无注解即放行"匿名 200）。 */
  private static final List<String> NAKED = List.of(
      "/api/v1/menus/routes", "/api/v1/dicts/locales", "/api/v1/meta/account", "/api/v1/departments/tree");

  private void assertUnauthenticated(String path) throws Exception {
    HttpResponse<String> response = send("GET", path, null, null);
    assertEquals(401, response.statusCode(), path + " → " + response.body());
    assertEquals(40101, json.readTree(response.body()).at("/error/code").asInt(), path + " → " + response.body());
  }

  @Test
  @DisplayName("SEC-02 四端点匿名：401 + 40101（路由表/权限码目录/字段目录/部门树不再裸奔）")
  void nakedEndpointsAreClosed() throws Exception {
    // 逐个收集而不是首个失败即止：注入验证时要一眼看出**四个**端点都漏了
    List<String> leaked = new ArrayList<>();
    for (String path : NAKED) {
      HttpResponse<String> response = send("GET", path, null, null);
      int code = json.readTree(response.body()).at("/error/code").asInt();
      if (response.statusCode() != 401 || code != 40101) {
        leaked.add(path + " → " + response.statusCode() + " code=" + code);
      }
    }
    assertEquals(List.of(), leaked, "这些端点匿名可达（拦截器默认态没生效）：" + leaked);
  }

  @Test
  @DisplayName("默认态覆盖整片白名单：抽样的裸端点匿名同样 401")
  void whitelistedBareEndpointsRequireSessionToo() throws Exception {
    assertUnauthenticated("/api/v1/menus/my");
    assertUnauthenticated("/api/v1/notifications");
    assertUnauthenticated("/api/v1/search?q=t50");
  }

  @Test
  @DisplayName("登录仍匿名可达（@Anonymous 生效）：错体 42201、错口令 401 + 登录失败文案")
  void loginStaysAnonymous() throws Exception {
    HttpResponse<String> malformed = send("POST", "/api/v1/session", "{}", null);
    assertEquals(422, malformed.statusCode(), malformed.body());
    assertEquals(42201, json.readTree(malformed.body()).at("/error/code").asInt(), malformed.body());

    HttpResponse<String> wrongPassword = send("POST", "/api/v1/session",
        "{\"account\":\"nobody-t50\",\"password\":\"wrong\"}", null);
    assertEquals(401, wrongPassword.statusCode(), wrongPassword.body());
    // 拦截器的 40101 文案是「未登录或会话已过期。」，这里必须是登录失败文案才说明请求真的进了 handler
    assertEquals("账号或密码错误。", json.readTree(wrongPassword.body()).at("/error/message").asText(),
        wrongPassword.body());
  }

  @Test
  @DisplayName("畸形 query 不炸错误面：匿名 401 仍回 40101 信封（不是裸 50001）")
  void malformedQueryStillGetsErrorEnvelope() throws Exception {
    // 错误信封要解析请求语言（?lang=），畸形 query 会在取参数时抛 Tomcat InvalidParameterException——
    // 该异常若逃出 @ExceptionHandler，40101 就变 50001（T50 暴露、随本卡修复 RequestLocaleResolver）
    HttpResponse<String> response = send("GET", "/api/v1/comments?=null", null, null);
    assertEquals(401, response.statusCode(), response.body());
    assertEquals(40101, json.readTree(response.body()).at("/error/code").asInt(), response.body());
  }

  @Test
  @DisplayName("带会话访问四端点：200（收的是匿名，不是一刀切 401）")
  void withSessionTheyStillWork() throws Exception {
    String cookie = login("admin", "admin123");
    for (String path : NAKED) {
      HttpResponse<String> response = send("GET", path, null, cookie);
      assertEquals(200, response.statusCode(), path + " → " + response.body());
    }
  }
}
