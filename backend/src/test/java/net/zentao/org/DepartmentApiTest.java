package net.zentao.org;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.util.List;
import net.zentao.ApiTestSupport;
import net.zentao.platform.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 部门 API（org 卡 §8）：建树/级联重算/环检测/双删除守卫/整树保存/平铺列表/权限码 40301，单流程自包含。 */
class DepartmentApiTest extends ApiTestSupport {

  @Autowired
  JdbcTemplate jdbcTemplate;

  private long createDepartment(String cookie, String name, Long parentId) throws Exception {
    return createDepartment(cookie, name, parentId, null);
  }

  private long createDepartment(String cookie, String name, Long parentId, Integer sort) throws Exception {
    String parentPart = parentId == null ? "" : ",\"parentId\":" + parentId;
    String sortPart = sort == null ? "" : ",\"sort\":" + sort;
    HttpResponse<String> response = send("POST", "/api/v1/departments",
        "{\"name\":\"" + name + "\"" + parentPart + sortPart + "}", cookie);
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data/id").asLong();
  }

  private JsonNode findNode(JsonNode items, long id) {
    for (JsonNode item : items) {
      if (item.get("id").asLong() == id) {
        return item;
      }
      JsonNode child = findNode(item.get("children"), id);
      if (child != null) {
        return child;
      }
    }
    return null;
  }

  @Test
  @DisplayName("建树→移动子树级联重算→环检测→双删除守卫→整树保存一致")
  void departmentLifecycle() throws Exception {
    String cookie = login("admin", "admin123");
    long rootA = createDepartment(cookie, "总部A", null);
    long childB = createDepartment(cookie, "研发B-" + rootA, rootA);
    long grandChildC = createDepartment(cookie, "测试C-" + childB, childB);
    long rootD = createDepartment(cookie, "分部D", null);
    assertTrue(rootA > 0 && childB > 0 && grandChildC > 0 && rootD > 0);

    HttpResponse<String> tree = send("GET", "/api/v1/departments/tree", null, cookie);
    JsonNode items0 = json.readTree(tree.body()).at("/data/items");
    JsonNode nodeA0 = findNode(items0, rootA);
    assertEquals(1, nodeA0.get("grade").asInt());
    assertEquals("," + rootA + ",", nodeA0.get("path").asText());
    JsonNode nodeC0 = findNode(items0, grandChildC);
    assertEquals(3, nodeC0.get("grade").asInt());

    HttpResponse<String> moved = send("PATCH", "/api/v1/departments/" + childB,
        "{\"parentId\":" + rootD + ",\"lockVersion\":0}", cookie);
    assertEquals(200, moved.statusCode(), moved.body());
    HttpResponse<String> treeAfter = send("GET", "/api/v1/departments/tree", null, cookie);
    JsonNode items = json.readTree(treeAfter.body()).at("/data/items");
    JsonNode nodeB = findNode(items, childB);
    assertEquals("," + rootD + "," + childB + ",", nodeB.get("path").asText());
    JsonNode nodeC2 = findNode(items, grandChildC);
    assertEquals("," + rootD + "," + childB + "," + grandChildC + ",", nodeC2.get("path").asText());
    assertEquals(3, nodeC2.get("grade").asInt());

    HttpResponse<String> cycle = send("PATCH", "/api/v1/departments/" + rootD,
        "{\"parentId\":" + grandChildC + ",\"lockVersion\":0}", cookie);
    assertEquals(422, cycle.statusCode(), cycle.body());
    assertTrue(cycle.body().contains("42203"), cycle.body());

    HttpResponse<String> withChildren = send("DELETE", "/api/v1/departments/" + childB, null, cookie);
    assertEquals(422, withChildren.statusCode(), withChildren.body());
    assertTrue(withChildren.body().contains("42203"), withChildren.body());

    jdbcTemplate.update("INSERT INTO account (account, password, real_name, department_id) VALUES (?, 'x', '成员', ?)",
        "department-member-" + System.nanoTime(), grandChildC);
    HttpResponse<String> withMembers = send("DELETE", "/api/v1/departments/" + grandChildC, null, cookie);
    assertEquals(422, withMembers.statusCode(), withMembers.body());
    assertTrue(withMembers.body().contains("42203"), withMembers.body());

    String body = "{\"nodes\":[{\"id\":" + rootA + ",\"name\":\"总部A改名\",\"sort\":5,\"parentId\":null},{\"name\":\"客户新部\",\"parentId\":"
        + rootA + ",\"sort\":9}]}";
    HttpResponse<String> saved = send("PUT", "/api/v1/departments/tree", body, cookie);
    assertEquals(200, saved.statusCode(), saved.body());
    HttpResponse<String> after = send("GET", "/api/v1/departments/tree", null, cookie);
    JsonNode afterItems = json.readTree(after.body()).at("/data/items");
    JsonNode renamed = findNode(afterItems, rootA);
    assertEquals("总部A改名", renamed.get("name").asText());
    assertEquals(5, renamed.get("sort").asInt());
    boolean hasNewChild = false;
    for (JsonNode child : renamed.get("children")) {
      if ("客户新部".equals(child.get("name").asText())) {
        hasNewChild = true;
      }
    }
    assertTrue(hasNewChild, "新增节点应挂到 rootA: " + afterItems);
  }

