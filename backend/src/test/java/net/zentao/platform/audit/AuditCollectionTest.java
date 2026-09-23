package net.zentao.platform.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T10 审计分级采集：按 VISION §一.事项 4 的粒度表逐类取一条合规格样本（auth/perm/config/business/
 * batch/export/sensitive/approve；query 类由 {@link AuditFrameworkTest} 的聚合用例看护）。
 *
 * <p>同 JVM 共享 H2 库：每例按自己那次的 traceId 精确定位**那一行**，不赌全局行数。
 */
class AuditCollectionTest extends ApiTestSupport {

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("auth：登出记 category=auth + 动作名 logout，UA/设备/IP 齐全")
  void logoutIsAuthRow() throws Exception {
    String cookie = login("admin", "admin123");
    HttpResponse<String> logout = sendWithUserAgent("DELETE", "/api/v1/session", null, cookie,
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0");
    assertEquals(200, logout.statusCode(), logout.body());

    Map<String, Object> row = rowOf(traceId(logout));
    assertEquals("logout", row.get("action"), row.toString());
    assertEquals("auth", row.get("category"));
    assertEquals("success", row.get("result"));
    assertTrue(String.valueOf(row.get("ua")).contains("Chrome"), "登出要留 UA 原文：" + row);
    assertTrue(String.valueOf(row.get("device")).contains("Chrome"), "登出要有设备摘要：" + row);
    assertNotNull(row.get("ip"), row.toString());
  }

  @Test
  @DisplayName("perm：账号改名落字段级 diff（旧值/新值），改口令只记动作不记值")
  void permDiffAndPasswordAction() throws Exception {
    String cookie = login("admin", "admin123");
    String account = "audit-perm-" + System.nanoTime() % 100000000;
    long accountId = dataId(send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"审计旧名\"}", cookie));

    HttpResponse<String> renamed = send("PATCH", "/api/v1/accounts/" + accountId,
        "{\"realName\":\"审计新名\",\"lockVersion\":0}", cookie);
    assertEquals(200, renamed.statusCode(), renamed.body());
    Map<String, Object> updateRow = rowOf(traceId(renamed));
    assertEquals("account-update", updateRow.get("action"));
    assertEquals("perm", updateRow.get("category"));
    JsonNode changes = json.readTree((String) updateRow.get("changes"));
    JsonNode realName = field(changes, "realName");
    assertEquals("\"审计旧名\"", realName.get("before").asText(), changes.toString());
    assertEquals("\"审计新名\"", realName.get("after").asText(), changes.toString());

    // 口令动作走管理员重置（自助改密只许本人）；口令值永不进审计——快照里根本没有散列
    HttpResponse<String> password = send("POST", "/api/v1/accounts/" + accountId + "/reset-password",
        "{\"newPassword\":\"secret456\"}", cookie);
    assertEquals(200, password.statusCode(), password.body());
    Map<String, Object> passwordRow = rowOf(traceId(password));
    assertEquals("account-reset-password", passwordRow.get("action"));
    assertEquals("perm", passwordRow.get("category"));
    assertNull(passwordRow.get("changes"), "口令不进 diff（快照里根本没有散列）：" + passwordRow);
  }

  @Test
  @DisplayName("config：密钥类设置项落 diff，其值掩码成 ***")
  void configSecretValueIsMasked() throws Exception {
    String cookie = login("admin", "admin123");
    String key = "t10.mail.password" + System.nanoTime() % 100000000;
    assertEquals(200, send("POST", "/api/v1/setting-entries",
        "{\"key\":\"" + key + "\",\"value\":\"\\\"old-secret\\\"\"}", cookie).statusCode());

    HttpResponse<String> updated = send("PATCH", "/api/v1/setting-entries/" + key,
        "{\"value\":\"\\\"new-secret\\\"\"}", cookie);
    assertEquals(200, updated.statusCode(), updated.body());
    Map<String, Object> row = rowOf(traceId(updated));
    assertEquals("setting-entry-update", row.get("action"));
    assertEquals("config", row.get("category"));
    JsonNode changes = json.readTree((String) row.get("changes"));
    JsonNode secret = field(changes, key);
    // 掩码值按 T04 口径落成裸 `***`（不是 JSON 引号包一层的字符串）
    assertEquals("***", secret.get("before").asText(), "密钥类配置项的旧值必须掩码：" + changes);
    assertEquals("***", secret.get("after").asText(), "密钥类配置项的新值必须掩码：" + changes);
    assertFalse(((String) row.get("changes")).contains("old-secret"), "明文密钥不许进审计表：" + row);
  }

