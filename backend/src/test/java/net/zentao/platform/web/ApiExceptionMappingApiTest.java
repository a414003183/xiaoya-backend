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
    // T50 起 /comments 不再匿名可达，本类测的是异常映射，故带会话打（匿名 401 由 AnonymousAccessTest 看护）
    String cookie = login("admin", "admin123");
    HttpResponse<String> response = send("GET", "/api/v1/comments?objectType=bug&objectId=1&limit=201", null, cookie);
    assertEquals(422, response.statusCode(), response.body());
    assertTrue(response.body().contains("42201") && response.body().contains("limit"), response.body());
  }

  @Test
  @DisplayName("畸形 query chunk（Tomcat InvalidParameter）→ 40001，不是 50001")
  void invalidQueryChunk() throws Exception {
    // 带会话后 handler 才读参数；拦截器的默认态只读 cookie，不解析 query（T50 后 401 信封也不再被畸形 query 炸成 500）
    String cookie = login("admin", "admin123");
    HttpResponse<String> response = send("GET", "/api/v1/comments?=null", null, cookie);
    assertEquals(400, response.statusCode(), response.body());
    assertTrue(response.body().contains("40001"), response.body());
  }

  @Test
  @DisplayName("Bean Validation 原因码标准化：@NotBlank → fields=required（BE-09）")
  void bodyValidationReasonIsCanonical() throws Exception {
    // BE-09：校验异常 → 标准信封的唯一映射入口——fields 的值必须是词表原因码
    // （FieldReasons：required/tooLong/tooSmall/tooLarge/pattern/invalid/duplicate），
    // 不再透传注解默认文案（"不能为空" 之类不可机判）。
    HttpResponse<String> response = send("POST", "/api/v1/session", "{\"account\":\"\",\"password\":\"\"}", null);
    assertEquals(422, response.statusCode(), response.body());
    var fields = json.readTree(response.body()).at("/error/fields");
    assertEquals("required", fields.at("/account").asText(), response.body());
    assertEquals("required", fields.at("/password").asText(), response.body());
  }

  @Test
  @DisplayName("方法参数校验原因码标准化：@Max → fields=tooLarge（BE-09）")
  void methodParameterReasonIsCanonical() throws Exception {
    String cookie = login("admin", "admin123");
    HttpResponse<String> response = send("GET", "/api/v1/comments?objectType=bug&objectId=1&limit=201", null, cookie);
    assertEquals(422, response.statusCode(), response.body());
    var fields = json.readTree(response.body()).at("/error/fields");
    assertEquals("tooLarge", fields.at("/limit").asText(), response.body());
  }

  @Test
  @DisplayName("请求体不可解析 → 40001（既有映射不回退）")
  void unreadableBody() throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/session", "{not-json", null);
    assertEquals(400, response.statusCode(), response.body());
    assertTrue(response.body().contains("40001"), response.body());
  }

  @Test
  @DisplayName("重复 sort → 42201 字段级 fields.steps=duplicate（BE-19）")
  void duplicateStepSortIsFieldLevel() throws Exception {
    // BE-19：请求直给的步骤 sort 重复不该落到唯一键兜底的通用 42201（fields 空）——
    // 应由 normalizeSteps 字段级守卫给出 fields.steps=duplicate，用户才知道是哪个字段错。
    String cookie = login("admin", "admin123");
    long productId = createProduct(cookie, "BE-19 重复 sort-" + System.nanoTime());

    HttpResponse<String> duplicate = send("POST", "/api/v1/products/" + productId + "/test-cases",
        "{\"title\":\"重复 sort 字段级\",\"needReview\":false,\"steps\":"
            + "[{\"sort\":1,\"description\":\"第一步\"},{\"sort\":1,\"description\":\"第二步\"}]}",
        cookie);
    assertEquals(422, duplicate.statusCode(), duplicate.body());
    var fields = json.readTree(duplicate.body()).at("/error/fields");
    assertEquals("duplicate", fields.at("/steps").asText(), duplicate.body());
  }

  @Test
  @DisplayName("唯一约束冲突 → 42201，不是 50001（T55）")
  void duplicateUniqueKey() throws Exception {
    // 顺序可达的重复：步骤 sort 由请求给出且不去重（TestCaseFields.normalizeSteps），
    // uk_case_step(case_id, sort) 是唯一防线 → 真 DuplicateKeyException。
    // 其余写路径都有 check-then-act 守卫，只有并发窗口可达，由 DuplicateKeyMappingTest 的单测覆盖。
    String cookie = login("admin", "admin123");
    long productId = createProduct(cookie, "T55 重复键-" + System.nanoTime());

    HttpResponse<String> duplicate = send("POST", "/api/v1/products/" + productId + "/test-cases",
        "{\"title\":\"重复 sort\",\"needReview\":false,\"steps\":"
            + "[{\"sort\":1,\"description\":\"第一步\"},{\"sort\":1,\"description\":\"第二步\"}]}",
        cookie);
    assertEquals(422, duplicate.statusCode(), duplicate.body());
    assertTrue(duplicate.body().contains("42201"), duplicate.body());
  }
}
