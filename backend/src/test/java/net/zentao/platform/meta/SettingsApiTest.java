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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** settings 端点（platform 卡 §8）：keys 过滤 / owner 语义 / 系统键 40301 / 个人键放行。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SettingsApiTest {

  @Value("${local.server.port}")
  int port;

  @Autowired
  DataSource dataSource;

  private final HttpClient http = HttpClient.newHttpClient();

  private record Session(String cookie) {}

  private Session login(String account, String password) throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/session",
        "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}", null);
    assertEquals(200, response.statusCode(), response.body());
    String cookie = response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
    return new Session(cookie);
  }

  @Test
  @DisplayName("admin 写系统键与个人键；keys 过滤；不存在的键不出现")
  void adminSettingsFlow() throws Exception {
    Session admin = login("admin", "admin123");
    HttpResponse<String> put = send("PUT", "/api/v1/settings",
        "{\"settings\":{\"common.timezone\":\"Asia/Shanghai\",\"execution.defaultWorkhours\":8,\"notify.story-change\":true}}",
        admin.cookie());
    assertEquals(200, put.statusCode(), put.body());
    assertTrue(put.body().contains("Asia/Shanghai"), put.body());

    HttpResponse<String> get = send("GET",
        "/api/v1/settings?keys=common.timezone,common.not-exist-key", null, admin.cookie());
    assertEquals(200, get.statusCode(), get.body());
    assertTrue(get.body().contains("Asia/Shanghai"), get.body());
    assertFalse(get.body().contains("not-exist-key"), "不存在的键不得出现: " + get.body());

    HttpResponse<String> getPersonal = send("GET", "/api/v1/settings?keys=notify.story-change", null, admin.cookie());
    assertTrue(getPersonal.body().contains("true"), getPersonal.body());
  }

  @Test
  @DisplayName("无 setting-manage 账号：写系统键 40301，写个人 notify.* 放行")
  void ownerSemanticsEnforced() throws Exception {
    String hash = new BCryptPasswordEncoder().encode("admin123");
    String account = "settings-user-" + java.util.UUID.randomUUID();
    try (var connection = dataSource.getConnection();
        var insert = connection.prepareStatement(
            "INSERT INTO account (account, password, real_name) VALUES (?, ?, '设置用户')")) {
      insert.setString(1, account);
      insert.setString(2, hash);
      insert.executeUpdate();
    }
    Session user = login(account, "admin123");

    HttpResponse<String> systemDenied = send("PUT", "/api/v1/settings",
        "{\"settings\":{\"common.timezone\":\"UTC\"}}", user.cookie());
    assertEquals(403, systemDenied.statusCode(), systemDenied.body());
    assertTrue(systemDenied.body().contains("40301"), systemDenied.body());

    HttpResponse<String> personalAllowed = send("PUT", "/api/v1/settings",
        "{\"settings\":{\"notify.bug-created\":true}}", user.cookie());
    assertEquals(200, personalAllowed.statusCode(), personalAllowed.body());
    HttpResponse<String> readBack = send("GET", "/api/v1/settings?keys=notify.bug-created", null, user.cookie());
    assertTrue(readBack.body().contains("true"), readBack.body());
  }

  private HttpResponse<String> send(String method, String path, String json, String cookie) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Content-Type", "application/json");
    if (cookie != null) {
      builder.header("Cookie", cookie);
    }
    builder.method(method, json == null
        ? HttpRequest.BodyPublishers.noBody()
        : HttpRequest.BodyPublishers.ofString(json));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
