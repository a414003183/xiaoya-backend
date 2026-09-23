package net.zentao.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-2 文档库后端（doc 卡 §3.1/§5/§7/§8）：可见性矩阵（open/private/default/mine 超管不豁免）、
 * mine 幂等、isDefault 二次置位互斥、空库删除守卫、lockVersion 40901、归属对象必填与可见性。
 */
class DocSpaceServiceTest extends ApiTestSupport {

  private static final String A = "docspace-a";
  private static final String B = "docspace-b";
  private static final String C = "docspace-c";
  private static boolean accountsReady;
  private String admin;
  private String cookieA;
  private String cookieB;
  private String cookieC;

  @BeforeEach
  void prepare() throws Exception {
    admin = login("admin", "admin123");
    if (!accountsReady) {
      accountWithPrivileges(admin, A, codes());
      accountWithPrivileges(admin, B, codes());
      accountWithPrivileges(admin, C, codes());
      accountsReady = true;
    }
    cookieA = login(A, "secret123");
    cookieB = login(B, "secret123");
    cookieC = login(C, "secret123");
  }

  private static String codes() {
    return "\"doc-space-view\",\"doc-space-create\",\"doc-space-edit\",\"doc-space-delete\","
        + "\"doc-view\",\"doc-create\",\"doc-edit\",\"doc-delete\"";
  }

  private static String spaceJson(String name, String extra) {
    return "{\"name\":\"" + name + "\"" + (extra == null ? "" : "," + extra) + "}";
  }

  private JsonNode createSpace(String cookie, String json) throws Exception {
    return data(send("POST", "/api/v1/doc-spaces", json, cookie));
  }

  private JsonNode spaces(String cookie) throws Exception {
    return data(send("GET", "/api/v1/doc-spaces?limit=200", null, cookie));
  }

  private boolean listHasSpace(JsonNode list, String name) {
    for (JsonNode item : list.at("/items")) {
      if (name.equals(item.at("/name").asText())) {
        return true;
      }
    }
    return false;
  }

  @Test
  @DisplayName("mine 库每人至多一个：重复创建返回既有库（幂等 200）；超管也不可见")
  void mineSpaceIsIdempotentAndInvisibleToSuperAdmin() throws Exception {
    JsonNode first = createSpace(cookieA, spaceJson("我的空间-A", "\"type\":\"mine\""));
    assertEquals("mine", first.at("/type").asText(), first.toString());
    assertEquals("private", first.at("/acl").asText(), first.toString());
    long spaceId = first.at("/id").asLong();

    JsonNode again = createSpace(cookieA, spaceJson("另一个名字", "\"type\":\"mine\""));
    assertEquals(spaceId, again.at("/id").asLong(), "重复创建应返回既有库：" + again);
    assertEquals("我的空间-A", again.at("/name").asText(), again.toString());

    // 仅创建者可见：B 详情 40302、列表 0 条；超管同样不可见
    assertEquals(403, send("GET", "/api/v1/doc-spaces/" + spaceId, null, cookieB).statusCode());
    assertEquals(403, send("GET", "/api/v1/doc-spaces/" + spaceId, null, admin).statusCode());
    assertTrue(listHasSpace(spaces(cookieA), "我的空间-A"), "创建者应可见");
    assertFalse(listHasSpace(spaces(cookieB), "我的空间-A"), "他人不可见");
    assertFalse(listHasSpace(spaces(admin), "我的空间-A"), "超管也不可见");
  }

  @Test
  @DisplayName("库可见性矩阵：open 全员 / private 白名单 / default 继承产品可见性")
  void spaceVisibilityMatrix() throws Exception {
    JsonNode open = createSpace(cookieA, spaceJson("公开库", null));
    long openId = open.at("/id").asLong();
    assertEquals(200, send("GET", "/api/v1/doc-spaces/" + openId, null, cookieB).statusCode());

    JsonNode privateSpace = createSpace(cookieA,
        spaceJson("私有库", "\"acl\":\"private\",\"whitelist\":{\"accounts\":[\"" + B + "\"]}"));
    long privateId = privateSpace.at("/id").asLong();
    assertEquals(200, send("GET", "/api/v1/doc-spaces/" + privateId, null, cookieB).statusCode());
    assertEquals(200, send("GET", "/api/v1/doc-spaces/" + privateId, null, admin).statusCode(), "private 库超管可见");
    // 白名单外普通账号：详情 40302、列表不出现
    assertEquals(403, send("GET", "/api/v1/doc-spaces/" + privateId, null, cookieC).statusCode());
    assertFalse(listHasSpace(spaces(cookieC), "私有库"), "白名单外不可见");

    // default 库挂在 private 产品上：产品外人不可见
    long privateProduct = dataId(send("POST", "/api/v1/products",
        "{\"name\":\"库门禁私有产品\",\"acl\":\"private\",\"whitelist\":[\"" + A + "\"]}", admin));
    JsonNode defaultSpace = createSpace(cookieA, spaceJson("继承库",
        "\"type\":\"product\",\"productId\":" + privateProduct + ",\"acl\":\"default\""));
    long defaultId = defaultSpace.at("/id").asLong();
    assertEquals(200, send("GET", "/api/v1/doc-spaces/" + defaultId, null, cookieA).statusCode());
    assertEquals(403, send("GET", "/api/v1/doc-spaces/" + defaultId, null, cookieB).statusCode());
  }

