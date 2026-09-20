package net.zentao.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.net.http.HttpResponse;

/**
 * 异常映射的 4xx 收口（06 A6-4）：schemathesis 打靶暴露「方法不支持 / 路径不存在」原先都落 50001，
 * 这里锁死为 40001（请求错误段）/ 40401（不存在段）——不允许 5xx 冒充客户端错误。
 */
class ApiExceptionMappingApiTest extends ApiTestSupport {

  @Test
  @DisplayName("请求方法不支持 → 40001（400），不是 50001")
  void methodNotSupported() throws Exception {
    HttpResponse<String> response = send("GET", "/api/v1/session", null, null);
    assertEquals(400, response.statusCode(), response.body());
    assertTrue(response.body().contains("40001"), response.body());
  }

  @Test
  @DisplayName("未映射路径 → 40401（404），不是 50001")
  void unmappedPath() throws Exception {
    HttpResponse<String> response = send("GET", "/api/v1/no-such-resource", null, null);
    assertEquals(404, response.statusCode(), response.body());
    assertTrue(response.body().contains("40401"), response.body());
  }

  @Test
  @DisplayName("方法参数校验（limit 超上限）→ 42201 带 fields，不是 50001")
  void methodParameterValidation() throws Exception {
    HttpResponse<String> response = send("GET", "/api/v1/comments?objectType=bug&objectId=1&limit=201", null, null);
    assertEquals(422, response.statusCode(), response.body());
    assertTrue(response.body().contains("42201") && response.body().contains("limit"), response.body());
  }

  @Test
  @DisplayName("畸形 query chunk（Tomcat InvalidParameter）→ 40001，不是 50001")
  void invalidQueryChunk() throws Exception {
    HttpResponse<String> response = send("GET", "/api/v1/comments?=null", null, null);
    assertEquals(400, response.statusCode(), response.body());
    assertTrue(response.body().contains("40001"), response.body());
  }

  @Test
  @DisplayName("请求体不可解析 → 40001（既有映射不回退）")
  void unreadableBody() throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/session", "{not-json", null);
    assertEquals(400, response.statusCode(), response.body());
    assertTrue(response.body().contains("40001"), response.body());
  }
}
