package net.zentao.requirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * B-REQ-02 parentId 可改（requirement 卡 §5 PATCH 补口）：改父成功、指向自身/成环 42203、
 * 跨产品/非 epic/不存在 42201、0 清空为独立需求、null 不修改。
 */
class StoryParentChangeTest extends net.zentao.ApiTestSupport {

  private String admin;
  private long productId;

  @BeforeEach
  void prepare() throws Exception {
    admin = login("admin", "admin123");
    productId = dataId(send("POST", "/api/v1/products",
        "{\"name\":\"改父产品-" + System.nanoTime() + "\"}", admin));
  }

  private long createStory(String extraJson) throws Exception {
    return dataId(send("POST", "/api/v1/products/" + productId + "/stories",
        "{\"title\":\"改父需求\"" + (extraJson == null ? "" : "," + extraJson) + "}", admin));
  }

  /** PATCH 成功返回 data，失败原样返回响应（断言用）。 */
  private HttpResponse<String> patchParent(long storyId, String body) throws Exception {
    JsonNode story = data(send("GET", "/api/v1/stories/" + storyId, null, admin));
    return send("PATCH", "/api/v1/stories/" + storyId,
        "{\"parentId\":" + body + ",\"lockVersion\":" + story.at("/lockVersion").asInt() + "}", admin);
  }

  @Test
  @DisplayName("改父成功 → parentId 生效；0 清空为独立需求；缺省键不修改")
  void changeAndClear() throws Exception {
    long epic = createStory("\"type\":\"epic\"");
    long story = createStory(null);

    assertEquals(200, patchParent(story, String.valueOf(epic)).statusCode());
    assertEquals(epic, data(send("GET", "/api/v1/stories/" + story, null, admin)).at("/parentId").asLong());

    assertEquals(200, patchParent(story, "0").statusCode());
    assertTrue(data(send("GET", "/api/v1/stories/" + story, null, admin)).at("/parentId").isNull());

    JsonNode latest = data(send("GET", "/api/v1/stories/" + story, null, admin));
    assertEquals(200, send("PATCH", "/api/v1/stories/" + story,
        "{\"title\":\"改名不改父\",\"lockVersion\":" + latest.at("/lockVersion").asInt() + "}", admin).statusCode());
    assertTrue(data(send("GET", "/api/v1/stories/" + story, null, admin)).at("/parentId").isNull(),
        "parentId 缺省（null）不修改");
  }

  @Test
  @DisplayName("指向自身 → 42203；沿 parent 链成环 → 42203")
  void selfAndCycleGuards() throws Exception {
    long story = createStory(null);
    HttpResponse<String> self = patchParent(story, String.valueOf(story));
    assertEquals(422, self.statusCode(), self.body());
    assertTrue(self.body().contains("42203"), self.body());

    long epicA = createStory("\"type\":\"epic\"");
    long epicB = createStory("\"type\":\"epic\"");
    assertEquals(200, patchParent(epicA, String.valueOf(epicB)).statusCode());
    HttpResponse<String> cycle = patchParent(epicB, String.valueOf(epicA));
    assertEquals(422, cycle.statusCode(), cycle.body());
    assertTrue(cycle.body().contains("42203"), cycle.body());
  }

  @Test
  @DisplayName("跨产品/非 epic/不存在 → 42201")
  void referenceValidation() throws Exception {
    long story = createStory(null);
    long plain = createStory(null);

    HttpResponse<String> notEpic = patchParent(story, String.valueOf(plain));
    assertEquals(422, notEpic.statusCode(), notEpic.body());
    assertTrue(notEpic.body().contains("42201"), notEpic.body());

    long otherProduct = dataId(send("POST", "/api/v1/products",
        "{\"name\":\"改父他产品-" + System.nanoTime() + "\"}", admin));
    long foreignEpic = dataId(send("POST", "/api/v1/products/" + otherProduct + "/stories",
        "{\"title\":\"他产品 epic\",\"type\":\"epic\"}", admin));
    HttpResponse<String> cross = patchParent(story, String.valueOf(foreignEpic));
    assertEquals(422, cross.statusCode(), cross.body());
    assertTrue(cross.body().contains("42201"), cross.body());

    HttpResponse<String> missing = patchParent(story, "999999999");
    assertEquals(422, missing.statusCode(), missing.body());
    assertTrue(missing.body().contains("42201"), missing.body());
  }
}
