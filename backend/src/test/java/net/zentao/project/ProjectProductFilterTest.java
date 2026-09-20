package net.zentao.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * B-PRD-01 项目列表按产品过滤：filters[productId] 经 project_product 反查展开为 id IN（
 * 写法同 org 域 @myDepartment 的「条款剥离 + 服务端展开」邻例）；等值/逗号 IN/无关联产品三种形态。
 */
class ProjectProductFilterTest extends net.zentao.ApiTestSupport {

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  @Test
  @DisplayName("filters[productId]：等值命中该产品关联项目、逗号 IN 并集、无关联产品恒空")
  void filterByProduct() throws Exception {
    long productA = createProduct(admin, "过滤产品A");
    long productB = createProduct(admin, "过滤产品B");
    long projectA = createProject(admin, "过滤项目A", productA, null);
    long projectB = createProject(admin, "过滤项目B", productB, null);
    long projectBoth = dataId(send("POST", "/api/v1/projects",
        "{\"name\":\"双产品项目\",\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-12-31\","
            + "\"productIds\":[" + productA + "," + productB + "]}",
        admin));
    createProduct(admin, "无关联产品"); // 故意不建项目

    JsonNode byA = data(send("GET", "/api/v1/projects?filters%5BproductId%5D=" + productA, null, admin));
    assertEquals(2, byA.at("/total").asLong(), byA.toString());
    assertTrue(containsProject(byA, projectA), byA.toString());
    assertTrue(containsProject(byA, projectBoth), byA.toString());
    assertTrue(!containsProject(byA, projectB), "他产品项目不得命中：" + byA);

    JsonNode byBoth = data(send("GET", "/api/v1/projects?filters%5BproductId%5D=" + productA + "," + productB, null,
        admin));
    assertEquals(3, byBoth.at("/total").asLong(), byBoth.toString());

    JsonNode none = data(send("GET", "/api/v1/projects?filters%5BproductId%5D=99999999", null, admin));
    assertEquals(0, none.at("/total").asLong(), none.toString());
  }

  private static boolean containsProject(JsonNode list, long projectId) {
    for (JsonNode item : list.at("/items")) {
      if (item.at("/id").asLong() == projectId) {
        return true;
      }
    }
    return false;
  }
}
