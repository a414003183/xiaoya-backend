package net.zentao.platform.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** B1 §H3：登录与写操作各留一行审计，含操作人/动作/对象/IP/traceId。 */
class AuditLogTest extends ApiTestSupport {

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("登录 + 建产品 + 产品改名：三次写各落一行，account/action/objectId/traceId 齐全")
  void auditsLoginAndWrites() throws Exception {
    HttpResponse<String> login = send("POST", "/api/v1/session",
        "{\"account\":\"admin\",\"password\":\"admin123\"}", null);
    assertEquals(200, login.statusCode(), login.body());
    String cookie = login.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];

    HttpResponse<String> created = send("POST", "/api/v1/products",
        "{\"name\":\"审计产品-" + System.nanoTime() + "\",\"acl\":\"public\"}", cookie);
    long productId = dataId(created);

    HttpResponse<String> renamed = send("PATCH", "/api/v1/products/" + productId,
        "{\"name\":\"审计产品改名\",\"lockVersion\":0}", cookie);
    assertEquals(200, renamed.statusCode(), renamed.body());

    // 按 traceId 定位各自那一行：同 JVM 共享 H2，audit_log 里还有别的用例留下的写记录；
    // 「恰好一行」同时证明登录没被 AuditAspect 重复记账。
    Map<String, Object> loginRow = singleRow(trace(login));
    assertEquals("admin", loginRow.get("account"));
    assertEquals("login", loginRow.get("action"));
    assertNotNull(loginRow.get("ip"), "登录审计缺 IP");
    assertNotNull(loginRow.get("created_at"), "登录审计缺时间");

    Map<String, Object> createRow = singleRow(trace(created));
    assertEquals("admin", createRow.get("account"));
    assertEquals("post /api/v1/products", createRow.get("action"));

    Map<String, Object> updateRow = singleRow(trace(renamed));
    assertEquals("admin", updateRow.get("account"));
    assertEquals("patch /api/v1/products/{productId}", updateRow.get("action"));
    assertEquals("product", updateRow.get("object_type"), "对象类型应由路由变量 {productId} 推导");
    assertEquals(productId, ((Number) updateRow.get("object_id")).longValue());
  }

  @Test
  @DisplayName("@Audit 声明的业务动作名覆盖路由推导值：账号创建落 account-create / account")
  void annotatedEndpointUsesDeclaredAction() throws Exception {
    String cookie = login("admin", "admin123");
    HttpResponse<String> created = send("POST", "/api/v1/accounts",
        "{\"account\":\"audit-" + System.nanoTime() + "\",\"password\":\"secret123\",\"realName\":\"审计账号\"}", cookie);
    assertEquals(200, created.statusCode(), created.body());

    Map<String, Object> row = singleRow(trace(created));
    assertEquals("account-create", row.get("action"), "@Audit 的动作名应盖过 route 推导值");
    assertEquals("account", row.get("object_type"));
  }

  private static String trace(HttpResponse<String> response) {
    return response.headers().firstValue("X-Trace-Id").orElseThrow();
  }

  /** 该 traceId 的审计行，必须恰好一行。 */
  private Map<String, Object> singleRow(String traceId) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT account, action, object_type, object_id, ip, created_at FROM audit_log WHERE trace_id = ?",
        traceId);
    assertEquals(1, rows.size(), "traceId " + traceId + " 的审计行数应为 1：" + rows);
    return rows.get(0);
  }
}