  @Test
  @DisplayName("归属对象必填与取值：type=product 缺 productId → 42201；custom 无 default acl → 42201")
  void ownerRequiredAndAclInvariants() throws Exception {
    HttpResponse<String> missingOwner = send("POST", "/api/v1/doc-spaces",
        spaceJson("缺产品库", "\"type\":\"product\""), cookieA);
    assertEquals(422, missingOwner.statusCode(), missingOwner.body());
    assertEquals("required", json.readTree(missingOwner.body()).at("/error/fields/productId").asText());

    HttpResponse<String> customDefault = send("POST", "/api/v1/doc-spaces",
        spaceJson("错误默认库", "\"type\":\"custom\",\"acl\":\"default\""), cookieA);
    assertEquals(422, customDefault.statusCode(), customDefault.body());
  }

  @Test
  @DisplayName("isDefault 互斥：同一产品二次置 true 时旧主库自动落 false")
  void defaultFlagIsExclusive() throws Exception {
    long product = createProduct(admin, "主库产品");
    long first = createSpace(cookieA, spaceJson("主库一",
        "\"type\":\"product\",\"productId\":" + product + ",\"isDefault\":true")).at("/id").asLong();
    long second = createSpace(cookieA, spaceJson("主库二",
        "\"type\":\"product\",\"productId\":" + product + ",\"isDefault\":true")).at("/id").asLong();

    assertFalse(data(send("GET", "/api/v1/doc-spaces/" + first, null, cookieA)).at("/isDefault").asBoolean(),
        "旧主库应落 false");
    assertTrue(data(send("GET", "/api/v1/doc-spaces/" + second, null, cookieA)).at("/isDefault").asBoolean());
  }

  @Test
  @DisplayName("删除守卫与乐观锁：非空库 42203、空库删除后 40401、lockVersion 不符 40901")
  void deleteGuardAndOptimisticLock() throws Exception {
    long spaceId = createSpace(cookieA, spaceJson("守卫库", null)).at("/id").asLong();
    long docId = dataId(send("POST", "/api/v1/doc-spaces/" + spaceId + "/docs",
        "{\"title\":\"库内文档\",\"content\":\"正文\"}", cookieA));
    assertEquals(1, data(send("GET", "/api/v1/doc-spaces/" + spaceId, null, cookieA)).at("/docCount").asLong());

    HttpResponse<String> nonEmpty = send("DELETE", "/api/v1/doc-spaces/" + spaceId, null, cookieA);
    assertEquals(422, nonEmpty.statusCode(), nonEmpty.body());

    assertEquals(200, send("DELETE", "/api/v1/docs/" + docId, null, cookieA).statusCode());
    assertEquals(200, send("DELETE", "/api/v1/doc-spaces/" + spaceId, null, cookieA).statusCode());
    assertEquals(404, send("GET", "/api/v1/doc-spaces/" + spaceId, null, cookieA).statusCode());

    long locked = createSpace(cookieA, spaceJson("乐观锁库", null)).at("/id").asLong();
    HttpResponse<String> conflict = send("PATCH", "/api/v1/doc-spaces/" + locked,
        "{\"name\":\"改名\",\"lockVersion\":99}", cookieA);
    assertEquals(409, conflict.statusCode(), conflict.body());
    JsonNode updated = data(send("PATCH", "/api/v1/doc-spaces/" + locked,
        "{\"name\":\"改名\",\"lockVersion\":0}", cookieA));
    assertEquals("改名", updated.at("/name").asText(), updated.toString());
    assertEquals(1, updated.at("/lockVersion").asInt(), updated.toString());
  }

  @Test
  @DisplayName("库内文档列表缺省排序取库 docSort（id_asc / id_desc）")
  void spaceDocSortApplies() throws Exception {
    long ascSpace = createSpace(cookieA, spaceJson("排序库甲", "\"docSort\":\"id_asc\"")).at("/id").asLong();
    long ascFirst = dataId(send("POST", "/api/v1/doc-spaces/" + ascSpace + "/docs",
        "{\"title\":\"甲一\"}", cookieA));
    dataId(send("POST", "/api/v1/doc-spaces/" + ascSpace + "/docs", "{\"title\":\"甲二\"}", cookieA));
    JsonNode asc = data(send("GET", "/api/v1/doc-spaces/" + ascSpace + "/docs", null, cookieA));
    assertEquals(ascFirst, asc.at("/items/0/id").asLong(), asc.toString());

    long descSpace = createSpace(cookieA, spaceJson("排序库乙", "\"docSort\":\"id_desc\"")).at("/id").asLong();
    dataId(send("POST", "/api/v1/doc-spaces/" + descSpace + "/docs", "{\"title\":\"乙一\"}", cookieA));
    long descSecond = dataId(send("POST", "/api/v1/doc-spaces/" + descSpace + "/docs",
        "{\"title\":\"乙二\"}", cookieA));
    JsonNode desc = data(send("GET", "/api/v1/doc-spaces/" + descSpace + "/docs", null, cookieA));
    assertEquals(descSecond, desc.at("/items/0/id").asLong(), desc.toString());
  }

  @Test
  @DisplayName("列表 DSL：type 过滤、q 命中名称、未注册过滤字段 40001")
  void listFilters() throws Exception {
    createSpace(cookieA, spaceJson("过滤库甲", "\"type\":\"custom\""));
    long product = createProduct(admin, "过滤产品");
    createSpace(cookieA, spaceJson("过滤库乙", "\"type\":\"product\",\"productId\":" + product));

    JsonNode custom = data(send("GET", "/api/v1/doc-spaces?filters%5Btype%5D=custom&q=过滤库甲", null, cookieA));
    assertEquals(1, custom.at("/total").asLong(), custom.toString());
    assertEquals("过滤库甲", custom.at("/items/0/name").asText(), custom.toString());

    HttpResponse<String> bad = send("GET", "/api/v1/doc-spaces?filters%5Bunknown%5D=1", null, cookieA);
    assertEquals(400, bad.statusCode(), bad.body());
  }
}
