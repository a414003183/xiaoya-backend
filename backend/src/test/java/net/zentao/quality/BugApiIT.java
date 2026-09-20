package net.zentao.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.zentao.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * T-11 Bug API IT（quality 卡 §5/§8，Testcontainers MySQL 8.4 真库）：
 * 批量创建 ≤50 与逐条结果、批量动作部分成功、乐观锁 40901、过滤白名单外 40001、
 * {@code resolution=tostory} 建需求并回填 storyId。
 *
 * <p>H2 单测（{@link BugStateMachineTest}）覆盖状态机与守卫语义；本 IT 补齐真库侧的
 * 约束落库（唯一/JSON 列/事务）与跨域副作用持久化。
 */
class BugApiIT extends MySqlContainerSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String adminCookie;

  @BeforeEach
  void login() throws Exception {
    adminCookie = loginAs("admin", "admin123");
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

  private JsonNode data(HttpResponse<String> response) throws Exception {
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data");
  }

  private long dataId(HttpResponse<String> response) throws Exception {
    return data(response).at("/id").asLong();
  }

  private long freshProduct() throws Exception {
    return dataId(send("POST", "/api/v1/products",
        "{\"name\":\"IT Bug 产品 " + System.nanoTime() + "\",\"acl\":\"public\"}", adminCookie));
  }

  private long createBug(long productId, String title) throws Exception {
    return dataId(send("POST", "/api/v1/products/" + productId + "/bugs",
        "{\"title\":\"" + title + "\",\"openedBuilds\":\"1\",\"severity\":2,\"priority\":2,"
            + "\"type\":\"codeerror\"}",
        adminCookie));
  }

  @Test
  @DisplayName("批量创建：50 条全成；51 条整体拒绝 42201；含非法项时逐条结果保留部分成功")
  void batchCreateLimit() throws Exception {
    long product = freshProduct();

    String fifty = IntStream.rangeClosed(1, 50)
        .mapToObj(index -> "{\"title\":\"IT 批 " + index + "\",\"openedBuilds\":\"1\"}")
        .collect(Collectors.joining(","));
    JsonNode okBatch = data(send("POST", "/api/v1/products/" + product + "/bugs/batch",
        "{\"items\":[" + fifty + "]}", adminCookie));
    assertEquals(50, okBatch.at("/results").size(), "50 条应逐条返回结果");
    for (JsonNode item : okBatch.at("/results")) {
      assertTrue(item.at("/ok").asBoolean(), "逐条结果：" + item);
    }

    String fiftyOne = IntStream.rangeClosed(1, 51)
        .mapToObj(index -> "{\"title\":\"IT 越限 " + index + "\",\"openedBuilds\":\"1\"}")
        .collect(Collectors.joining(","));
    HttpResponse<String> tooMany = send("POST", "/api/v1/products/" + product + "/bugs/batch",
        "{\"items\":[" + fiftyOne + "]}", adminCookie);
    assertEquals(422, tooMany.statusCode(), tooMany.body());
    assertTrue(tooMany.body().contains("42201"), tooMany.body());

    // 第 2 条缺 title → 该条 ok=false 且带 error，第 1 条仍成功落库
    JsonNode partial = data(send("POST", "/api/v1/products/" + product + "/bugs/batch",
        "{\"items\":[{\"title\":\"IT 部分成功\",\"openedBuilds\":\"1\"},{\"openedBuilds\":\"1\"}]}", adminCookie));
    assertTrue(partial.at("/results/0/ok").asBoolean(), partial.toString());
    assertEquals(false, partial.at("/results/1/ok").asBoolean(), partial.toString());
    assertTrue(partial.at("/results/1/error").asText().contains("42201"), partial.toString());
  }

  @Test
  @DisplayName("乐观锁：PATCH lockVersion 相符成改并自增；旧版本再写 40901 且内容不变")
  void lockVersionGuard() throws Exception {
    long bug = createBug(freshProduct(), "IT 锁 Bug " + System.nanoTime());

    JsonNode first = data(send("PATCH", "/api/v1/bugs/" + bug,
        "{\"title\":\"IT 锁-改一\",\"lockVersion\":0}", adminCookie));
    assertEquals("IT 锁-改一", first.at("/title").asText(), first.toString());
    assertEquals(1, first.at("/lockVersion").asInt(),
        "写路径必须回读库内自增后的 lockVersion（否则前端下一次 PATCH 必 40901）：" + first);

    // 新版本可继续写；旧版本（0）被拒
    JsonNode second = data(send("PATCH", "/api/v1/bugs/" + bug,
        "{\"title\":\"IT 锁-改二\",\"lockVersion\":1}", adminCookie));
    assertEquals(2, second.at("/lockVersion").asInt(), second.toString());

    HttpResponse<String> stale = send("PATCH", "/api/v1/bugs/" + bug,
        "{\"title\":\"IT 锁-过期写入\",\"lockVersion\":0}", adminCookie);
    assertEquals(409, stale.statusCode(), stale.body());
    assertTrue(stale.body().contains("40901"), stale.body());

    JsonNode after = data(send("GET", "/api/v1/bugs/" + bug, null, adminCookie));
    assertEquals("IT 锁-改二", after.at("/title").asText(), "过期写入不得落地：" + after);
  }

  @Test
  @DisplayName("过滤白名单：已注册字段可用，白名单外字段 40001")
  void filterWhitelist() throws Exception {
    long product = freshProduct();
    createBug(product, "IT 过滤 Bug " + System.nanoTime());

    JsonNode filtered = data(send("GET",
        "/api/v1/products/" + product + "/bugs?filters%5Bstatus%5D=active&filters%5Bseverity%5D=2",
        null, adminCookie));
    assertEquals(1, filtered.at("/total").asLong(), filtered.toString());
    assertEquals(0, data(send("GET", "/api/v1/products/" + product + "/bugs?filters%5Bstatus%5D=closed",
        null, adminCookie)).at("/total").asLong());

    HttpResponse<String> unknown = send("GET",
        "/api/v1/products/" + product + "/bugs?filters%5BtoTask%5D=1", null, adminCookie);
    assertEquals(400, unknown.statusCode(), unknown.body());
    assertTrue(unknown.body().contains("40001"), unknown.body());
  }

  @Test
  @DisplayName("resolve=tostory：建需求并回填 bug.storyId（跨域副作用真库持久化）")
  void tostoryBackfillsStory() throws Exception {
    long product = freshProduct();
    long bug = createBug(product, "IT 转需求 Bug " + System.nanoTime());

    JsonNode resolved = data(send("POST", "/api/v1/bugs/" + bug + "/resolve",
        "{\"resolution\":\"tostory\",\"comment\":\"转需求处理\"}", adminCookie));
    assertEquals("resolved", resolved.at("/status").asText(), resolved.toString());
    assertEquals("tostory", resolved.at("/resolution").asText(), resolved.toString());
    long storyId = resolved.at("/storyId").asLong();
    assertTrue(storyId > 0, "tostory 应回填 storyId：" + resolved);

    JsonNode story = data(send("GET", "/api/v1/stories/" + storyId, null, adminCookie));
    assertEquals("active", story.at("/status").asText(), story.toString());
    assertEquals(0, "bug".compareTo(story.at("/source").asText()), story.toString());

    // 回填是持久的：重新读 Bug 仍在
    assertEquals(storyId, data(send("GET", "/api/v1/bugs/" + bug, null, adminCookie)).at("/storyId").asLong());
  }

  @Test
  @DisplayName("批量动作：部分成功逐条结果，越权/非法项 error 不阻断其余项")
  void batchActionPartialSuccess() throws Exception {
    long product = freshProduct();
    long activeBug = createBug(product, "IT 批动作 A " + System.nanoTime());
    long resolvedBug = createBug(product, "IT 批动作 B " + System.nanoTime());
    send("POST", "/api/v1/bugs/" + resolvedBug + "/resolve", "{\"resolution\":\"fixed\",\"resolvedBuild\":\"1\"}",
        adminCookie);

    JsonNode results = data(send("POST", "/api/v1/bugs/batch",
        "{\"ids\":[" + activeBug + "," + resolvedBug + "],\"action\":\"close\"}", adminCookie));
    assertEquals(false, results.at("/results/0/ok").asBoolean(), "active 态不可 close：" + results);
    assertEquals(true, results.at("/results/1/ok").asBoolean(), "resolved 态可 close：" + results);

    assertEquals("closed", data(send("GET", "/api/v1/bugs/" + resolvedBug, null, adminCookie))
        .at("/status").asText());
    assertEquals("active", data(send("GET", "/api/v1/bugs/" + activeBug, null, adminCookie))
        .at("/status").asText(), "失败项不得产生半更新");
  }
}
