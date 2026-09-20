package net.zentao.platform.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.zentao.platform.session.SessionPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 文件 API（platform 卡 §8）：下载 attachment+downloads+1；他人删除 40302；软删后下载 40401；列表必填过滤 40001。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FileApiTest {

  @Value("${local.server.port}")
  int port;

  @Autowired
  UploadFileHandler uploadHandler;

  @Autowired
  FileRepository repository;

  @Autowired
  org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  private final HttpClient http = HttpClient.newHttpClient();
  private SessionPrincipal uploader;
  private String plainCookie;
  private String plainAccount;

  @BeforeEach
  void seedAccounts() throws Exception {
    String adminCookie = login("admin", "admin123");
    FileView view = uploadHandler.upload(new SessionPrincipal(1, "admin"), "附件.txt",
        new java.io.ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), 5, "story", 3L);
    adminFileId = view.id();

    plainAccount = "file-user-" + UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO account (account, password, real_name) VALUES (?, ?, '文件用户')",
        plainAccount, new BCryptPasswordEncoder().encode("admin123"));
    plainCookie = login(plainAccount, "admin123");
  }

  private long adminFileId;

  private String login(String account, String password) throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/session"))
            .header("Content-Type", "application/json")
            .header("X-Requested-With", "fetch")
            .POST(HttpRequest.BodyPublishers.ofString("{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private HttpResponse<String> send(String cookie, String method, String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Cookie", cookie)
            .header("X-Requested-With", "fetch")
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  @DisplayName("下载恒 attachment 且 downloads+1；软删后下载 40401")
  void downloadCountsAndAttachment() throws Exception {
    HttpResponse<byte[]> download = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/files/" + adminFileId + "/download"))
            .header("Cookie", plainCookie)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofByteArray());
    assertEquals(200, download.statusCode());
    assertTrue(download.headers().firstValue("Content-Disposition").orElse("").contains("attachment"));
    assertEquals("hello", new String(download.body(), StandardCharsets.UTF_8));
    assertEquals(1, jdbcTemplate.queryForObject("SELECT downloads FROM file WHERE id = ?", Integer.class, adminFileId));

    jdbcTemplate.update("UPDATE file SET deleted_at = NOW() WHERE id = ?", adminFileId);
    HttpResponse<String> gone = send(plainCookie, "GET", "/api/v1/files/" + adminFileId + "/download");
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());
  }

  @Test
  @DisplayName("绑定对象文件登录即可下载；非上传人删除 → 40302")
  void deleteGuard() throws Exception {
    HttpResponse<String> forbidden = send(plainCookie, "DELETE", "/api/v1/files/" + adminFileId);
    assertEquals(403, forbidden.statusCode(), forbidden.body());
    assertTrue(forbidden.body().contains("40302"), forbidden.body());
  }

  @Test
  @DisplayName("列表缺 filters[objectType]/[objectId] → 40001")
  void listRequiresObjectFilters() throws Exception {
    HttpResponse<String> missing = send(plainCookie, "GET", "/api/v1/files");
    assertEquals(400, missing.statusCode(), missing.body());
    assertTrue(missing.body().contains("40001"), missing.body());

    HttpResponse<String> ok = send(plainCookie, "GET", "/api/v1/files?filters%5BobjectType%5D=story&filters%5BobjectId%5D=3");
    assertEquals(200, ok.statusCode(), ok.body());
    assertTrue(ok.body().contains("附件"), ok.body());
  }

  private FileView uploadAsAdmin(String filename) {
    return uploadHandler.upload(new SessionPrincipal(1, "admin"), filename,
        new java.io.ByteArrayInputStream(new byte[] {1, 2, 3}), 3, "story", 3L);
  }

  @Test
  @DisplayName("A-02 图片内联：白名单扩展名 inline+nosniff+按扩展名 Content-Type；不计 downloads")
  void rawInlineImages() throws Exception {
    java.util.Map<String, String> cases = java.util.Map.of(
        "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png",
        "gif", "image/gif", "webp", "image/webp", "bmp", "image/bmp");
    for (var entry : cases.entrySet()) {
      FileView view = uploadAsAdmin("pic." + entry.getKey());
      HttpResponse<byte[]> raw = http.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/files/" + view.id() + "/raw"))
              .header("Cookie", plainCookie)
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofByteArray());
      assertEquals(200, raw.statusCode(), entry.getKey());
      assertEquals(entry.getValue(),
          raw.headers().firstValue("Content-Type").orElse("").split(";")[0].trim(), entry.getKey());
      assertTrue(raw.headers().firstValue("Content-Disposition").orElse("").contains("inline"), entry.getKey());
      assertEquals("nosniff", raw.headers().firstValue("X-Content-Type-Options").orElse(""), entry.getKey());
      assertEquals(3, raw.body().length, entry.getKey());
      assertEquals(0, jdbcTemplate.queryForObject("SELECT downloads FROM file WHERE id = ?", Integer.class, view.id()),
          "raw 预览不计 downloads: " + entry.getKey());
    }
  }

  @Test
  @DisplayName("A-02 内联守卫：svg/无扩展名 40001；不可见 40302；软删 40401")
  void rawGuards() throws Exception {
    FileView svg = uploadAsAdmin("evil.svg");
    HttpResponse<String> svgRaw = send(plainCookie, "GET", "/api/v1/files/" + svg.id() + "/raw");
    assertEquals(400, svgRaw.statusCode(), svgRaw.body());
    assertTrue(svgRaw.body().contains("40001"), svgRaw.body());

    FileView noExt = uploadAsAdmin("noextension");
    HttpResponse<String> noExtRaw = send(plainCookie, "GET", "/api/v1/files/" + noExt.id() + "/raw");
    assertEquals(400, noExtRaw.statusCode(), noExtRaw.body());
    assertTrue(noExtRaw.body().contains("40001"), noExtRaw.body());

    // 未绑定对象的文件仅上传人/超管可见：admin 的 png，普通用户 → 40302
    FileView unbound = uploadHandler.upload(new SessionPrincipal(1, "admin"), "secret.png",
        new java.io.ByteArrayInputStream(new byte[] {1}), 1, null, null);
    HttpResponse<String> forbidden = send(plainCookie, "GET", "/api/v1/files/" + unbound.id() + "/raw");
    assertEquals(403, forbidden.statusCode(), forbidden.body());
    assertTrue(forbidden.body().contains("40302"), forbidden.body());

    FileView deleted = uploadAsAdmin("gone.png");
    jdbcTemplate.update("UPDATE file SET deleted_at = NOW() WHERE id = ?", deleted.id());
    HttpResponse<String> gone = send(plainCookie, "GET", "/api/v1/files/" + deleted.id() + "/raw");
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());
  }
}
