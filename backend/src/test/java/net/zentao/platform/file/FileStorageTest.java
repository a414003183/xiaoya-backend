package net.zentao.platform.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 上传守卫（platform 卡 §8 + T60）：类型/内容/体积闸门 → 42201；存储路径落盘且不越界；清理任务删磁盘。 */
@SpringBootTest(properties = { "zentao.file.root=${java.io.tmpdir}/zentao-file-test" })
class FileStorageTest {

  @Autowired
  UploadFileHandler uploadHandler;

  @Autowired
  FileRepository repository;

  @Autowired
  FileStorage storage;

  @Autowired
  FileCleanupJob cleanupJob;

  @Autowired
  org.springframework.core.env.Environment environment;

  private final SessionPrincipal admin = new SessionPrincipal(1, "admin");

  @TempDir
  Path tempDir;

  private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3 };

  @Test
  @DisplayName("黑名单扩展名 → 42201")
  void blacklistExtensionRejected() {
    ApiException exception = assertThrows(ApiException.class,
        () -> uploadHandler.upload(admin, "shell.php", stream("x"), 1, "", 0L));
    assertEquals(42201, exception.errorCode().code());
    assertTrue(exception.fields().containsValue("extensionBlocked"));
  }

  @Test
  @DisplayName("T60 主动 Web 内容不收：html/svg/js 家族 → 42201 extensionBlocked（改名前就被挡）")
  void activeContentExtensionsRejected() {
    for (String extension : List.of("html", "HTML", "htm", "xhtml", "shtml", "svg", "svgz", "js", "mjs", "cjs")) {
      ApiException exception = assertThrows(ApiException.class,
          () -> uploadHandler.upload(admin, "payload." + extension, stream("<b>x</b>"), 8, "", 0L),
          extension);
      assertEquals(42201, exception.errorCode().code(), extension);
      assertTrue(exception.fields().containsValue("extensionBlocked"), extension);
    }
  }

  @Test
  @DisplayName("T60 图片类核对内容：扩展名是图片就得是图片（改名冒充 → 42201 contentMismatch）")
  void imageExtensionRequiresImageContent() {
    ApiException mismatch = assertThrows(ApiException.class,
        () -> uploadHandler.upload(admin, "fake.png", stream("<html><body>x"), 13, "", 0L));
    assertEquals(42201, mismatch.errorCode().code());
    assertTrue(mismatch.fields().containsValue("contentMismatch"));

    // 魔数对得上就放行：png 真头 / bmp "BM" / gif "GIF89a" / webp "RIFF????WEBP" / jpeg FFD8FF
    assertEquals("png", uploadHandler.upload(admin, "real.png", new ByteArrayInputStream(PNG), PNG.length, "", 0L)
        .extension());
    byte[] webp = { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P' };
    assertEquals("webp", uploadHandler.upload(admin, "real.webp", new ByteArrayInputStream(webp), webp.length, "", 0L)
        .extension());
    assertEquals("bmp", uploadHandler.upload(admin, "real.bmp", stream("BM123"), 5, "", 0L).extension());
    assertEquals("gif", uploadHandler.upload(admin, "real.gif", stream("GIF89a"), 6, "", 0L).extension());
    byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1 };
    assertEquals("jpg", uploadHandler.upload(admin, "real.jpg", new ByteArrayInputStream(jpeg), jpeg.length, "", 0L)
        .extension());
  }

  @Test
  @DisplayName("T60 非图片类不做内容核对：正文里带 HTML 片段的 txt/日志照收（不误伤业务附件）")
  void nonImageContentNotSniffed() {
    String snippet = "<html>构建日志片段</html>";
    assertEquals("txt", uploadHandler.upload(admin, "build.log.txt", stream(snippet),
        snippet.getBytes(StandardCharsets.UTF_8).length, "", 0L).extension());
  }

  @Test
  @DisplayName("T60 内联白名单与图片魔数表同集（两处漂移即红）")
  void inlineWhitelistMatchesMagicTable() {
    assertEquals(net.zentao.platform.web.FileController.INLINE_IMAGE_TYPES.keySet(),
        UploadFileHandler.IMAGE_MAGIC.keySet(),
        "FileController 的内联白名单与 UploadFileHandler 的魔数表必须同集");
  }

  @Test
  @DisplayName("T60 路径越界一律拒（`..` 与绝对路径），root 内正常路径照旧")
  void pathTraversalRejected() {
    // 每次换名字：真越界时留下的文件不该让下一轮用例变红（注入验证会真的写出去）
    String tag = "t60-escape-" + System.nanoTime();
    for (String escape : List.of("../" + tag + ".txt", "202409/../../" + tag + ".txt", "/etc/" + tag)) {
      assertThrows(IllegalStateException.class, () -> storage.store(escape, stream("x"), 1), escape);
      assertThrows(IllegalStateException.class, () -> storage.read(escape), escape);
      assertThrows(IllegalStateException.class, () -> storage.remove(escape), escape);
    }
    Path root = Path.of(environment.getProperty("zentao.file.root")).toAbsolutePath().normalize();
    assertTrue(Files.notExists(root.getParent().resolve(tag + ".txt"), java.nio.file.LinkOption.NOFOLLOW_LINKS),
        "越界写入后 root 之外不该出现文件");
  }

  @Test
  @DisplayName("超 50MB → 42201；成功上传落盘且路径 {yyyyMM}/{uuid}.{ext}")
  void uploadStoresFile() throws Exception {
    byte[] big = new byte[(int) (UploadFileHandler.MAX_SIZE_BYTES + 1)];
    ApiException tooLarge = assertThrows(ApiException.class,
        () -> uploadHandler.upload(admin, "big.zip", new ByteArrayInputStream(big), big.length, "", 0L));
    assertEquals(42201, tooLarge.errorCode().code());

    FileView view = uploadHandler.upload(admin, "说明.txt", stream("文件内容"), "文件内容".getBytes(StandardCharsets.UTF_8).length, "story", 9L);
    assertTrue(view.id() > 0);
    assertEquals("txt", view.extension());
    assertTrue(view.url().endsWith("/download"));
    FilePO po = repository.findVisibleById(view.id()).orElseThrow();
    assertTrue(po.getPath().matches("\\d{6}/[0-9a-f-]{36}\\.txt"), po.getPath());
    assertTrue(Files.exists(Path.of(environment.getProperty("zentao.file.root")).resolve(po.getPath())));

    // 软删后清理：置 deletedAt 31 天前再跑 cleanup → 磁盘文件被删
    po.setDeletedAt(java.time.Instant.now().minus(31, java.time.temporal.ChronoUnit.DAYS));
    repository.update(po);
    cleanupJob.cleanup();
    assertTrue(Files.notExists(Path.of(environment.getProperty("zentao.file.root")).resolve(po.getPath())));
    assertTrue(repository.findVisibleById(view.id()).isEmpty());
  }

  private ByteArrayInputStream stream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
