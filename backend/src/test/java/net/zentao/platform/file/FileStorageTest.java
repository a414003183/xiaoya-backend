package net.zentao.platform.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 上传守卫（platform 卡 §8）：黑名单/超 50MB → 42201；存储路径落盘；清理任务删磁盘。 */
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

  @Test
  @DisplayName("黑名单扩展名 → 42201")
  void blacklistExtensionRejected() {
    ApiException exception = assertThrows(ApiException.class,
        () -> uploadHandler.upload(admin, "shell.php", stream("x"), 1, "", 0L));
    assertEquals(42201, exception.errorCode().code());
    assertTrue(exception.fields().containsValue("extensionBlocked"));
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