  @Test
  @DisplayName("business：产品改名落字段级 diff")
  void businessUpdateKeepsDiff() throws Exception {
    String cookie = login("admin", "admin123");
    long productId = createProduct(cookie, "审计业务产品-" + System.nanoTime() % 100000000);

    HttpResponse<String> renamed = send("PATCH", "/api/v1/products/" + productId,
        "{\"name\":\"审计业务产品改名\",\"lockVersion\":0}", cookie);
    assertEquals(200, renamed.statusCode(), renamed.body());
    Map<String, Object> row = rowOf(traceId(renamed));
    assertEquals("product-update", row.get("action"));
    assertEquals("business", row.get("category"));
    assertEquals("product", row.get("object_type"));
    assertEquals(productId, ((Number) row.get("object_id")).longValue());
    JsonNode changes = json.readTree((String) row.get("changes"));
    assertEquals("\"审计业务产品改名\"", field(changes, "name").get("after").asText(), changes.toString());
  }

  @Test
  @DisplayName("batch：批量建档记一行批次摘要（batch_id + 筛选条件/总数/成败数）")
  void batchSummaryRow() throws Exception {
    String cookie = login("admin", "admin123");
    String suffix = String.valueOf(System.nanoTime() % 100000000);
    HttpResponse<String> batch = send("POST", "/api/v1/accounts/batch",
        "{\"items\":["
            + "{\"account\":\"audit-b1-" + suffix + "\",\"password\":\"secret123\",\"realName\":\"批量一\"},"
            + "{\"account\":\"audit-b2-" + suffix + "\",\"password\":\"secret123\",\"realName\":\"批量二\"},"
            + "{\"account\":\"ab\",\"password\":\"secret123\",\"realName\":\"格式错\"}"
            + "]}", cookie);
    assertEquals(200, batch.statusCode(), batch.body());

    Map<String, Object> row = rowOf(traceId(batch));
    assertEquals("batch-operation", row.get("action"));
    assertEquals("batch", row.get("category"));
    assertNotNull(row.get("batch_id"), "批量行要有批次号：" + row);
    JsonNode extra = json.readTree((String) row.get("extra"));
    assertEquals(3, extra.get("total").asInt(), extra.toString());
    assertEquals(2, extra.get("succeeded").asInt(), extra.toString());
    assertEquals(1, extra.get("failed").asInt(), extra.toString());
    assertEquals(1, extra.get("failures").size(), "失败逐条摘要要进 extra：" + extra);
  }

  @Test
  @DisplayName("export：CSV 导出记筛选条件/字段/条数/文件哈希/下载 IP")
  void exportCsvRow() throws Exception {
    String cookie = login("admin", "admin123");
    String marker = "审计导出-" + System.nanoTime() % 100000000;
    createProduct(cookie, marker);

    HttpResponse<String> exported = send("GET",
        "/api/v1/products?format=csv&filters%5BcreatedBy%5D=admin", null, cookie);
    assertEquals(200, exported.statusCode(), exported.body());
    assertTrue(exported.body().contains(marker), "导出的 CSV 里应有刚建的产品：" + exported.body());

    Map<String, Object> row = rowOf(traceId(exported));
    assertEquals("export-csv", row.get("action"));
    assertEquals("export", row.get("category"));
    assertNotNull(row.get("ip"), row.toString());
    JsonNode extra = json.readTree((String) row.get("extra"));
    assertTrue(extra.get("filters").has("filters[createdBy]"), "筛选条件要进 extra：" + extra);
    assertFalse(extra.get("fields").isEmpty(), "导出字段（CSV 表头）要进 extra：" + extra);
    assertTrue(extra.get("rows").asInt() >= 1, extra.toString());
    assertEquals(64, extra.get("sha256").asText().length(), "文件哈希应为 sha256 十六进制：" + extra);
  }

