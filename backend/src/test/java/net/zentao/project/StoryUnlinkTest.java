package net.zentao.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * B-PRJ-06 解除关联（project 卡 §5 DELETE stories 行）：
 * 执行侧 unlink 只删执行自身 project_story 行（项目级行不动），解除后执行需求集回落到项目行；
 * 项目侧 unlink 后项目/执行需求集皆空；两侧均幂等（行不存在也 200 data:null）。
 *
 * <p>执行级关联行无 API 写入口（契约只开项目级 POST），执行自身行经 JdbcTemplate 直插（链接表硬删，无软删列）。
 */
class StoryUnlinkTest extends net.zentao.ApiTestSupport {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  @Test
  @DisplayName("解除执行关联：只删执行自身行，需求集回落到项目行；项目级解除后两侧皆空；幂等")
  void unlinkMatrix() throws Exception {
    long product = createProduct(admin, "解除关联产品");
    long project = createProject(admin, "解除关联项目", product, null);
    long story = createStory(admin, product, "被解除需求", null);
    long execution = createExecution(admin, project, "解除关联执行");
    assertEquals(200, send("POST", "/api/v1/projects/" + project + "/stories",
        "{\"storyIds\":[" + story + "]}", admin).statusCode());
    jdbcTemplate.update(
        "INSERT INTO project_story (project_id, story_id, product_id, sort) VALUES (?, ?, ?, 0)",
        execution, story, product);

    assertEquals(Set.of(story), storyIdsOf(project, "project"), "项目级关联在位");
    assertEquals(Set.of(story), storyIdsOf(execution, "execution"), "执行集 = 自身行 ∪ 项目行");

    // 执行侧解除：删执行自身行；项目级行不动 → 执行集仍含该需求（来自项目行）
    assertEquals(200, send("DELETE", "/api/v1/executions/" + execution + "/stories/" + story, null, admin)
        .statusCode());
    assertEquals(Set.of(story), storyIdsOf(project, "project"), "项目级行不受执行解除影响");
    assertEquals(Set.of(story), storyIdsOf(execution, "execution"), "执行集回落到项目行");
    assertEquals(0, jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM project_story WHERE project_id = ? AND story_id = ?", Long.class, execution, story),
        "执行自身行已删");

    // 幂等：执行侧行已不存在，再删仍 200
    assertEquals(200, send("DELETE", "/api/v1/executions/" + execution + "/stories/" + story, null, admin)
        .statusCode());

    // 项目侧解除：项目集空；执行集 = 空 ∪ 空 = 空
    assertEquals(200, send("DELETE", "/api/v1/projects/" + project + "/stories/" + story, null, admin).statusCode());
    assertEquals(Set.of(), storyIdsOf(project, "project"), "项目解除后需求集为空");
    assertEquals(Set.of(), storyIdsOf(execution, "execution"), "执行集随项目行为空");
    assertEquals(200, send("DELETE", "/api/v1/projects/" + project + "/stories/" + story, null, admin).statusCode(),
        "项目侧幂等");

    // 关联全清后项目可删（删除守卫不再被 project_story 挡）
    HttpResponse<String> blocked = send("DELETE", "/api/v1/projects/" + project, null, admin);
    assertEquals(422, blocked.statusCode(), "仍有执行 → 42203");
    assertTrue(blocked.body().contains("42203"), blocked.body());
  }

  private Set<Long> storyIdsOf(long objectId, String kind) throws Exception {
    JsonNode list = data(send("GET", "/api/v1/" + kind + "s/" + objectId + "/stories", null, admin));
    Set<Long> ids = new LinkedHashSet<>();
    for (JsonNode item : list.at("/items")) {
      ids.add(item.at("/id").asLong());
    }
    return ids;
  }
}
