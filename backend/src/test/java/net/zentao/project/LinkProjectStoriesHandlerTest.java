package net.zentao.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.util.LinkedHashSet;
import java.util.Set;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-4 项目/执行关联需求（project 卡 §2/§5/§8）：project_story 幂等关联（重复关联不增行，unique 索引兜底）、
 * 缺失/非本项目产品的需求逐项 42201、项目集/执行取所属项目产品集合、列表按关联反查。
 */
class LinkProjectStoriesHandlerTest extends ApiTestSupport {

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  private Set<Long> storyIdsOf(JsonNode list) {
    Set<Long> ids = new LinkedHashSet<>();
    for (JsonNode item : list.at("/items")) {
      ids.add(item.at("/id").asLong());
    }
    return ids;
  }

  @Test
  @DisplayName("关联需求：逐项结果（合法 ok / 缺失与跨产品 42201）、重复关联不增行、列表反查")
  void linkIsIdempotent() throws Exception {
    long product = createProduct(admin, "关联产品");
    long otherProduct = createProduct(admin, "外部产品");
    long project = createProject(admin, "关联项目", product, null);
    long storyA = createStory(admin, product, "关联需求A", null);
    long storyB = createStory(admin, product, "关联需求B", null);
    long outside = createStory(admin, otherProduct, "外部产品需求", null);

    String url = "/api/v1/projects/" + project + "/stories";
    JsonNode results = data(send("POST", url,
        "{\"storyIds\":[" + storyA + "," + storyB + "," + outside + ",99999999]}", admin));
    assertEquals(4, results.at("/results").size(), results.toString());
    assertTrue(results.at("/results/0/ok").asBoolean(), results.toString());
    assertEquals(storyA, results.at("/results/0/id").asLong(), results.toString());
    assertTrue(results.at("/results/1/ok").asBoolean(), results.toString());
    assertFalse(results.at("/results/2/ok").asBoolean(), results.toString());
    assertTrue(results.at("/results/2/error").asText().startsWith("42201:"), results.toString());
    assertFalse(results.at("/results/3/ok").asBoolean(), results.toString());
    assertTrue(results.at("/results/3/error").asText().startsWith("42201:"), results.toString());

    assertEquals(Set.of(storyA, storyB), storyIdsOf(data(send("GET", url, null, admin))));

    // 幂等：重复关联（含同请求重复 id）不增行——若真插第二行会被 (project_id, story_id) unique 索引拒绝
    JsonNode again = data(send("POST", url, "{\"storyIds\":[" + storyA + "," + storyA + "]}", admin));
    assertEquals(1, again.at("/results").size(), again.toString());
    assertTrue(again.at("/results/0/ok").asBoolean(), again.toString());
    assertEquals(Set.of(storyA, storyB), storyIdsOf(data(send("GET", url, null, admin))));

    // 执行需求集随所属项目承接（契约无执行级关联写端点）：外部产品需求仍不入集
    long execution = createExecution(admin, project, "关联执行");
    String execUrl = "/api/v1/executions/" + execution + "/stories";
    assertEquals(Set.of(storyA, storyB), storyIdsOf(data(send("GET", execUrl, null, admin))));
    JsonNode execFiltered = data(send("GET", execUrl + "?filters%5Bid%5D=" + storyA, null, admin));
    assertEquals(1, execFiltered.at("/total").asLong(), execFiltered.toString());
  }

  @Test
  @DisplayName("关联守卫：storyIds 缺失 42201、空数组无操作、对象不可见 40302、列表支持 filters[status]")
  void guards() throws Exception {
    long product = createProduct(admin, "关联守卫产品");
    long project = createProject(admin, "关联守卫项目", product, "\"acl\":\"private\"");
    long story = createStory(admin, product, "关联守卫需求", null);
    String url = "/api/v1/projects/" + project + "/stories";

    HttpResponse<String> missing = send("POST", url, "{}", admin);
    assertEquals(422, missing.statusCode(), missing.body());
    assertTrue(missing.body().contains("storyIds"), missing.body());

    JsonNode empty = data(send("POST", url, "{\"storyIds\":[]}", admin));
    assertEquals(0, empty.at("/results").size(), empty.toString());

    data(send("POST", url, "{\"storyIds\":[" + story + "]}", admin));
    JsonNode filtered = data(send("GET", url + "?filters%5Bstatus%5D=draft", null, admin));
    assertEquals(1, filtered.at("/total").asLong(), filtered.toString());
    assertEquals(0, data(send("GET", url + "?filters%5Bstatus%5D=closed", null, admin)).at("/total").asLong());

    String outsider = accountWithPrivileges(admin, "t4-link-vis", "\"project-view\"");
    HttpResponse<String> hidden = send("GET", url, null, outsider);
    assertEquals(403, hidden.statusCode(), hidden.body());
    assertTrue(hidden.body().contains("40302"), hidden.body());
    // 有 project-view 无 project-link-story：功能权限码 40301
    HttpResponse<String> noPrivilege = send("POST", url, "{\"storyIds\":[" + story + "]}", outsider);
    assertEquals(403, noPrivilege.statusCode(), noPrivilege.body());
    assertTrue(noPrivilege.body().contains("40301"), noPrivilege.body());
  }
}
