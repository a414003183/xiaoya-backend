package net.zentao.platform.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.util.Set;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 操作日志列表 API（B1 §H3 读侧）：权限门（无 audit-log-view → 40301）、DSL 白名单（未注册字段 → 40001）、
 * filters 等值/逗号 IN 与 q 关键词。同 JVM 共享 H2 库，故不断言全局行数——按 objectId/traceId 精确定位。
 */
class AuditLogQueryTest extends ApiTestSupport {

  @Test
  @DisplayName("admin 列表：写操作那行按 objectType/objectId 定位，account/action/traceId/createdAt 齐全")
  void adminListsWriteRow() throws Exception {
    String cookie = login("admin", "admin123");
    long productId = createProduct(cookie, "审计查询产品-" + System.nanoTime());
    HttpResponse<String> renamed = send("PATCH", "/api/v1/products/" + productId,
        "{\"name\":\"审计查询产品改名\",\"lockVersion\":0}", cookie);
    assertEquals(200, renamed.statusCode(), renamed.body());
    String traceId = renamed.headers().firstValue("X-Trace-Id").orElseThrow();

    JsonNode list = data(send("GET",
        "/api/v1/audit-logs?filters%5BobjectType%5D=product&filters%5BobjectId%5D=" + productId, null, cookie));
    assertEquals(1, list.get("total").asInt(), "产品 id 全局唯一，故该对象只剩这一行：" + list);
    JsonNode row = list.get("items").get(0);
    assertEquals(traceId, row.get("traceId").asText(), "行应与响应的 X-Trace-Id 对得上");
    assertEquals("admin", row.get("account").asText());
    assertEquals("product-update", row.get("action").asText());
    assertEquals("product", row.get("objectType").asText());
    assertEquals(productId, row.get("objectId").asLong());
    assertFalse(row.get("ip").isNull(), "写请求应记来源 IP：" + list);
    assertTrue(row.get("createdAt").asText().length() > 0, "createdAt 应为 ISO 时刻：" + list);
    assertTrue(row.get("detail").asText().contains("/api/v1/products/" + productId), list.toString());
  }

  @Test
  @DisplayName("无 audit-log-view 的账号：40301（登录 40101 由同一拦截器判定）")
  void privilegeGate() throws Exception {
    String admin = login("admin", "admin123");
    String cookie =
        accountWithPrivileges(admin, "audit-nopriv-" + (System.nanoTime() % 100000000), "\"file-upload\"");

    HttpResponse<String> forbidden = send("GET", "/api/v1/audit-logs", null, cookie);
    assertEquals(403, forbidden.statusCode(), forbidden.body());
    assertTrue(forbidden.body().contains("40301"), forbidden.body());

    HttpResponse<String> anonymous = send("GET", "/api/v1/audit-logs", null, null);
    assertEquals(401, anonymous.statusCode(), anonymous.body());
    assertTrue(anonymous.body().contains("40101"), anonymous.body());
  }

  @Test
  @DisplayName("未注册的过滤/排序字段：40001（白名单外注入防线）")
  void unregisteredFieldsRejected() throws Exception {
    String cookie = login("admin", "admin123");

    HttpResponse<String> unknownFilter = send("GET", "/api/v1/audit-logs?filters%5Bdetail%5D=x", null, cookie);
    assertEquals(400, unknownFilter.statusCode(), unknownFilter.body());
    assertTrue(unknownFilter.body().contains("40001"), unknownFilter.body());

    HttpResponse<String> unknownSort = send("GET", "/api/v1/audit-logs?sort=ip", null, cookie);
    assertEquals(400, unknownSort.statusCode(), unknownSort.body());
    assertTrue(unknownSort.body().contains("40001"), unknownSort.body());
  }

  @Test
  @DisplayName("filters[action]：等值 login 只回登录行（含本次登录），逗号 IN 是超集")
  void actionFilterEqAndIn() throws Exception {
    String cookie = login("admin", "admin123");

    JsonNode eq = data(send("GET", "/api/v1/audit-logs?filters%5Baction%5D=login&limit=200", null, cookie));
    assertTrue(eq.get("total").asInt() >= 1, "本次登录必留一行：" + eq);
    boolean sawAdmin = false;
    for (JsonNode item : eq.get("items")) {
      assertEquals("login", item.get("action").asText(), eq.toString());
      sawAdmin |= "admin".equals(item.get("account").asText());
    }
    assertTrue(sawAdmin, "本次登录行应在结果里：" + eq);

    JsonNode in = data(send("GET", "/api/v1/audit-logs?filters%5Baction%5D=login,login-failed&limit=200",
        null, cookie));
    assertTrue(in.get("total").asInt() >= eq.get("total").asInt(), "逗号 IN 应含等值结果：" + in);
    for (JsonNode item : in.get("items")) {
      assertTrue(Set.of("login", "login-failed").contains(item.get("action").asText()), in.toString());
    }

    // 失败登录也留痕（login-failed）：逗号 IN 一次取两类，登录日志页按此组合查询
    String nobody = "audit-nouser-" + (System.nanoTime() % 100000000);
    HttpResponse<String> failed = send("POST", "/api/v1/session",
        "{\"account\":\"" + nobody + "\",\"password\":\"secret123\"}", null);
    assertEquals(401, failed.statusCode(), failed.body());
    JsonNode twoKinds = data(send("GET",
        "/api/v1/audit-logs?filters%5Baction%5D=login,login-failed&q=" + nobody, null, cookie));
    assertEquals(1, twoKinds.get("total").asInt(), twoKinds.toString());
    assertEquals("login-failed", twoKinds.get("items").get(0).get("action").asText(), twoKinds.toString());
    assertEquals(nobody, twoKinds.get("items").get(0).get("account").asText(), twoKinds.toString());
  }

  @Test
  @DisplayName("q 关键词：LIKE account/action（命中行两列至少一列包含关键词）")
  void keywordSearchesAccountAndAction() throws Exception {
    String cookie = login("admin", "admin123");

    JsonNode list = data(send("GET", "/api/v1/audit-logs?q=admin&limit=200", null, cookie));
    assertTrue(list.get("total").asInt() >= 1, "admin 的登录行应在：" + list);
    for (JsonNode item : list.get("items")) {
      String account = item.get("account").isNull() ? "" : item.get("account").asText();
      assertTrue(account.contains("admin") || item.get("action").asText().contains("admin"), list.toString());
    }
  }
}
