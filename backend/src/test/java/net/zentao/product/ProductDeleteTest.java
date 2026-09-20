package net.zentao.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A-07 软删（product 卡 §5/§8）：product/branch/plan/release 四个 DELETE。
 * product 守卫 = 未删 story/branch/plan/release/build 任一 → 42203；branch/plan 守卫 = 未删需求指向；
 * release 叶子直删；删后详情/重复删 40401。
 */
class ProductDeleteTest extends net.zentao.ApiTestSupport {

  private String admin;
  private long productId;

  @BeforeEach
  void prepare() throws Exception {
    admin = login("admin", "admin123");
    productId = freshProduct(null);
  }

  private long freshProduct(String extraJson) throws Exception {
    return dataId(send("POST", "/api/v1/products",
        "{\"name\":\"删除产品-" + System.nanoTime() + "\"" + (extraJson == null ? "" : "," + extraJson) + "}",
        admin));
  }

  private HttpResponse<String> delete(String path) throws Exception {
    return send("DELETE", path, null, admin);
  }

  private long createStory(long productId, String extraJson) throws Exception {
    return dataId(send("POST", "/api/v1/products/" + productId + "/stories",
        "{\"title\":\"守卫需求\"" + (extraJson == null ? "" : "," + extraJson) + "}", admin));
  }

  @Test
  @DisplayName("product 守卫：story/branch/plan/release/build 任一未删 → 42203；空产品删成功、重复删 40401")
  void productGuards() throws Exception {
    long withStory = freshProduct(null);
    createStory(withStory, null);
    assertEquals(422, delete("/api/v1/products/" + withStory).statusCode(), "story 未删 → 42203");

    long branchProduct = freshProduct("\"type\":\"branch\"");
    dataId(send("POST", "/api/v1/products/" + branchProduct + "/branches", "{\"name\":\"守卫分支\"}", admin));
    assertTrue(delete("/api/v1/products/" + branchProduct).body().contains("42203"), "branch 未删 → 42203");

    long withPlan = freshProduct(null);
    dataId(send("POST", "/api/v1/products/" + withPlan + "/plans", "{\"title\":\"守卫计划\"}", admin));
    assertTrue(delete("/api/v1/products/" + withPlan).body().contains("42203"), "plan 未删 → 42203");

    long withRelease = freshProduct(null);
    dataId(send("POST", "/api/v1/products/" + withRelease + "/releases",
        "{\"name\":\"守卫发布\",\"releaseDate\":\"2026-09-18\"}", admin));
    assertTrue(delete("/api/v1/products/" + withRelease).body().contains("42203"), "release 未删 → 42203");

    long withBuild = freshProduct(null);
    dataId(send("POST", "/api/v1/products/" + withBuild + "/builds", "{\"name\":\"守卫构建\"}", admin));
    assertTrue(delete("/api/v1/products/" + withBuild).body().contains("42203"), "build 未删 → 42203");

    HttpResponse<String> ok = delete("/api/v1/products/" + productId);
    assertEquals(200, ok.statusCode(), ok.body());
    assertTrue(json.readTree(ok.body()).at("/data").isNull(), ok.body());
    HttpResponse<String> gone = send("GET", "/api/v1/products/" + productId, null, admin);
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());
    HttpResponse<String> again = delete("/api/v1/products/" + productId);
    assertEquals(404, again.statusCode(), again.body());
    assertTrue(again.body().contains("40401"), again.body());
  }

  @Test
  @DisplayName("branch 守卫：存在未删需求 branchId 指向 → 42203；无指向删成功、重复删 40401")
  void branchGuardAndDelete() throws Exception {
    long branchProduct = freshProduct("\"type\":\"branch\"");
    long branch = dataId(send("POST", "/api/v1/products/" + branchProduct + "/branches",
        "{\"name\":\"占用分支\"}", admin));
    createStory(branchProduct, "\"branchId\":" + branch);
    HttpResponse<String> blocked = delete("/api/v1/branches/" + branch);
    assertEquals(422, blocked.statusCode(), blocked.body());
    assertTrue(blocked.body().contains("42203"), blocked.body());

    long free = dataId(send("POST", "/api/v1/products/" + branchProduct + "/branches",
        "{\"name\":\"自由分支\"}", admin));
    HttpResponse<String> ok = delete("/api/v1/branches/" + free);
    assertEquals(200, ok.statusCode(), ok.body());
    HttpResponse<String> again = delete("/api/v1/branches/" + free);
    assertEquals(404, again.statusCode(), again.body());
    assertTrue(again.body().contains("40401"), again.body());
  }

  @Test
  @DisplayName("plan 守卫：存在未删需求 planId 指向 → 42203；无指向删成功、详情与重复删 40401")
  void planGuardAndDelete() throws Exception {
    long plan = dataId(send("POST", "/api/v1/products/" + productId + "/plans",
        "{\"title\":\"占用计划\"}", admin));
    createStory(productId, "\"planId\":" + plan);
    HttpResponse<String> blocked = delete("/api/v1/plans/" + plan);
    assertEquals(422, blocked.statusCode(), blocked.body());
    assertTrue(blocked.body().contains("42203"), blocked.body());

    long free = dataId(send("POST", "/api/v1/products/" + productId + "/plans",
        "{\"title\":\"自由计划\"}", admin));
    HttpResponse<String> ok = delete("/api/v1/plans/" + free);
    assertEquals(200, ok.statusCode(), ok.body());
    HttpResponse<String> gone = send("GET", "/api/v1/plans/" + free, null, admin);
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());
    HttpResponse<String> again = delete("/api/v1/plans/" + free);
    assertEquals(404, again.statusCode(), again.body());
    assertTrue(again.body().contains("40401"), again.body());
  }

  @Test
  @DisplayName("release 叶子对象：直接软删，详情与重复删 40401")
  void releaseDelete() throws Exception {
    long release = dataId(send("POST", "/api/v1/products/" + productId + "/releases",
        "{\"name\":\"叶子发布\",\"releaseDate\":\"2026-09-18\"}", admin));
    HttpResponse<String> ok = delete("/api/v1/releases/" + release);
    assertEquals(200, ok.statusCode(), ok.body());
    HttpResponse<String> gone = send("GET", "/api/v1/releases/" + release, null, admin);
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());
    HttpResponse<String> again = delete("/api/v1/releases/" + release);
    assertEquals(404, again.statusCode(), again.body());
    assertTrue(again.body().contains("40401"), again.body());
  }

  @Test
  @DisplayName("守卫可解除：删净需求后产品可删（42203 → 200）")
  void guardReleasedAfterStoryDeleted() throws Exception {
    long story = createStory(productId, null);
    assertTrue(delete("/api/v1/products/" + productId).body().contains("42203"));

    assertEquals(200, send("DELETE", "/api/v1/stories/" + story, null, admin).statusCode());
    HttpResponse<String> ok = delete("/api/v1/products/" + productId);
    assertEquals(200, ok.statusCode(), ok.body());
  }
}
