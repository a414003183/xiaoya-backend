package net.zentao.platform.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.zentao.ApiTestSupport;
import net.zentao.platform.activity.ObjectVisibilityRegistry;
import net.zentao.platform.session.SessionPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 文件 API（platform 卡 §8 / T49）：下载 attachment+downloads+1；绑定对象可见性（40302）；
 * 他人删除 40302；软删后 40401；列表必填过滤 40001。
 *
 * <p>夹具用**真实产品/需求**（公开产品 → 普通用户可见；私有产品 → 不可见）：T49 之前"绑定即可下载"，
 * 夹具才敢绑一个不存在的 story:3；现在可见性生效，必须真建对象，否则测的是"对象查无 = 不可见"。
 */
class FileApiTest extends ApiTestSupport {

  @Autowired
  UploadFileHandler uploadHandler;

  @Autowired
  FileRepository repository;

  @Autowired
  ObjectVisibilityRegistry visibilityRegistry;

  @Autowired
  org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  private String adminCookie;
  private String plainCookie;
  private String plainAccount;
  private long publicStoryId;
  private long privateStoryId;
  private long adminFileId;

  /** T60 起图片类扩展名要核魔数：夹具里的 png 必须是真 png（不再是随便几个字节）。 */
  private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3 };

  @BeforeEach
  void seedAccounts() throws Exception {
    adminCookie = login("admin", "admin123");
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    long publicProductId = createProduct(adminCookie, "文件-公开产品-" + suffix);
    publicStoryId = createStory(adminCookie, publicProductId, "文件公开需求", null);
    long privateProductId = dataId(send("POST", "/api/v1/products",
        "{\"name\":\"文件-私有产品-" + suffix + "\",\"acl\":\"private\"}", adminCookie));
    privateStoryId = createStory(adminCookie, privateProductId, "文件私有需求", null);

    // 上传人 = admin（超管）；附件绑在普通用户可见的公开需求上
    adminFileId = uploadAsAdmin("附件.txt", "hello", 5, "story", publicStoryId).id();

    plainAccount = "file-user-" + suffix;
    jdbcTemplate.update("INSERT INTO account (account, password, real_name) VALUES (?, ?, '文件用户')",
        plainAccount, new BCryptPasswordEncoder().encode("admin123"));
    plainCookie = login(plainAccount, "admin123");
  }

  private String url(String path) {
    return "http://localhost:" + port + "/api/v1" + path;
  }

  private HttpResponse<byte[]> download(String cookie, long fileId) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create(url("/files/" + fileId + "/download")))
            .header("Cookie", cookie)
            .header("X-Requested-With", "fetch")
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofByteArray());
  }

  private FileView uploadAsAdmin(String filename, String content, long size, String objectType, Long objectId) {
    return uploadHandler.upload(new SessionPrincipal(1, "admin"), filename,
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), size, objectType, objectId);
  }

  private FileView uploadPngAsAdmin(String filename, String objectType, Long objectId) {
    return uploadHandler.upload(new SessionPrincipal(1, "admin"), filename,
        new ByteArrayInputStream(PNG), PNG.length, objectType, objectId);
  }

  @Test
  @DisplayName("下载恒 attachment 且 downloads+1（对象可见者）；软删后下载 40401")
  void downloadCountsAndAttachment() throws Exception {
    HttpResponse<byte[]> download = download(plainCookie, adminFileId);
    assertEquals(200, download.statusCode(), new String(download.body(), StandardCharsets.UTF_8));
    assertTrue(download.headers().firstValue("Content-Disposition").orElse("").contains("attachment"));
    assertEquals("hello", new String(download.body(), StandardCharsets.UTF_8));
    assertEquals(1, jdbcTemplate.queryForObject("SELECT downloads FROM file WHERE id = ?", Integer.class, adminFileId));

    jdbcTemplate.update("UPDATE file SET deleted_at = NOW() WHERE id = ?", adminFileId);
    HttpResponse<String> gone = send("GET", "/api/v1/files/" + adminFileId + "/download", null, plainCookie);
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());
  }

  @Test
  @DisplayName("T49 越权回归：绑定对象不可见的附件不可下载/预览/列出（40302）；上传人本人不受影响")
  void boundFileRequiresObjectVisibility() throws Exception {
    long secretFileId = uploadPngAsAdmin("secret.png", "story", privateStoryId).id();

    HttpResponse<byte[]> download = download(plainCookie, secretFileId);
    assertEquals(403, download.statusCode(), new String(download.body(), StandardCharsets.UTF_8));
    assertTrue(new String(download.body(), StandardCharsets.UTF_8).contains("40302"),
        new String(download.body(), StandardCharsets.UTF_8));

    HttpResponse<String> raw = send("GET", "/api/v1/files/" + secretFileId + "/raw", null, plainCookie);
    assertEquals(403, raw.statusCode(), raw.body());
    assertTrue(raw.body().contains("40302"), raw.body());

    HttpResponse<String> list = send("GET",
        "/api/v1/files?filters%5BobjectType%5D=story&filters%5BobjectId%5D=" + privateStoryId, null, plainCookie);
    assertEquals(403, list.statusCode(), list.body());

    // 上传人（此处为超管）本人仍可下载：可见性只在"非上传人"这一支生效
    assertEquals(200, download(adminCookie, secretFileId).statusCode());

    // 对象查无（不存在的 id / 已删对象）= 不可见：不给"猜 id"留缝
    long orphanFileId = uploadPngAsAdmin("orphan.png", "story", 999999L).id();
    assertEquals(403, download(plainCookie, orphanFileId).statusCode());

    // 公开需求上的附件对同一普通用户仍可下载：没有把正常路径一起关掉
    assertEquals(200, download(plainCookie, adminFileId).statusCode());
    assertEquals(200, send("GET", "/api/v1/files?filters%5BobjectType%5D=story&filters%5BobjectId%5D=" + publicStoryId,
        null, plainCookie).statusCode());
  }

  @Test
  @DisplayName("T49：在用 objectType 全有可见性谓词（未注册类型 fail-open 放行，故用『查无此对象』验证）")
  void everyUsedObjectTypeIsRegistered() {
    SessionPrincipal principal = new SessionPrincipal(9999L, plainAccount);
    for (String objectType : List.of("story", "task", "bug", "testCase", "doc")) {
      assertFalse(visibilityRegistry.isVisible(principal, objectType, 999999L),
          objectType + " 未注册谓词：查无此对象仍判可见（fail-open）");
    }
    assertTrue(visibilityRegistry.isVisible(principal, "account", 999999L), "account 无行级 ACL：登录即可见");
  }

  @Test
  @DisplayName("T60 容器闸门：>50MB 上传 → 422 tooLarge（不是 50001，也不是断连）")
  void containerSizeGateIsValidationError() throws Exception {
    // 54MB 而不是 51MB：容器在 50MB 处中止解析，未被读走的余量要大于 Tomcat 默认的 2MB 吞包额度，
    // 这个用例才真的在考 `server.tomcat.max-swallow-size`（去掉那行配置 → 客户端收到断连而不是 422，实测）
    byte[] payload = new byte[54 * 1024 * 1024];
    String boundary = "----zentao" + System.nanoTime();
    byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; "
        + "filename=\"big.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
    byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create(url("/files")))
            .header("Cookie", adminCookie)
            .header("X-Requested-With", "fetch")
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            // 分段发布：不把 51MB 复制成第二份（测试 JVM 堆也别浪费）
            .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(head, payload, tail)))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(422, response.statusCode(), response.body());
    assertTrue(response.body().contains("42201"), response.body());
    assertTrue(response.body().contains("tooLarge"), response.body());
  }

  @Test
  @DisplayName("非上传人删除 → 40302")
  void deleteGuard() throws Exception {
    HttpResponse<String> forbidden = send("DELETE", "/api/v1/files/" + adminFileId, null, plainCookie);
    assertEquals(403, forbidden.statusCode(), forbidden.body());
    assertTrue(forbidden.body().contains("40302"), forbidden.body());
  }

  @Test
  @DisplayName("列表缺 filters[objectType]/[objectId] → 40001；objectId 非数字 → 40001（不是 50001）")
  void listRequiresObjectFilters() throws Exception {
    HttpResponse<String> missing = send("GET", "/api/v1/files", null, plainCookie);
    assertEquals(400, missing.statusCode(), missing.body());
    assertTrue(missing.body().contains("40001"), missing.body());

    HttpResponse<String> notNumeric = send("GET",
        "/api/v1/files?filters%5BobjectType%5D=story&filters%5BobjectId%5D=abc", null, plainCookie);
    assertEquals(400, notNumeric.statusCode(), notNumeric.body());
    assertTrue(notNumeric.body().contains("40001"), notNumeric.body());

    HttpResponse<String> ok = send("GET",
        "/api/v1/files?filters%5BobjectType%5D=story&filters%5BobjectId%5D=" + publicStoryId, null, plainCookie);
    assertEquals(200, ok.statusCode(), ok.body());
    assertTrue(ok.body().contains("附件"), ok.body());
  }

  @Test
  @DisplayName("A-02 图片内联：白名单扩展名 inline+nosniff+按扩展名 Content-Type；不计 downloads")
  void rawInlineImages() throws Exception {
    java.util.Map<String, byte[]> cases = java.util.Map.of(
        "jpg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3 },
        "jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 4, 5 },
        "png", PNG,
        "gif", "GIF89a123".getBytes(StandardCharsets.UTF_8),
        "webp", new byte[] { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 9 },
        "bmp", "BM123456".getBytes(StandardCharsets.UTF_8));
    java.util.Map<String, String> types = java.util.Map.of(
        "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png",
        "gif", "image/gif", "webp", "image/webp", "bmp", "image/bmp");
    for (var entry : cases.entrySet()) {
      String extension = entry.getKey();
      byte[] content = entry.getValue();
      FileView view = uploadHandler.upload(new SessionPrincipal(1, "admin"), "pic." + extension,
          new ByteArrayInputStream(content), content.length, "story", publicStoryId);
      HttpResponse<byte[]> raw = http.send(
          HttpRequest.newBuilder(URI.create(url("/files/" + view.id() + "/raw")))
              .header("Cookie", plainCookie)
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofByteArray());
      assertEquals(200, raw.statusCode(), extension);
      assertEquals(types.get(extension),
          raw.headers().firstValue("Content-Type").orElse("").split(";")[0].trim(), extension);
      assertTrue(raw.headers().firstValue("Content-Disposition").orElse("").contains("inline"), extension);
      assertEquals("nosniff", raw.headers().firstValue("X-Content-Type-Options").orElse(""), extension);
      assertEquals(content.length, raw.body().length, extension);
      assertEquals(0, jdbcTemplate.queryForObject("SELECT downloads FROM file WHERE id = ?", Integer.class, view.id()),
          "raw 预览不计 downloads: " + extension);
    }
  }

  @Test
  @DisplayName("A-02 内联守卫：非图片（pdf）40001；未绑定对象 40302；软删 40401")
  void rawGuards() throws Exception {
    FileView pdf = uploadAsAdmin("report.pdf", "%PDF-1.4", 8, "story", publicStoryId);
    HttpResponse<String> pdfRaw = send("GET", "/api/v1/files/" + pdf.id() + "/raw", null, plainCookie);
    assertEquals(400, pdfRaw.statusCode(), pdfRaw.body());
    assertTrue(pdfRaw.body().contains("40001"), pdfRaw.body());

    FileView noExt = uploadAsAdmin("noextension", "x", 1, "story", publicStoryId);
    HttpResponse<String> noExtRaw = send("GET", "/api/v1/files/" + noExt.id() + "/raw", null, plainCookie);
    assertEquals(400, noExtRaw.statusCode(), noExtRaw.body());
    assertTrue(noExtRaw.body().contains("40001"), noExtRaw.body());

    // 未绑定对象的文件仅上传人/超管可见：admin 的 png，普通用户 → 40302
    FileView unbound = uploadPngAsAdmin("secret.png", null, null);
    HttpResponse<String> forbidden = send("GET", "/api/v1/files/" + unbound.id() + "/raw", null, plainCookie);
    assertEquals(403, forbidden.statusCode(), forbidden.body());
    assertTrue(forbidden.body().contains("40302"), forbidden.body());

    FileView deleted = uploadPngAsAdmin("gone.png", "story", publicStoryId);
    jdbcTemplate.update("UPDATE file SET deleted_at = NOW() WHERE id = ?", deleted.id());
    HttpResponse<String> gone = send("GET", "/api/v1/files/" + deleted.id() + "/raw", null, plainCookie);
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());
  }
}
