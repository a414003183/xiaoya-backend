package net.zentao.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.zentao.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * P5 IT（Testcontainers MySQL 8.4 真库）：doc 版本链唯一键、todo HHmm 列往返与状态机落列、
 * 周报同周幂等唯一键、燃尽同日懒算 upsert —— H2 单测覆盖不到的方言/约束面。
 */
class WorkspaceApiIT extends MySqlContainerSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String admin;

  @BeforeEach
  void login() throws Exception {
    admin = loginAs("admin", "admin123");
  }

  @Test
  @DisplayName("doc：连续发布得 v1/v2/v3（UNIQUE(doc_id,version) 不冲突），旧快照逐字节不变")
  void docVersionChainOnRealDb() throws Exception {
    long space = dataId(send("POST", "/api/v1/doc-spaces", "{\"name\":\"IT 库\",\"type\":\"custom\"}", admin));
    long doc = dataId(send("POST", "/api/v1/doc-spaces/" + space + "/docs",
        "{\"title\":\"IT 文档\",\"status\":\"published\",\"content\":\"# 第一版\"}", admin));

    for (int version = 2; version <= 3; version++) {
      assertEquals(200, send("POST", "/api/v1/docs/" + doc + "/save-draft",
          "{\"content\":\"# 第 " + version + " 版\"}", admin).statusCode());
      assertEquals(200, send("POST", "/api/v1/docs/" + doc + "/publish", null, admin).statusCode());
    }
    JsonNode versions = data(send("GET", "/api/v1/docs/" + doc + "/versions", null, admin));
    assertEquals(3, versions.at("/total").asLong(), versions.toString());
    assertEquals(3, versions.at("/items").get(0).at("/version").asInt(), "固定 version desc：" + versions);
    assertEquals("# 第一版", data(send("GET", "/api/v1/docs/" + doc + "/versions/1", null, admin))
        .at("/content").asText(), "旧快照不可变");
  }

  @Test
  @DisplayName("todo：char(4) HHmm 往返（读出补冒号）、finish/activate 四列落库与清空、40901")
  void todoLifecycleOnRealDb() throws Exception {
    long todo = dataId(send("POST", "/api/v1/todos",
        "{\"title\":\"IT 待办\",\"beginTime\":\"09:30\",\"endTime\":\"11:45\"}", admin));
    JsonNode created = data(send("GET", "/api/v1/todos/" + todo, null, admin));
    assertEquals("09:30", created.at("/beginTime").asText(), "HHmm → HH:mm 往返：" + created);
    assertEquals("11:45", created.at("/endTime").asText(), created.toString());

    assertEquals(200, send("POST", "/api/v1/todos/" + todo + "/finish", null, admin).statusCode());
    JsonNode done = data(send("GET", "/api/v1/todos/" + todo, null, admin));
    assertEquals("done", done.at("/status").asText());
    assertTrue(!done.at("/finishedAt").isNull(), "finishedAt 落库");

    assertEquals(200, send("POST", "/api/v1/todos/" + todo + "/activate", null, admin).statusCode());
    JsonNode activated = data(send("GET", "/api/v1/todos/" + todo, null, admin));
    assertTrue(activated.at("/finishedBy").isNull() && activated.at("/finishedAt").isNull()
        && activated.at("/closedBy").isNull() && activated.at("/closedAt").isNull(), "activate 清四列：" + activated);

    HttpResponse<String> stale = send("PATCH", "/api/v1/todos/" + todo,
        "{\"priority\":2,\"lockVersion\":0}", admin);
    assertEquals(409, stale.statusCode(), "乐观锁真库自增：" + stale.body());
  }

  @Test
  @DisplayName("weekly_report：同 (project,week) 重复 GET 只留一行；burn 同日二次访问行数不增")
  void snapshotsAreIdempotentOnRealDb() throws Exception {
    long product = dataId(send("POST", "/api/v1/products", "{\"name\":\"IT 产品\",\"acl\":\"public\"}", admin));
    long project = dataId(send("POST", "/api/v1/projects",
        "{\"name\":\"IT 项目\",\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-12-31\",\"productIds\":["
            + product + "]}", admin));
    long execution = dataId(send("POST", "/api/v1/projects/" + project + "/executions",
        "{\"type\":\"sprint\",\"name\":\"IT 执行\",\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}",
        admin));
    dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"IT 任务\",\"estimateHours\":8}", admin));

    assertEquals(200, send("GET",
        "/api/v1/projects/" + project + "/weekly-reports/current?date=2026-09-16", null, admin).statusCode());
    assertEquals(200, send("GET",
        "/api/v1/projects/" + project + "/weekly-reports/current?date=2026-09-18", null, admin).statusCode());
    assertEquals(1, data(send("GET", "/api/v1/projects/" + project + "/weekly-reports", null, admin))
        .at("/total").asLong(), "同周唯一键幂等");

    JsonNode first = data(send("GET", "/api/v1/executions/" + execution + "/reports/burn", null, admin));
    JsonNode second = data(send("GET", "/api/v1/executions/" + execution + "/reports/burn", null, admin));
    assertEquals(first.at("/remaining").toString(), second.at("/remaining").toString(), "燃尽同日重算幂等");
    assertEquals(first.at("/ideal").size(), second.at("/ideal").size(), "理想线长度稳定");
  }

  private HttpResponse<String> send(String method, String path, String body, String cookie) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Content-Type", "application/json");
    if (cookie != null) {
      builder.header("Cookie", cookie);
    }
    builder.method(method,
        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String loginAs(String account, String password) throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/session",
        "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}", null);
    assertEquals(200, response.statusCode(), response.body());
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private long dataId(HttpResponse<String> response) throws Exception {
    return data(response).at("/id").asLong();
  }

  private JsonNode data(HttpResponse<String> response) throws Exception {
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data");
  }
}
