package net.zentao.platform.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.zentao.MySqlContainerSupport;
import net.zentao.doc.domain.Doc;
import net.zentao.doc.domain.DocRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * T54 / BE-01 的真库侧证明（Testcontainers MySQL 8.4；CI {@code mvn -f backend verify -Pit} 必跑）：
 * {@code SoftDeletes.apply} 生成的 {@code SET lock_version = lock_version + 1} 在 MySQL 上成立，
 * 软删之后带旧版本的写 0 行——与 H2 侧 {@link SoftDeleteLockTest} 同一断言、不同方言。
 *
 * <p>为什么要有这一条：T52 的教训是「H2 两种写法都吃，上线才断」，Row API 的 raw 片段只在真库上才算数。
 */
class SoftDeleteLockIT extends MySqlContainerSupport {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();

  @Autowired private DocRepository docs;

  private String admin;

  @BeforeEach
  void login() throws Exception {
    admin = login("admin", "admin123");
  }

  private HttpResponse<String> send(String method, String path, String body, String cookie) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Content-Type", "application/json");
    if (cookie != null) {
      builder.header("Cookie", cookie);
    }
    builder.method(method,
        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String login(String account, String password) throws Exception {
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
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data/id").asLong();
  }

  @Test
  @DisplayName("MySQL 真库：软删推进 lock_version，stale update 0 行（不复活）")
  void softDeleteBumpsLockVersionOnMySql() throws Exception {
    long spaceId = dataId(send("POST", "/api/v1/doc-spaces", "{\"name\":\"T54 MySQL\",\"type\":\"custom\"}", admin));
    long docId = dataId(send("POST", "/api/v1/doc-spaces/" + spaceId + "/docs",
        "{\"title\":\"T54 doc\",\"content\":\"body\"}", admin));

    Doc stale = docs.findActiveById(docId).orElseThrow();
    assertEquals(200, send("DELETE", "/api/v1/docs/" + docId, null, admin).statusCode());

    assertTrue(docs.update(stale).isEmpty(), "MySQL 上软删后旧版本 update 必须 0 行");
    assertTrue(docs.findActiveById(docId).isEmpty(), "复活必须没有发生");
  }
}
