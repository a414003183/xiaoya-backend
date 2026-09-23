package net.zentao.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * springdoc 运行时导出：target/openapi.json，供 tools/contract-check/contract-diff.mjs 与契约比对。
 *
 * <p>T14 起 /v3/api-docs 要 api-doc-view（SurfaceGuardFilter），故导出用 admin 会话取——
 * 不为门禁开后门：匿名可读的导出路径本身就是要修的那个洞。
 */
class OpenApiExportTest extends ApiTestSupport {

  @Test
  @DisplayName("导出 /v3/api-docs 到 target/openapi.json（admin 会话）")
  void exportsContractJson() throws Exception {
    String cookie = login("admin", "admin123");
    HttpResponse<String> response = send("GET", "/v3/api-docs", null, cookie);
    assertEquals(200, response.statusCode(), response.body());
    assertTrue(response.body().contains("\"openapi\""), response.body());
    Files.createDirectories(Path.of("target"));
    Files.writeString(Path.of("target/openapi.json"), response.body());
  }
}
