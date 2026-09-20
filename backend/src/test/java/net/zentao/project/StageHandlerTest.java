package net.zentao.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-6 阶段类型字典（project 卡 §3.2/§5/§8）：同 projectModel 下 percent 累计 >100 → 42201（field=percent）、
 * PATCH 白名单、软删后列表不可见、列表 DSL。
 */
class StageHandlerTest extends net.zentao.ApiTestSupport {

  private String adminCookie;

  @BeforeEach
  void login() throws Exception {
    adminCookie = login("admin", "admin123");
  }

  /** 阶段字典是全局字典（percent 累计跨用例），本类用例开跑前清空既有阶段。 */
  private void clearStages() throws Exception {
    JsonNode items = data(send("GET", "/api/v1/stages?limit=200", null, adminCookie)).at("/items");
    for (JsonNode item : items) {
      assertEquals(200, send("DELETE", "/api/v1/stages/" + item.at("/id").asLong(), null, adminCookie).statusCode());
    }
  }

  private HttpResponse<String> createStage(String name, String percent, String type) throws Exception {
    return send("POST", "/api/v1/stages",
        "{\"name\":\"" + name + "\",\"percent\":" + percent + ",\"type\":\"" + type
            + "\",\"projectModel\":\"waterfall\"}",
        adminCookie);
  }

  @Test
  @DisplayName("percent 累计：同 projectModel 累计 >100 → 42201 field=percent；PATCH 排除自身")
  void percentAccumulation() throws Exception {
    clearStages();
    long request = dataId(createStage("需求", "60", "request"));
    assertEquals(200, createStage("设计", "40", "design").statusCode(), "累计 =100 允许");

    HttpResponse<String> over = createStage("开发", "10", "dev");
    assertEquals(422, over.statusCode(), over.body());
    assertTrue(over.body().contains("42201"), over.body());
    assertTrue(over.body().contains("percent"), over.body());

    HttpResponse<String> patchOver = send("PATCH", "/api/v1/stages/" + request, "{\"percent\":70}", adminCookie);
    assertEquals(422, patchOver.statusCode(), patchOver.body());
    assertTrue(patchOver.body().contains("percent"), patchOver.body());

    JsonNode patched = data(send("PATCH", "/api/v1/stages/" + request,
        "{\"name\":\"需求分析\",\"percent\":55,\"type\":\"review\",\"sort\":3}", adminCookie));
    assertEquals("需求分析", patched.at("/name").asText(), patched.toString());
    assertEquals(55.0, patched.at("/percent").asDouble(), 0.001, patched.toString());
    assertEquals("review", patched.at("/type").asText(), patched.toString());
    assertEquals(3, patched.at("/sort").asInt(), patched.toString());
    assertEquals("waterfall", patched.at("/projectModel").asText(), patched.toString());
  }

  @Test
  @DisplayName("percent 越界与缺必填 → 42201；软删后列表不可见")
  void validationAndSoftDelete() throws Exception {
    clearStages();
    HttpResponse<String> outOfRange = createStage("越界", "101", "other");
    assertEquals(422, outOfRange.statusCode(), outOfRange.body());
    assertTrue(outOfRange.body().contains("percent"), outOfRange.body());

    HttpResponse<String> missing = send("POST", "/api/v1/stages",
        "{\"name\":\"缺百分比\",\"type\":\"other\",\"projectModel\":\"waterfall\"}", adminCookie);
    assertEquals(422, missing.statusCode(), missing.body());
    assertTrue(missing.body().contains("percent"), missing.body());

    long stage = dataId(createStage("待删阶段", "20", "mix"));
    JsonNode before = data(send("GET", "/api/v1/stages?filters%5Btype%5D=mix", null, adminCookie));
    assertEquals(1, before.at("/items").size(), before.toString());

    assertEquals(200, send("DELETE", "/api/v1/stages/" + stage, null, adminCookie).statusCode());
    JsonNode after = data(send("GET", "/api/v1/stages?filters%5Btype%5D=mix", null, adminCookie));
    assertEquals(0, after.at("/total").asLong(), after.toString());
    assertEquals(404, send("PATCH", "/api/v1/stages/" + stage, "{\"name\":\"复活\"}", adminCookie).statusCode());
  }

  @Test
  @DisplayName("列表 DSL：filters[projectModel]/q 命中，白名单外字段 40001")
  void listDsl() throws Exception {
    clearStages();
    dataId(createStage("瀑布需求", "10", "request"));

    JsonNode byModel = data(send("GET", "/api/v1/stages?filters%5BprojectModel%5D=waterfall", null, adminCookie));
    assertEquals(1, byModel.at("/total").asLong(), byModel.toString());

    JsonNode searched = data(send("GET", "/api/v1/stages?q=瀑布", null, adminCookie));
    assertEquals(1, searched.at("/items").size(), searched.toString());
    assertEquals("瀑布需求", searched.at("/items/0/name").asText(), searched.toString());

    HttpResponse<String> unregistered = send("GET", "/api/v1/stages?filters%5Bghost%5D=1", null, adminCookie);
    assertEquals(400, unregistered.statusCode(), unregistered.body());
    assertTrue(unregistered.body().contains("40001"), unregistered.body());
  }
}
