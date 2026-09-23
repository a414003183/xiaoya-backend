package net.zentao.platform.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.zentao.ApiTestSupport;
import net.zentao.platform.session.SessionPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T57 / BE-05：下载计数必须是**行级自增**，不是「读出来 +1 再整行写回」。
 *
 * <p>旧实现的并发形状：两个请求各持一份快照（都是 N），各自写回 N+1——丢一次计数，且因为整行写回，
 * 同表其它列的并发改动也会被陈旧快照覆盖。本用例打真 HTTP（控制器的 guard → 存储 → 计数全链），
 * 并发 N 次下载后要求计数**恰为** N：原子自增下恒定成立，读改写下会偏小。
 */
class FileDownloadCounterTest extends ApiTestSupport {

  private static final int CONCURRENCY = 16;

  @Autowired UploadFileHandler uploadHandler;

  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("并发下载 16 次：计数恰为 16（旧实现：读改写丢更新）")
  void concurrentDownloadsCountExactly() throws Exception {
    String cookie = login("admin", "admin123");
    byte[] payload = new byte[64 * 1024];
    new Random(57).nextBytes(payload);
    long fileId = uploadHandler.upload(new SessionPrincipal(1, "admin"), "并发下载.txt",
        new ByteArrayInputStream(payload), payload.length, null, null).id();

    List<CompletableFuture<HttpResponse<byte[]>>> futures = new ArrayList<>();
    for (int index = 0; index < CONCURRENCY; index++) {
      futures.add(http.sendAsync(HttpRequest.newBuilder(
              URI.create("http://localhost:" + port + "/api/v1/files/" + fileId + "/download"))
          .header("Cookie", cookie)
          .header("X-Requested-With", "fetch")
          .GET()
          .build(), HttpResponse.BodyHandlers.ofByteArray()));
    }
    for (CompletableFuture<HttpResponse<byte[]>> future : futures) {
      HttpResponse<byte[]> response = future.get(30, TimeUnit.SECONDS);
      assertEquals(200, response.statusCode(), "并发下载不应失败");
    }

    Long downloads = jdbcTemplate.queryForObject("SELECT downloads FROM file WHERE id = ?", Long.class, fileId);
    assertEquals(Long.valueOf(CONCURRENCY), downloads, "并发下载不得丢计数（读改写会丢）");
    assertTrue(payload.length == 64 * 1024, "夹具自检：负载大小不变");
  }
}
