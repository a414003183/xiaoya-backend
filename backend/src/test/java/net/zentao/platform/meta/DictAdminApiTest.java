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
 * 字典管理（T16 P1-4）端到端：类型与数据项 CRUD + **读取侧即时可见** + 写入约束。
 *
 * <p>本卡的真验收是「建完就能被下拉读到」：DB 字典是 `GET /dicts/{code}` 的第二来源（代码注册优先），
 * 故用读取端点复核，而不是只看管理列表。同 JVM 共享 H2，故每个用例自建唯一 code。
 */
class DictAdminApiTest extends ApiTestSupport {

  @Test
  @DisplayName("建类型 + 两条数据项 → GET /dicts/{code} 按 sortNo 出 {value,label}；停用/删除即时生效")
  void crudReadsThroughToDictEndpoint() throws Exception {
    String cookie = login("admin", "admin123");
    String code = "t16-demo-" + System.nanoTime() % 100000000;

    JsonNode type = data(send("POST", "/api/v1/dict-types",
        "{\"code\":\"" + code + "\",\"name\":\"演示等级\"}", cookie));
    assertEquals(code, type.get("code").asText());
    assertEquals("active", type.get("status").asText(), "缺省状态 active：" + type);

    long high = data(send("POST", "/api/v1/dict-types/" + code + "/items",
        "{\"itemLabel\":\"高\",\"itemValue\":\"high\",\"sortNo\":20}", cookie)).get("id").asLong();
    data(send("POST", "/api/v1/dict-types/" + code + "/items",
        "{\"itemLabel\":\"低\",\"itemValue\":\"low\",\"sortNo\":10}", cookie));

    // 读取侧（下拉数据源）立刻拿到两条，且按 sortNo 升序
    JsonNode dict = data(send("GET", "/api/v1/dicts/" + code, null, cookie));
    assertEquals(2, dict.get("items").size(), dict.toString());
    assertEquals("low", dict.at("/items/0/value").asText(), "sortNo 小的在前：" + dict);
    assertEquals("低", dict.at("/items/0/label").asText(), "label 是管理员填的字面文案：" + dict);
    assertEquals("high", dict.at("/items/1/value").asText());

    // 改标签 → 读取侧立刻是新文案
    assertEquals(200, send("PATCH", "/api/v1/dict-items/" + high, "{\"itemLabel\":\"高（改）\"}", cookie).statusCode());
    assertEquals("高（改）", data(send("GET", "/api/v1/dicts/" + code, null, cookie)).at("/items/1/label").asText());

    // 停用一条 → 读取侧只剩一条
    assertEquals(200, send("PATCH", "/api/v1/dict-items/" + high, "{\"status\":\"disabled\"}", cookie).statusCode());
    JsonNode afterDisableItem = data(send("GET", "/api/v1/dicts/" + code, null, cookie));
    assertEquals(1, afterDisableItem.get("items").size(), afterDisableItem.toString());

    // 停用类型 → 读取侧 404（等于这个字典没了）
    assertEquals(200, send("PATCH", "/api/v1/dict-types/" + code, "{\"status\":\"disabled\"}", cookie).statusCode());
    assertEquals(404, send("GET", "/api/v1/dicts/" + code, null, cookie).statusCode());

    // 删类型 → 级联删数据项；重建同名类型时数据项列表是空的
    assertEquals(200, send("DELETE", "/api/v1/dict-types/" + code, null, cookie).statusCode());
    assertEquals(404, send("DELETE", "/api/v1/dict-types/" + code, null, cookie).statusCode(), "再删 404");
    assertEquals(200, send("POST", "/api/v1/dict-types",
        "{\"code\":\"" + code + "\",\"name\":\"演示等级\"}", cookie).statusCode());
    JsonNode items = data(send("GET", "/api/v1/dict-types/" + code + "/items", null, cookie));
    assertEquals(0, items.get("total").asInt(), "级联删除后重建，数据项不该还在：" + items);
  }

  @Test
  @DisplayName("写入约束：撞内置字典名/格式非法/重复 code/同类型值重复 → 42201；类型不存在 → 40401")
  void writeGuards() throws Exception {
    String cookie = login("admin", "admin123");
    String code = "t16-guard-" + System.nanoTime() % 100000000;

    HttpResponse<String> reserved = send("POST", "/api/v1/dict-types",
        "{\"code\":\"timezones\",\"name\":\"撞内置名\"}", cookie);
    assertEquals(422, reserved.statusCode(), reserved.body());
    assertTrue(reserved.body().contains("\"code\":\"duplicate\""), reserved.body());

    HttpResponse<String> badCode = send("POST", "/api/v1/dict-types", "{\"code\":\"Bad_Code\",\"name\":\"x\"}", cookie);
    assertEquals(422, badCode.statusCode(), badCode.body());
    assertTrue(badCode.body().contains("\"code\":\"pattern\""), badCode.body());

    assertEquals(200, send("POST", "/api/v1/dict-types",
        "{\"code\":\"" + code + "\",\"name\":\"守卫\"}", cookie).statusCode());
    HttpResponse<String> duplicate = send("POST", "/api/v1/dict-types",
        "{\"code\":\"" + code + "\",\"name\":\"守卫2\"}", cookie);
    assertEquals(422, duplicate.statusCode(), duplicate.body());

    assertEquals(404, send("POST", "/api/v1/dict-types/t16-missing-" + code + "/items",
        "{\"itemLabel\":\"a\",\"itemValue\":\"a\"}", cookie).statusCode(), "类型不存在先 404");
    assertEquals(200, send("POST", "/api/v1/dict-types/" + code + "/items",
        "{\"itemLabel\":\"甲\",\"itemValue\":\"a\"}", cookie).statusCode());
    HttpResponse<String> sameValue = send("POST", "/api/v1/dict-types/" + code + "/items",
        "{\"itemLabel\":\"乙\",\"itemValue\":\"a\"}", cookie);
    assertEquals(422, sameValue.statusCode(), sameValue.body());
    assertTrue(sameValue.body().contains("\"itemValue\":\"duplicate\""), sameValue.body());

    HttpResponse<String> badStatus = send("PATCH", "/api/v1/dict-types/" + code, "{\"status\":\"paused\"}", cookie);
    assertEquals(422, badStatus.statusCode(), badStatus.body());
    assertEquals(404, send("PATCH", "/api/v1/dict-items/999999999", "{\"itemLabel\":\"x\"}", cookie).statusCode());
    assertEquals(404, send("DELETE", "/api/v1/dict-items/999999999", null, cookie).statusCode());
  }

  @Test
  @DisplayName("权限：无 setting-manage 既进不了管理面也建不了数据项（40301）")
  void privilegeGate() throws Exception {
    String adminCookie = login("admin", "admin123");
    String cookie = accountWithPrivileges(adminCookie, "t16-nopriv-" + System.nanoTime() % 100000000, "\"account-view\"");

    assertEquals(403, send("GET", "/api/v1/dict-types", null, cookie).statusCode());
    assertEquals(403, send("POST", "/api/v1/dict-types", "{\"code\":\"t16-x\",\"name\":\"x\"}", cookie).statusCode());
    assertFalse(send("GET", "/api/v1/dict-types", null, cookie).body().isEmpty());
  }
}
