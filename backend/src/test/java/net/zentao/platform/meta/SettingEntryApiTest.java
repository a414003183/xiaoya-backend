package net.zentao.platform.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 参数管理（T15 P1-3）端到端：系统的 setting 行增删改查 + 读取方即时可见 + 写入约束。
 *
 * <p>本卡的真验收是「改完立刻生效」：`SettingQueryService` 每请求查库、无缓存，故用
 * `GET /settings?keys=` 复核——不是页面自说自话。
 * 同 JVM 共享 H2，故每个用例用自己唯一的键，不依赖全局行数。
 */
class SettingEntryApiTest extends ApiTestSupport {

  @Test
  @DisplayName("增改查删一条链：值即时被 GET /settings 读到，删掉后键消失")
  void crudRoundTrip() throws Exception {
    String cookie = login("admin", "admin123");
    String key = "t15.roundTrip" + System.nanoTime() % 100000000;

    JsonNode created = data(send("POST", "/api/v1/setting-entries",
        "{\"key\":\"" + key + "\",\"value\":\"\\\"Asia/Shanghai\\\"\"}", cookie));
    assertEquals(key, created.get("key").asText());
    assertEquals("t15", created.get("domain").asText(), "扁平键按首个点拆域：" + created);
    assertEquals("\"Asia/Shanghai\"", created.get("value").asText(), "JSON 文本原样往返");

    // 读取方（设置页走的同一个 Service）立刻读到
    JsonNode read = data(send("GET", "/api/v1/settings?keys=" + key, null, cookie));
    assertEquals("Asia/Shanghai", read.at("/settings").get(key).asText(), read.toString());
    // 列表里也看得到（分页列表恒为系统行）
    JsonNode list = data(send("GET", "/api/v1/setting-entries?filters%5Bdomain%5D=t15&q=roundTrip", null, cookie));
    assertEquals(1, list.get("total").asInt(), list.toString());

    JsonNode updated = data(send("PATCH", "/api/v1/setting-entries/" + key,
        "{\"value\":\"\\\"UTC\\\"\"}", cookie));
    assertEquals("\"UTC\"", updated.get("value").asText());
    assertEquals("UTC", data(send("GET", "/api/v1/settings?keys=" + key, null, cookie))
        .at("/settings").get(key).asText(), "改完立刻生效");

    assertEquals(200, send("DELETE", "/api/v1/setting-entries/" + key, null, cookie).statusCode());
    JsonNode afterDelete = data(send("GET", "/api/v1/settings?keys=" + key, null, cookie));
    assertTrue(afterDelete.at("/settings").isMissingNode() || afterDelete.at("/settings").isEmpty(),
        "删掉后读取方不再返回该键：" + afterDelete);
    assertEquals(404, send("PATCH", "/api/v1/setting-entries/" + key, "{\"value\":\"1\"}", cookie).statusCode());
    assertEquals(404, send("DELETE", "/api/v1/setting-entries/" + key, null, cookie).statusCode(),
        "再删一次 404：页面上的行已过期，该提示而不是假装成功");
  }

  @Test
  @DisplayName("写入约束：键重复/键格式/值非 JSON → 42201；个人偏好行不进这个列表")
  void writeGuards() throws Exception {
    String cookie = login("admin", "admin123");
    String key = "t15.guard" + System.nanoTime() % 100000000;
    String body = "{\"key\":\"" + key + "\",\"value\":\"1\"}";
    assertEquals(200, send("POST", "/api/v1/setting-entries", body, cookie).statusCode());

    HttpResponse<String> duplicate = send("POST", "/api/v1/setting-entries", body, cookie);
    assertEquals(422, duplicate.statusCode(), duplicate.body());
    assertTrue(duplicate.body().contains("\"key\":\"duplicate\""), duplicate.body());

    HttpResponse<String> badKey = send("POST", "/api/v1/setting-entries", "{\"key\":\"NoDomainDot\",\"value\":\"1\"}", cookie);
    assertEquals(422, badKey.statusCode(), badKey.body());
    HttpResponse<String> upperDomain = send("POST", "/api/v1/setting-entries",
        "{\"key\":\"Bad.Domain\",\"value\":\"1\"}", cookie);
    assertEquals(422, upperDomain.statusCode(), "域段必须小写字母开头：" + upperDomain.body());

    HttpResponse<String> badValue = send("PATCH", "/api/v1/setting-entries/" + key, "{\"value\":\"Asia/Shanghai\"}", cookie);
    assertEquals(422, badValue.statusCode(), badValue.body());
    assertTrue(badValue.body().contains("\"value\":\"invalidJson\""), badValue.body());
    // 非法值被挡在门外：原值没被写坏
    assertTrue(data(send("GET", "/api/v1/settings?keys=" + key, null, cookie))
        .at("/settings").toString().contains("1"), "旧值原样保留");

    // 个人偏好行（owner=账号）不属这个页面：notify.* 写进去的是个人行，列表里查不到
    assertEquals(200, send("PUT", "/api/v1/settings", "{\"settings\":{\"notify.t15probe\":true}}", cookie).statusCode());
    JsonNode personal = data(send("GET", "/api/v1/setting-entries?filters%5Bdomain%5D=notify&q=t15probe", null, cookie));
    assertEquals(0, personal.get("total").asInt(), "个人偏好行不进参数管理列表：" + personal);
    assertFalse(personal.get("items").elements().hasNext(), personal.toString());
  }

  @Test
  @DisplayName("权限：无 setting-manage 的账号既读不到列表也改不了参数（40301）")
  void privilegeGate() throws Exception {
    String adminCookie = login("admin", "admin123");
    String cookie = accountWithPrivileges(adminCookie, "t15-nopriv-" + System.nanoTime() % 100000000, "\"account-view\"");

    assertEquals(403, send("GET", "/api/v1/setting-entries", null, cookie).statusCode());
    assertEquals(403, send("POST", "/api/v1/setting-entries", "{\"key\":\"t15.x\",\"value\":\"1\"}", cookie).statusCode());
    String forbidden = send("DELETE", "/api/v1/setting-entries/t15.x", null, cookie).body();
    assertTrue(forbidden.contains("40301"), forbidden);
  }
}
