package net.zentao.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A-07 三型软删（project 卡 §5 DELETE 行）：program/project/execution 守卫矩阵——
 * program 有子 program/project → 42203、project 有执行或关联需求 → 42203、execution 有未删任务 → 42203；
 * 删除成功后详情 40401，重复删除 40401。
 */
class ProjectDeleteTest extends net.zentao.ApiTestSupport {

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  @Test
  @DisplayName("program：有子 program 或子 project → 42203；空 program 删除成功且详情 40401、重复删 40401")
  void programDelete() throws Exception {
    long parent = createProgram("删除守卫项目集", null);

    long childProgram = createProgram("子项目集", parent);
    assertGuardBlocked(send("DELETE", "/api/v1/programs/" + parent, null, admin), "有子项目集");
    assertEquals(200, send("DELETE", "/api/v1/programs/" + childProgram, null, admin).statusCode(),
        "先删子项目集解守卫");

    long product = createProduct(admin, "项目集产品");
    long childProject = createProject(admin, "项目集下项目", product, "\"parentId\":" + parent);
    assertGuardBlocked(send("DELETE", "/api/v1/programs/" + parent, null, admin), "有子项目");
    assertEquals(200, send("DELETE", "/api/v1/projects/" + childProject, null, admin).statusCode(), "先删子项目解守卫");

    assertEquals(200, send("DELETE", "/api/v1/programs/" + parent, null, admin).statusCode(), "子对象清空后可删");
    assertGone("/api/v1/programs/" + parent, "删后详情");
    assertGoneDelete("/api/v1/programs/" + parent, "重复删除");
  }

  @Test
  @DisplayName("project：有执行或关联需求 → 42203；两者皆无 → 删除成功")
  void projectDelete() throws Exception {
    long product = createProduct(admin, "删除产品");
    long withExecution = createProject(admin, "有执行项目", product, null);
    createExecution(admin, withExecution, "挡删除执行");
    assertGuardBlocked(send("DELETE", "/api/v1/projects/" + withExecution, null, admin), "有执行");

    long withStory = createProject(admin, "有关联需求项目", product, null);
    long story = createStory(admin, product, "挡删除需求", null);
    assertEquals(200, send("POST", "/api/v1/projects/" + withStory + "/stories",
        "{\"storyIds\":[" + story + "]}", admin).statusCode());
    assertGuardBlocked(send("DELETE", "/api/v1/projects/" + withStory, null, admin), "有关联需求");

    long free = createProject(admin, "可删项目", product, null);
    assertEquals(200, send("DELETE", "/api/v1/projects/" + free, null, admin).statusCode());
    assertGone("/api/v1/projects/" + free, "删后详情");
    assertGoneDelete("/api/v1/projects/" + free, "重复删除");
  }

  @Test
  @DisplayName("execution：存在未删任务 → 42203；无任务 → 删除成功且详情 40401")
  void executionDelete() throws Exception {
    long product = createProduct(admin, "执行删除产品");
    long project = createProject(admin, "执行删除项目", product, null);
    long withTask = createExecution(admin, project, "有任务执行");
    assertEquals(200,
        send("POST", "/api/v1/executions/" + withTask + "/tasks", "{\"title\":\"挡删除任务\"}", admin).statusCode());
    assertGuardBlocked(send("DELETE", "/api/v1/executions/" + withTask, null, admin), "有任务");

    long free = createExecution(admin, project, "无任务执行");
    assertEquals(200, send("DELETE", "/api/v1/executions/" + free, null, admin).statusCode());
    assertGone("/api/v1/executions/" + free, "删后详情");
    assertGoneDelete("/api/v1/executions/" + free, "重复删除");
  }

  private long createProgram(String name, Long parentId) throws Exception {
    return dataId(send("POST", "/api/v1/programs",
        "{\"name\":\"" + name + "\"" + (parentId == null ? "" : ",\"parentId\":" + parentId) + "}", admin));
  }

  private void assertGuardBlocked(HttpResponse<String> response, String action) throws Exception {
    assertEquals(422, response.statusCode(), action + " → " + response.body());
    assertTrue(response.body().contains("42203"), action + " → " + response.body());
  }

  private void assertGone(String path, String action) throws Exception {
    HttpResponse<String> detail = send("GET", path, null, admin);
    assertEquals(404, detail.statusCode(), action + " → " + detail.body());
    assertTrue(detail.body().contains("40401"), action + " → " + detail.body());
  }

  private void assertGoneDelete(String path, String action) throws Exception {
    HttpResponse<String> again = send("DELETE", path, null, admin);
    assertEquals(404, again.statusCode(), action + " → " + again.body());
    assertTrue(again.body().contains("40401"), action + " → " + again.body());
  }
}