  @Test
  @DisplayName("平铺列表：分页/名称搜索/上级与层级筛选/排序/parentName/未注册字段 40001")
  void departmentListPagingFiltersAndSort() throws Exception {
    String cookie = login("admin", "admin123");
    String tag = String.valueOf(System.nanoTime());
    long rootA = createDepartment(cookie, "ListRoot-" + tag, null);
    long childSlow = createDepartment(cookie, "ListDev-" + tag, rootA, 5);
    long childFast = createDepartment(cookie, "ListQa-" + tag, rootA, 2);
    long grandChild = createDepartment(cookie, "ListSub-" + tag, childSlow, 1);

    // 上级筛选：恰好两条子部门；缺省排序 = sort 升序（childFast=2 在 childSlow=5 前）；parentName 为上级名
    JsonNode byParent = data(send("GET", "/api/v1/departments?filters%5BparentId%5D=" + rootA, null, cookie));
    assertEquals(2, byParent.get("total").asLong(), byParent.toString());
    assertEquals(List.of(childFast, childSlow),
        List.of(byParent.at("/items/0/id").asLong(), byParent.at("/items/1/id").asLong()));
    assertEquals("ListRoot-" + tag, byParent.at("/items/0/parentName").asText());
    assertTrue(byParent.at("/items/0/children").isEmpty(), byParent.at("/items/0").toString());

    // 分页：limit=1 的第二页 = 缺省排序的第二条；total 仍是过滤后的总数
    JsonNode page2 = data(send("GET", "/api/v1/departments?filters%5BparentId%5D=" + rootA + "&limit=1&page=2", null, cookie));
    assertEquals(2, page2.get("total").asLong(), page2.toString());
    assertEquals(1, page2.get("items").size());
    assertEquals(childSlow, page2.at("/items/0/id").asLong());

    // 层级筛选（与上级筛选叠加）：子部门 grade=2；孙部门 grade=3 且上级名为子部门名
    JsonNode byGrade = data(send("GET", "/api/v1/departments?filters%5BparentId%5D=" + rootA + "&filters%5Bgrade%5D=2", null, cookie));
    assertEquals(2, byGrade.get("total").asLong(), byGrade.toString());
    JsonNode grand = data(send("GET", "/api/v1/departments?filters%5BparentId%5D=" + childSlow, null, cookie)).at("/items/0");
    assertEquals(3, grand.get("grade").asInt());
    assertEquals("ListDev-" + tag, grand.get("parentName").asText());

    // 名称搜索：q 只命中本用例前缀
    JsonNode searched = data(send("GET", "/api/v1/departments?q=ListSub-" + tag, null, cookie));
    assertEquals(1, searched.get("total").asLong(), searched.toString());
    assertEquals(grandChild, searched.at("/items/0/id").asLong());

    // 排序：-id 降序（页内单调下降）
    JsonNode byId = data(send("GET", "/api/v1/departments?sort=-id&limit=200", null, cookie));
    long previous = Long.MAX_VALUE;
    for (JsonNode item : byId.get("items")) {
      assertTrue(item.get("id").asLong() < previous, byId.get("items").toString());
      previous = item.get("id").asLong();
    }

    // 未注册过滤/排序字段 → 40001
    HttpResponse<String> unknownFilter = send("GET", "/api/v1/departments?filters%5Bbogus%5D=1", null, cookie);
    assertEquals(400, unknownFilter.statusCode(), unknownFilter.body());
    HttpResponse<String> unknownSort = send("GET", "/api/v1/departments?sort=-bogus", null, cookie);
    assertEquals(400, unknownSort.statusCode(), unknownSort.body());
  }

  @Test
  @DisplayName("无 department-create 码的账号 → 40301")
  void forbiddenWithoutPrivilege() throws Exception {
    String account = "department-guest-" + System.nanoTime();
    String hash = new BCryptPasswordEncoder().encode("admin123");
    jdbcTemplate.update("INSERT INTO account (account, password, real_name) VALUES (?, ?, '访客')", account, hash);

    HttpResponse<String> login = send("POST", "/api/v1/session",
        "{\"account\":\"" + account + "\",\"password\":\"admin123\"}", null);
    assertEquals(200, login.statusCode(), login.body());
    String guestCookie = login.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];

    HttpResponse<String> response = send("POST", "/api/v1/departments", "{\"name\":\"非法部门\"}", guestCookie);
    assertEquals(403, response.statusCode(), response.body());
    assertTrue(response.body().contains(String.valueOf(ErrorCode.FORBIDDEN.code())), response.body());

    // 列表端点同码：无 department-view → 40301
    HttpResponse<String> list = send("GET", "/api/v1/departments", null, guestCookie);
    assertEquals(403, list.statusCode(), list.body());
    assertTrue(list.body().contains(String.valueOf(ErrorCode.FORBIDDEN.code())), list.body());
  }
}
