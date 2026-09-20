package net.zentao.platform.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** lang-item 覆盖层（platform 卡 §8）：覆盖 overridden=true / DELETE 恢复默认 / 重复覆盖不增行。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LangItemMergeTest {

  @Value("${local.server.port}")
  int port;

  @Autowired
  DataSource dataSource;

  private final HttpClient http = HttpClient.newHttpClient();

  private String loginCookie() throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/session"))
            .header("Content-Type", "application/json")
            .header("X-Requested-With", "fetch")
            .POST(HttpRequest.BodyPublishers.ofString("{\"account\":\"admin\",\"password\":\"admin123\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private HttpResponse<String> send(String method, String path, String json, String cookie) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Content-Type", "application/json")
        .header("Cookie", cookie);
    builder.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private int rowCount(String domain) throws Exception {
    try (var connection = dataSource.getConnection();
        var query = connection.prepareStatement("SELECT COUNT(*) FROM lang_item WHERE domain = ? AND section = 'title'")) {
      query.setString(1, domain);
      var resultSet = query.executeQuery();
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  @Test
  @DisplayName("覆盖后 overridden=true 且值生效；重复覆盖同行更新；DELETE 恢复默认")
  void overrideMergeAndRestore() throws Exception {
    String cookie = loginCookie();
    String domain = "test-lang-" + java.util.UUID.randomUUID();

    HttpResponse<String> initial = send("GET", "/api/v1/lang-items/" + domain + "/title", null, cookie);
    assertTrue(initial.body().contains("\"overridden\":false"), initial.body());

    HttpResponse<String> put1 = send("PUT", "/api/v1/lang-items/" + domain + "/title",
        "{\"items\":{\"status.active\":\"启用中\"}}", cookie);
    assertEquals(200, put1.statusCode(), put1.body());

    HttpResponse<String> after1 = send("GET", "/api/v1/lang-items/" + domain + "/title", null, cookie);
    assertTrue(after1.body().contains("\"overridden\":true"), after1.body());
    assertTrue(after1.body().contains("启用中"), after1.body());

    send("PUT", "/api/v1/lang-items/" + domain + "/title", "{\"items\":{\"status.active\":\"已启用\"}}", cookie);
    assertEquals(1, rowCount(domain), "重复覆盖应同行更新不增行");

    HttpResponse<String> deleted = send("DELETE", "/api/v1/lang-items/" + domain + "/title", null, cookie);
    assertEquals(200, deleted.statusCode(), deleted.body());
    assertTrue(deleted.body().contains("\"overridden\":false"), deleted.body());
    assertEquals(0, rowCount(domain), "删覆盖应恢复默认（行删除）");
  }
}