  @Test
  @DisplayName("sensitive：账号详情记敏感字段清单与（可选）原因")
  void sensitiveReadRow() throws Exception {
    String cookie = login("admin", "admin123");
    long accountId = dataId(send("POST", "/api/v1/accounts",
        "{\"account\":\"audit-sens-" + System.nanoTime() % 100000000 + "\",\"password\":\"secret123\","
            + "\"realName\":\"敏感读目标\",\"mobile\":\"13800000000\"}", cookie));

    HttpResponse<String> detail = send("GET", "/api/v1/accounts/" + accountId + "?reason="
        + java.net.URLEncoder.encode("核验联系方式", java.nio.charset.StandardCharsets.UTF_8), null, cookie);
    assertEquals(200, detail.statusCode(), detail.body());

    Map<String, Object> row = rowOf(traceId(detail));
    assertEquals("sensitive-view", row.get("action"));
    assertEquals("sensitive", row.get("category"));
    assertEquals("account", row.get("object_type"));
    assertEquals(accountId, ((Number) row.get("object_id")).longValue());
    JsonNode extra = json.readTree((String) row.get("extra"));
    assertTrue(extra.get("fields").toString().contains("mobile"), extra.toString());
    assertEquals("核验联系方式", extra.get("reason").asText(), extra.toString());
  }

  @Test
  @DisplayName("approve：文档发布写 category=approve + 前后整快照")
  void approvePublishSnapshot() throws Exception {
    String cookie = login("admin", "admin123");
    String unique = "u" + System.nanoTime() % 100000000;
    long spaceId = dataId(send("POST", "/api/v1/doc-spaces",
        "{\"name\":\"审计发布库-" + unique + "\",\"type\":\"custom\"}", cookie));
    long docId = dataId(send("POST", "/api/v1/doc-spaces/" + spaceId + "/docs",
        "{\"title\":\"审计发布文档\",\"content\":\"首版正文\"}", cookie));

    HttpResponse<String> published = send("POST", "/api/v1/docs/" + docId + "/publish", "{}", cookie);
    assertEquals(200, published.statusCode(), published.body());
    Map<String, Object> row = rowOf(traceId(published));
    assertEquals("doc-publish", row.get("action"));
    assertEquals("approve", row.get("category"));
    assertEquals("doc", row.get("object_type"));
    assertEquals(docId, ((Number) row.get("object_id")).longValue());
    JsonNode snapshot = json.readTree((String) row.get("snapshot"));
    assertEquals("draft", snapshot.at("/before/status").asText(), "整快照要含迁移前状态：" + snapshot);
    assertEquals("published", snapshot.at("/after/status").asText(), "整快照要含迁移后状态：" + snapshot);
  }

  @Test
  @DisplayName("denied：权限拒绝留痕（拦截器显式记账，处理器没执行到）")
  void deniedAttemptIsRecorded() throws Exception {
    String admin = login("admin", "admin123");
    String account = "audit-denied-" + System.nanoTime() % 100000000;
    accountWithPrivileges(admin, account, "\"file-upload\"");
    String cookie = login(account, "secret123");

    HttpResponse<String> refused = send("GET", "/api/v1/accounts", null, cookie);
    assertEquals(403, refused.statusCode(), refused.body());

    Map<String, Object> row = rowOf(traceId(refused));
    assertEquals("access-denied", row.get("action"));
    assertEquals("perm", row.get("category"));
    assertEquals("denied", row.get("result"));
    assertEquals(account, row.get("account"));
    assertNotNull(row.get("reason"), row.toString());
    assertTrue(String.valueOf(row.get("detail")).contains("account-view"), "detail 要写清缺哪个权限码：" + row);
  }

  /** 带 UA 的请求（登录/登出这类 auth 行要看 UA 与设备摘要，默认客户端不发这个头）。 */
  private HttpResponse<String> sendWithUserAgent(String method, String path, String body, String cookie, String ua)
      throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Content-Type", "application/json")
        .header("User-Agent", ua);
    if (cookie != null) {
      builder.header("Cookie", cookie);
    }
    return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
        : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String traceId(HttpResponse<String> response) {
    return response.headers().firstValue("X-Trace-Id").orElseThrow();
  }

  private Map<String, Object> rowOf(String traceId) {
    return jdbcTemplate.queryForMap("SELECT * FROM audit_log WHERE trace_id = ?", traceId);
  }

  private static JsonNode field(JsonNode changes, String name) {
    for (JsonNode change : changes) {
      if (name.equals(change.get("field").asText())) {
        return change;
      }
    }
    throw new AssertionError("changes 里没有字段 " + name + "：" + changes);
  }
}
