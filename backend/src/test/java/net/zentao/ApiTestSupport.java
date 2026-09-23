package net.zentao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;

/**
 * HTTP 级用例基座：真 HTTP 打本进程随机端口（真过滤器链/真会话/Security 配置）。
 * 供各域用例复用 login/send/dataId 三件套，避免每个测试类各抄一份。
 */
public abstract class ApiTestSupport extends H2TestSupport {

  @Value("${local.server.port}")
  protected int port;

  protected final HttpClient http = HttpClient.newHttpClient();
  protected final ObjectMapper json = new ObjectMapper();

  protected HttpResponse<String> send(String method, String path, String body, String cookie) throws Exception {
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

  /** 登录并返回 ZT_SESSION cookie；失败即用例失败。 */
  protected String login(String account, String password) throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/session",
        "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}", null);
    assertEquals(200, response.statusCode(), response.body());
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  /** 期望 200 并取 data.id。 */
  protected long dataId(HttpResponse<String> response) throws Exception {
    return data(response).at("/id").asLong();
  }

  /** 期望 200 并取 data 节点。 */
  protected JsonNode data(HttpResponse<String> response) throws Exception {
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data");
  }

  // ── 共用最小建链（T-4 project 成员/干系人/关联/看板各族用例；日期取合规区间） ──

  protected long createProduct(String cookie, String name) throws Exception {
    return dataId(send("POST", "/api/v1/products", "{\"name\":\"" + name + "\",\"acl\":\"public\"}", cookie));
  }

  protected long createProject(String cookie, String name, long productId, String extraJson) throws Exception {
    return dataId(send("POST", "/api/v1/projects",
        "{\"name\":\"" + name + "\",\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-12-31\",\"productIds\":["
            + productId + "]" + (extraJson == null ? "" : "," + extraJson) + "}",
        cookie));
  }

  protected long createExecution(String cookie, long projectId, String name) throws Exception {
    return dataId(send("POST", "/api/v1/projects/" + projectId + "/executions",
        "{\"type\":\"sprint\",\"name\":\"" + name + "\",\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}",
        cookie));
  }

  protected long createStory(String cookie, long productId, String title, String extraJson) throws Exception {
    return dataId(send("POST", "/api/v1/products/" + productId + "/stories",
        "{\"title\":\"" + title + "\"" + (extraJson == null ? "" : "," + extraJson) + "}", cookie));
  }

  /** 建（或复用）只带指定权限码的测试账号并登录返回 cookie；账号名需全局唯一（同 JVM 共享 H2 库）。 */
  protected String accountWithPrivileges(String adminCookie, String account, String privilegeCodes)
      throws Exception {
    long groupId = dataId(send("POST", "/api/v1/roles", "{\"name\":\"组-" + account + "\"}", adminCookie));
    assertEquals(200, send("PUT", "/api/v1/roles/" + groupId + "/privileges",
        "{\"codes\":[" + privilegeCodes + "]}", adminCookie).statusCode());
    send("POST", "/api/v1/accounts", "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\""
        + account + "\",\"roleIds\":[" + groupId + "]}", adminCookie);
    return login(account, "secret123");
  }
}
