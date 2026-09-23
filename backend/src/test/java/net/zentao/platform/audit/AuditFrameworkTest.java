package net.zentao.platform.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import net.zentao.ApiTestSupport;
import net.zentao.platform.meta.SettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * T04 审计 2.0 框架（ADR-004）：哈希链、失败行、diff 掩码、聚合采样、保留策略、详情与校验端点。
 *
 * <p>同 JVM 共享 H2 库：断言一律按 traceId/objectId/唯一动作名精确定位，不赌全局行数；
 * 篡改用例改完**立刻还原**（改回原值后链重新自洽），不影响其他用例。
 */
class AuditFrameworkTest extends ApiTestSupport {

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Autowired
  AuditQueryStatAccumulator accumulator;

  @Autowired
  AuditQueryStatRepository statRepository;

  @Autowired
  AuditCleanupJob cleanupJob;

  @Autowired
  SettingRepository settings;

  @Autowired
  AuditSnapshotRegistry snapshots;

  @Autowired
  JsonMapper jsonMapper;

  @Test
  @DisplayName("哈希链：相邻两行的 prev_hash/hash 首尾相接，verify 有效，detail 带 changes/hash 两列")
  void chainLinksAndVerifies() throws Exception {
    String cookie = login("admin", "admin123");
    long productId = createProduct(cookie, "审计链产品-" + System.nanoTime());
    HttpResponse<String> renamed = send("PATCH", "/api/v1/products/" + productId,
        "{\"name\":\"审计链改名\",\"lockVersion\":0}", cookie);
    assertEquals(200, renamed.statusCode(), renamed.body());

    String traceId = renamed.headers().firstValue("X-Trace-Id").orElseThrow();
    Map<String, Object> row = jdbcTemplate.queryForMap(
        "SELECT id, prev_hash, hash, category, result FROM audit_log WHERE trace_id = ?", traceId);
    long id = ((Number) row.get("id")).longValue();
    String prevHash = (String) row.get("prev_hash");
    String hash = (String) row.get("hash");
    assertNotNull(hash, "T04 起每行都要落链哈希：" + row);
    assertEquals("business", row.get("category"), "未登记动作回落 business");
    assertEquals("success", row.get("result"));

    // 上一行（更早的写）的 hash 必须等于本行的 prev_hash——链就是这样接起来的
    List<Map<String, Object>> previous = jdbcTemplate.queryForList(
        "SELECT hash FROM audit_log WHERE id < ? AND hash IS NOT NULL ORDER BY id DESC LIMIT 1", id);
    assertEquals(previous.get(0).get("hash"), prevHash, "本行 prev_hash 应等于上一有哈希行的 hash");

    JsonNode verified = data(send("GET", "/api/v1/audit-logs/verify?fromId=" + id + "&toId=" + id, null, cookie));
    assertTrue(verified.get("valid").asBoolean(), verified.toString());
    assertEquals(1, verified.get("scanned").asInt(), verified.toString());
    assertTrue(verified.get("unhashedPrefix").asLong() >= 0, verified.toString());
    assertEquals(id, verified.get("checkedFrom").asLong());

    JsonNode detail = data(send("GET", "/api/v1/audit-logs/" + id, null, cookie));
    assertEquals(id, detail.get("id").asLong());
    assertEquals(hash, detail.get("hash").asText());
    assertEquals(prevHash, detail.get("prevHash").asText());
    for (String field : List.of("changes", "snapshot", "extra")) {
      assertTrue(detail.has(field), "详情应带 " + field + "：" + detail);
    }
  }

  @Test
  @DisplayName("防篡改：改一行的内容后 verify 精确指向该行（改回即复原）")
  void verifyDetectsTamperedRow() throws Exception {
    String cookie = login("admin", "admin123");
    long id = jdbcTemplate.queryForObject(
        "SELECT MAX(id) FROM audit_log WHERE hash IS NOT NULL", Long.class);
    String original = jdbcTemplate.queryForObject("SELECT detail FROM audit_log WHERE id = ?", String.class, id);

    jdbcTemplate.update("UPDATE audit_log SET detail = ? WHERE id = ?", "T04 篡改探针", id);
    JsonNode broken = data(send("GET", "/api/v1/audit-logs/verify?fromId=" + id + "&toId=" + id, null, cookie));
    assertFalse(broken.get("valid").asBoolean(), "内容被改后哈希必须对不上：" + broken);
    assertEquals(id, broken.get("brokenId").asLong(), broken.toString());

    jdbcTemplate.update("UPDATE audit_log SET detail = ? WHERE id = ?", original, id);
    JsonNode restored = data(send("GET", "/api/v1/audit-logs/verify?fromId=" + id + "&toId=" + id, null, cookie));
    assertTrue(restored.get("valid").asBoolean(), "还原后链应重新自洽：" + restored);
  }

  @Test
  @DisplayName("失败也落行：写端点 40401 → result=fail + reason，业务错误照旧抛出")
  void failedWriteLeavesFailRow() throws Exception {
    String cookie = login("admin", "admin123");
    HttpResponse<String> failed = send("PATCH", "/api/v1/products/99999999",
        "{\"name\":\"不存在\",\"lockVersion\":0}", cookie);
    assertEquals(404, failed.statusCode(), failed.body());

    Map<String, Object> row = jdbcTemplate.queryForMap(
        "SELECT category, result, reason FROM audit_log WHERE trace_id = ?",
        failed.headers().firstValue("X-Trace-Id").orElseThrow());
    assertEquals("fail", row.get("result"), row.toString());
    assertNotNull(row.get("reason"), "失败行必须带原因：" + row);
    assertTrue(((String) row.get("reason")).contains("不存在"), "原因应是可读文案：" + row);
  }

  @Test
  @DisplayName("diff 掩码：字段变更入 changes，敏感字段只留 ***（口令不进审计）")
  void diffMasksSensitiveFields() {
    AuditDiffer differ = new AuditDiffer(snapshots, jsonMapper);
    Map<String, Object> current = new java.util.HashMap<>(Map.of("name", "旧名", "password", "old-secret"));
    // provider 每次返回新快照（框架把 before 留在内存里与 after 比对）
    snapshots.register("t04-probe", id -> new java.util.HashMap<>(current));
    Map<String, Object> before = differ.before("t04-probe", 1L);
    current.put("name", "新名");
    current.put("password", "new-secret");

    String changesJson = differ.changesJson(before, "t04-probe", 1L, List.of());
    assertNotNull(changesJson, "有变更就该产出 changes");
    assertFalse(changesJson.contains("old-secret"), "旧口令不得入审计：" + changesJson);
    assertFalse(changesJson.contains("new-secret"), "新口令不得入审计：" + changesJson);
    List<AuditDiffer.AuditChange> changes = differ.parse(changesJson);
    assertEquals(2, changes.size(), changesJson);
    AuditDiffer.AuditChange name = changes.stream().filter(c -> c.field().equals("name")).findFirst().orElseThrow();
    assertTrue(name.before().contains("旧名") && name.after().contains("新名"), changesJson);
    AuditDiffer.AuditChange password =
        changes.stream().filter(c -> c.field().equals("password")).findFirst().orElseThrow();
    assertEquals("***", password.before());
    assertEquals("***", password.after());
  }

  @Test
  @DisplayName("查询聚合：真请求经过滤器累加 → flush 落 audit_query_stat → query-stats 端点可查")
  void queryStatsAggregateAndList() throws Exception {
    String cookie = login("admin", "admin123");
    HttpResponse<String> first = send("GET", "/api/v1/audit-logs/query-stats", null, cookie);
    assertEquals(200, first.statusCode(), first.body());

    accumulator.flush();
    Map<String, Object> stat = jdbcTemplate.queryForMap(
        "SELECT query_count, total_ms FROM audit_query_stat WHERE account = 'admin' AND resource = 'audit-logs'");
    assertTrue(((Number) stat.get("query_count")).longValue() >= 1, "本请求应被累加：" + stat);
    assertTrue(((Number) stat.get("total_ms")).longValue() >= 0, stat.toString());

    // 再打两次：累加列走单条 SQL 自增，行数不变、计数增加
    send("GET", "/api/v1/audit-logs/query-stats", null, cookie);
    send("GET", "/api/v1/audit-logs/query-stats", null, cookie);
    accumulator.flush();
    Long rows = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM audit_query_stat WHERE account = 'admin' AND resource = 'audit-logs'", Long.class);
    assertEquals(1L, rows, "同一 账号+模块+日 只能一行（uq_audit_query_stat）");
    Long grown = jdbcTemplate.queryForObject(
        "SELECT query_count FROM audit_query_stat WHERE account = 'admin' AND resource = 'audit-logs'", Long.class);
    assertTrue(grown >= 3, "三次请求应累加到同一行：" + grown);

    JsonNode list = data(send("GET",
        "/api/v1/audit-logs/query-stats?filters%5Bresource%5D=audit-logs&filters%5Baccount%5D=admin", null, cookie));
    assertEquals(1, list.get("total").asInt(), list.toString());
    JsonNode item = list.get("items").get(0);
    assertEquals("admin", item.get("account").asText());
    assertEquals("audit-logs", item.get("resource").asText());
    assertTrue(item.get("count").asLong() >= 3, list.toString());
    assertFalse(item.get("day").isNull(), list.toString());
  }

  @Test
  @DisplayName("保留策略：超期行被清理、未超期行保留，清理动作自身记一行 audit-cleanup")
  void cleanupRemovesExpiredRows() {
    String suffix = Long.toString(System.nanoTime());
    String oldAction = "t04-old-" + suffix;
    jdbcTemplate.update("INSERT INTO audit_log (action, category, result, created_at)"
        + " VALUES (?, 'business', 'success', DATEADD('DAY', -40, CURRENT_TIMESTAMP))", oldAction);
    settings.upsert(SettingRepository.SYSTEM_OWNER, "audit", "retention-days", "30");
    try {
      cleanupJob.cleanup();

      Long remaining = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM audit_log WHERE action = ?", Long.class, oldAction);
      assertEquals(0L, remaining, "40 天前的行应被保留策略清掉");
      Map<String, Object> cleanup = jdbcTemplate.queryForMap(
          "SELECT category, result, detail FROM audit_log WHERE action = 'audit-cleanup'"
              + " ORDER BY id DESC LIMIT 1");
      assertEquals("config", cleanup.get("category"), "audit-cleanup 登记在 config 类");
      assertEquals("success", cleanup.get("result"));
      String detail = (String) cleanup.get("detail");
      assertTrue(detail.contains("removed=") && detail.contains("retentionDays=30"),
          "清理留痕要能回答「删了多少、按什么口径」：" + detail);
    } finally {
      settings.deleteSystem("audit", "retention-days");
    }
  }

  @Test
  @DisplayName("新过滤字段与详情：filters[category]/filters[result] 生效，越界参数给 40001/40401")
  void newFiltersAndErrorPaths() throws Exception {
    String cookie = login("admin", "admin123");
    createProduct(cookie, "审计过滤产品-" + System.nanoTime());

    JsonNode auth = data(send("GET",
        "/api/v1/audit-logs?filters%5Bcategory%5D=auth&filters%5Baction%5D=login&limit=5", null, cookie));
    assertTrue(auth.get("total").asInt() >= 1, auth.toString());
    for (JsonNode item : auth.get("items")) {
      assertEquals("auth", item.get("category").asText(), auth.toString());
      assertEquals("login", item.get("action").asText(), auth.toString());
      assertEquals("success", item.get("result").asText(), auth.toString());
    }

    JsonNode failedLogins = data(send("GET",
        "/api/v1/audit-logs?filters%5Bresult%5D=fail&filters%5Baction%5D=login-failed&limit=5", null, cookie));
    for (JsonNode item : failedLogins.get("items")) {
      assertEquals("fail", item.get("result").asText(), failedLogins.toString());
    }

    HttpResponse<String> tooManyRows = send("GET", "/api/v1/audit-logs/verify?maxRows=999999", null, cookie);
    assertEquals(400, tooManyRows.statusCode(), tooManyRows.body());
    assertTrue(tooManyRows.body().contains("40001"), tooManyRows.body());

    HttpResponse<String> notFound = send("GET", "/api/v1/audit-logs/999999999", null, cookie);
    assertEquals(404, notFound.statusCode(), notFound.body());
    assertTrue(notFound.body().contains("40401"), notFound.body());

    HttpResponse<String> badId = send("GET", "/api/v1/audit-logs/abc", null, cookie);
    assertEquals(400, badId.statusCode(), badId.body());
  }
}
