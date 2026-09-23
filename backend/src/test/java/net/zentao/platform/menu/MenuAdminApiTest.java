package net.zentao.platform.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 菜单管理（T19 P2-1 / T21 / T03 纯 DB 化）端到端：DB 整树（迁移播种）→ 增删改 → 侧栏视图（/menus/my）即时生效。
 *
 * <p>本卡的真验收是「改完菜单侧栏立刻变样」：服务端用 /menus/my（不是管理页接口）复核，与浏览器侧栏同源。
 * 同 JVM 共享 H2，故每个用例自建唯一 key/path；**不动播种行**（删了就影响后续用例——删除能力用自建行验）。
 */
class MenuAdminApiTest extends ApiTestSupport {

  private static final String UNIQUE = Long.toString(System.nanoTime() % 100000000);

  @Test
  @DisplayName("整树来自 DB（迁移播种）：分区/页面/权限码齐备，且不再有 source/menuId 之类内置标记")
  void treeIsServedFromDb() throws Exception {
    String cookie = login("admin", "admin123");

    JsonNode tree = data(send("GET", "/api/v1/menus/tree", null, cookie));
    JsonNode system = find(tree, "admin/system");
    assertEquals("section", system.get("kind").asText());
    assertEquals("admin", system.get("parentKey").asText());
    assertTrue(system.get("children").size() >= 3, "「系统管理」分区至少 3 项：" + system);
    JsonNode dicts = childByPath(system, "/admin/dicts");
    assertEquals("item", dicts.get("kind").asText());
    assertEquals("platform.dict.title", dicts.get("title").asText(), "播种保留注册表里的 i18n 键：" + dicts);
    assertEquals("setting-manage", dicts.get("perm").asText());
    assertEquals("DictTypeListPage", dicts.get("component").asText(), "组件由 path 从注册表推导：" + dicts);
    assertFalse(dicts.has("source"), "纯 DB 后没有 source 标记：" + dicts);
    assertFalse(dicts.has("menuId"), "寻址按 key，没有 menuId：" + dicts);

    // 一级目录带图标（侧栏左栏的组图标来自这里）
    assertEquals("SettingOutlined", find(tree, "admin").get("icon").asText());

    assertEquals(200, send("GET", "/api/v1/menus/my", null, cookie).statusCode());
    JsonNode mine = data(send("GET", "/api/v1/menus/my", null, cookie));
    assertEquals("item", childByPath(find(mine, "admin/system"), "/admin/dicts").get("kind").asText());

    // 页面注册表端点（T03）：path → component 的代码侧对应关系，菜单表单按它匹配组件
    JsonNode registry = data(send("GET", "/api/v1/menus/page-registry", null, cookie)).get("items");
    assertTrue(registry.size() >= 100, "注册表要含全部页面（含隐藏页）：" + registry.size());
    boolean sawHidden = false;
    for (JsonNode entry : registry) {
      if ("/admin/roles/:roleId/members".equals(entry.get("path").asText())) {
        sawHidden = entry.get("hide").asBoolean();
        assertEquals("RoleMembersPage", entry.get("component").asText());
      }
    }
    assertTrue(sawHidden, "隐藏页也要在注册表里（hide=true）：" + registry);
  }

  @Test
  @DisplayName("新建一级模块（目录）+ 菜单项 + 按钮：目录与菜单进侧栏，按钮只进管理面；删目录级联删子孙")
  void createModuleMenuAndButton() throws Exception {
    String cookie = login("admin", "admin123");

    JsonNode dir = data(send("POST", "/api/v1/menus",
        "{\"type\":\"dir\",\"title\":\"自建模块\",\"icon\":\"BulbOutlined\",\"orderNo\":90}", cookie));
    String dirKey = dir.get("key").asText();
    assertEquals("group", dir.get("kind").asText(), "无上级的目录 = 一级模块：" + dir);
    assertTrue(dir.get("path").isNull(), "目录不是页面入口，没有路径：" + dir);

    // 菜单项：path 必须命中页面注册表（组件是代码），同一个页面再挂一个入口时 key 退化为 db-<id>
    JsonNode item = data(send("POST", "/api/v1/menus",
        "{\"type\":\"menu\",\"parentKey\":\"" + dirKey + "\",\"title\":\"自建页面\",\"path\":\"/admin/dicts\","
            + "\"perm\":\"setting-manage\"}",
        cookie));
    String itemKey = item.get("key").asText();
    assertEquals("item", item.get("kind").asText());
    assertEquals(dirKey, item.get("parentKey").asText());
    assertTrue(itemKey.startsWith("db-"), "同一页面已挂过入口 → key 用 db-<id>：" + item);
    assertEquals("DictTypeListPage", item.get("component").asText(), "组件仍由 path 推导：" + item);

    JsonNode button = data(send("POST", "/api/v1/menus",
        "{\"type\":\"button\",\"parentKey\":\"" + itemKey + "\",\"title\":\"导出\",\"perm\":\"setting-manage\"}",
        cookie));
    String buttonKey = button.get("key").asText();
    assertEquals("button", button.get("kind").asText(), "按钮挂菜单下：" + button);
    assertTrue(button.get("path").isNull(), "按钮没有路径：" + button);

    // 管理面：三层都在，按钮挂在菜单项下
    JsonNode admin = data(send("GET", "/api/v1/menus/tree", null, cookie));
    assertEquals("自建模块", find(admin, dirKey).get("title").asText());
    JsonNode adminItem = find(admin, itemKey);
    assertEquals("导出", firstChild(adminItem).get("title").asText());
    assertEquals("button", firstChild(adminItem).get("kind").asText());
    // 侧栏：目录与菜单在，按钮不下发（它不是导航目标）
    JsonNode mine = data(send("GET", "/api/v1/menus/my", null, cookie));
    assertEquals("自建模块", find(mine, dirKey).get("title").asText(), "新建一级模块要立刻进侧栏：" + mine);
    assertEquals(0, find(mine, itemKey).get("children").size(), "按钮不进侧栏：" + find(mine, itemKey));

    // 停用一级模块 → 整棵子树从侧栏消失
    assertEquals(200, patch(cookie, dirKey, "{\"status\":\"disabled\"}").statusCode());
    JsonNode afterDisable = data(send("GET", "/api/v1/menus/my", null, cookie));
    assertNull(find(afterDisable, dirKey), "停用后侧栏不该有它：" + afterDisable);
    assertNotNull(find(data(send("GET", "/api/v1/menus/tree", null, cookie)), dirKey), "管理面仍看得到停用项");

    // 删一级模块 → 子孙一起删（否则留下悬空的上级）
    assertEquals(200, delete(cookie, dirKey).statusCode());
    JsonNode after = data(send("GET", "/api/v1/menus/tree", null, cookie));
    assertNull(find(after, dirKey), "删除后管理视图不该有一级模块：" + after);
    assertNull(find(after, itemKey), "子菜单要跟着删");
    assertNull(find(after, buttonKey), "按钮也要跟着删");
    assertEquals(404, delete(cookie, buttonKey).statusCode(), "按钮已随父删除");
    assertEquals(404, delete(cookie, itemKey).statusCode());
  }

  @Test
  @DisplayName("改播种行：PATCH 按 nodeKey 改标题/图标/路径（路径改了组件跟着重匹配），改回原样")
  void editSeededRow() throws Exception {
    String cookie = login("admin", "admin123");

    assertEquals(200, patch(cookie, "/admin/params",
        "{\"title\":\"参数（改）\",\"icon\":\"ExperimentOutlined\",\"orderNo\":1}").statusCode());
    JsonNode changed = childByPath(find(data(send("GET", "/api/v1/menus/tree", null, cookie)), "admin/system"),
        "/admin/params");
    assertEquals("参数（改）", changed.get("title").asText(), "管理员文案盖过 i18n 键：" + changed);
    assertEquals("ExperimentOutlined", changed.get("icon").asText());
    assertEquals(1, changed.get("orderNo").asInt());

    // 路径可改（T26/T68 的能力保留）：改成另一个页面，侧栏数据源跟着走、组件按新 path 重匹配
    assertEquals(200, patch(cookie, "/admin/params", "{\"path\":\"/admin/settings\"}").statusCode());
    JsonNode moved = childByPath(find(data(send("GET", "/api/v1/menus/my", null, cookie)), "admin/system"),
        "/admin/settings");
    assertEquals("SettingPage", moved.get("component").asText(), "组件按新 path 从注册表重匹配：" + moved);

    // 改回来（后续用例还要用这个入口）
    assertEquals(200, patch(cookie, "/admin/params", "{\"path\":\"/admin/params\"}").statusCode());
    JsonNode restored = childByPath(find(data(send("GET", "/api/v1/menus/tree", null, cookie)), "admin/system"),
        "/admin/params");
    assertEquals("ParamListPage", restored.get("component").asText());
    assertEquals(200, patch(cookie, "/admin/params",
        "{\"title\":\"platform.param.title\",\"icon\":\"\",\"orderNo\":999}").statusCode(), "标题/图标还原（i18n 键）");
  }

  @Test
  @DisplayName("写入约束：类型/层级/权限码/path 四条校验 → 42201；改删不存在的行 → 40401")
  void writeGuards() throws Exception {
    String cookie = login("admin", "admin123");

    // perm 不在权限编目内：写进去也没人能拿到该码，菜单会永远是空的 → 建的时候就挡
    assertEquals(422, send("POST", "/api/v1/menus",
        "{\"type\":\"menu\",\"parentKey\":\"admin/system\",\"title\":\"x\",\"path\":\"/admin/dicts\","
            + "\"perm\":\"no-such-perm\"}",
        cookie).statusCode());
    // 菜单项必须给 path
    assertEquals(422, send("POST", "/api/v1/menus",
        "{\"type\":\"menu\",\"parentKey\":\"admin/system\",\"title\":\"x\"}", cookie).statusCode());
    // path 不在页面注册表里（组件是代码，造不出来）
    var unknownPath = send("POST", "/api/v1/menus",
        "{\"type\":\"menu\",\"parentKey\":\"admin/system\",\"title\":\"x\",\"path\":\"/admin/nope\"}", cookie);
    assertEquals(422, unknownPath.statusCode());
    assertEquals("unknown", json.readTree(unknownPath.body()).at("/error/fields/path").asText(), unknownPath.body());
    // path 不以 / 开头
    assertEquals(422, send("POST", "/api/v1/menus",
        "{\"type\":\"menu\",\"parentKey\":\"admin/system\",\"title\":\"x\",\"path\":\"admin/nope\"}", cookie).statusCode());
    // 菜单不能挂到菜单下（层级最多三级）
    assertEquals(422, send("POST", "/api/v1/menus",
        "{\"type\":\"menu\",\"parentKey\":\"/admin/dicts\",\"title\":\"x\",\"path\":\"/admin/settings\"}", cookie)
        .statusCode());
    // 按钮必须挂菜单下，且必须有权限码
    assertEquals(422, send("POST", "/api/v1/menus",
        "{\"type\":\"button\",\"parentKey\":\"admin/system\",\"title\":\"x\",\"perm\":\"setting-manage\"}", cookie)
        .statusCode());
    assertEquals(422, send("POST", "/api/v1/menus",
        "{\"type\":\"button\",\"parentKey\":\"/admin/dicts\",\"title\":\"x\"}", cookie).statusCode());
    // 目录不能挂到分区下（侧栏渲染不出更深一层）
    assertEquals(422, send("POST", "/api/v1/menus",
        "{\"type\":\"dir\",\"parentKey\":\"admin/system\",\"title\":\"x\"}", cookie).statusCode());
    // type 不是三类之一
    assertEquals(422, send("POST", "/api/v1/menus", "{\"type\":\"page\",\"title\":\"x\"}", cookie).statusCode());

    assertEquals(404, patch(cookie, "no-such-key", "{\"title\":\"z\"}").statusCode());
    assertEquals(404, delete(cookie, "no-such-key").statusCode());
  }

  @Test
  @DisplayName("侧栏过滤：无权限码的账号看不到该项，管理端点在无 menu-manage 时 403")
  void myTreeFiltersByPrivilege() throws Exception {
    String admin = login("admin", "admin123");
    String key = data(send("POST", "/api/v1/menus",
        "{\"type\":\"menu\",\"parentKey\":\"admin/system\",\"title\":\"仅审计可见\",\"path\":\"/admin/dicts\","
            + "\"perm\":\"audit-log-view\"}",
        admin)).get("key").asText();
    try {
      // 只有 file-upload 的账号：菜单管理面进不去，侧栏视图里也不该出现要 audit-log-view 的项
      String weak = accountWithPrivileges(admin, "t21user" + UNIQUE, "\"file-upload\"");
      assertEquals(403, send("GET", "/api/v1/menus/tree", null, weak).statusCode(), "无 menu-manage 的管理视图应 403");
      assertNull(find(data(send("GET", "/api/v1/menus/my", null, weak)), key), "无权限码的项不该下发：" + key);
      assertNotNull(find(data(send("GET", "/api/v1/menus/my", null, admin)), key));
      assertEquals(403, send("GET", "/api/v1/menus/page-registry", null, weak).statusCode(), "注册表也要 menu-manage");
    } finally {
      delete(admin, key);
    }
  }

  @Test
  @DisplayName("按钮按页面归位：页面声明的按钮 + 编目按域归属的动作码都挂在页面节点下；路由表覆盖全部页面")
  void buttonsUnderPagesAndRouteTable() throws Exception {
    String cookie = login("admin", "admin123");
    JsonNode tree = data(send("GET", "/api/v1/menus/tree", null, cookie));

    // ① 页面文件里声明的按钮（HasPerm 扫进注册表）：角色页的五个动作码都在它自己下面
    JsonNode roles = find(tree, "/admin/roles");
    assertTrue(buttonPerms(roles).containsAll(
        List.of("role-create", "role-edit", "role-delete", "role-copy", "role-priv-edit")),
        "页面声明的按钮要挂在页面下：" + roles);

    // ② 编目里没有页面字面量的动作码（工作流）按域归属到该域首页：task-finish → 任务列表页
    JsonNode tasks = find(tree, "/executions/:executionId/tasks");
    assertTrue(buttonPerms(tasks).contains("task-finish"), "task 域的动作码要归到任务页下：" + buttonPerms(tasks));

    // ③ 隐藏页（@hide 的详情/编辑页）挂在它的宿主菜单页下，不进侧栏但进管理树与路由表
    JsonNode roleMembers = find(tree, "/admin/roles/:roleId/members");
    assertTrue(roleMembers.get("hidden").asBoolean(), "详情页是隐藏页：" + roleMembers);
    JsonNode mine = data(send("GET", "/api/v1/menus/my", null, cookie));
    assertNull(find(mine, "/admin/roles/:roleId/members"), "隐藏页不进侧栏");

    // ④ 路由表：全部页面（含隐藏页），path 与 component 成对给出（前端按 component 取组件）
    JsonNode routes = data(send("GET", "/api/v1/menus/routes", null, cookie)).get("items");
    assertTrue(routes.size() >= 100, "路由表要含全部页面（含隐藏页）：" + routes.size());
    JsonNode roleRoute = null;
    JsonNode hiddenRoute = null;
    for (JsonNode route : routes) {
      if ("/admin/roles".equals(route.get("path").asText())) {
        roleRoute = route;
      }
      if ("/admin/roles/:roleId/members".equals(route.get("path").asText())) {
        hiddenRoute = route;
      }
    }
    assertNotNull(roleRoute, "播种页面要在路由表里");
    assertEquals("RoleListPage", roleRoute.get("component").asText());
    assertFalse(roleRoute.get("hidden").asBoolean());
    assertNotNull(hiddenRoute, "隐藏页也要在路由表里（否则详情页打不开）");
    assertTrue(hiddenRoute.get("hidden").asBoolean());
    assertEquals("RoleMembersPage", hiddenRoute.get("component").asText());
    assertEquals("/admin/roles", hiddenRoute.get("activeMenu").asText());
  }

  // ── 写端点（T03 起按 nodeKey 走查询参数）──

  private HttpResponse<String> patch(String cookie, String nodeKey, String body) throws Exception {
    return send("PATCH", "/api/v1/menus?nodeKey=" + encode(nodeKey), body, cookie);
  }

  private HttpResponse<String> delete(String cookie, String nodeKey) throws Exception {
    return send("DELETE", "/api/v1/menus?nodeKey=" + encode(nodeKey), null, cookie);
  }

  private static String encode(String nodeKey) {
    return URLEncoder.encode(nodeKey, StandardCharsets.UTF_8);
  }

  /** 页面节点的按钮权限码（kind=button 的子级）。 */
  private static List<String> buttonPerms(JsonNode page) {
    List<String> codes = new ArrayList<>();
    for (JsonNode child : page.path("children")) {
      if ("button".equals(child.path("kind").asText())) {
        codes.add(child.path("perm").asText());
      }
    }
    return codes;
  }

  // ── 树遍历小工具（断言用）──

  /** 全树按 key 或 path 找节点（找不到返回 null）。 */
  private static JsonNode find(JsonNode tree, String keyOrPath) {
    for (JsonNode group : tree.get("items")) {
      JsonNode hit = findDeep(group, keyOrPath);
      if (hit != null) {
        return hit;
      }
    }
    return null;
  }

  private static JsonNode findDeep(JsonNode node, String keyOrPath) {
    if (keyOrPath.equals(node.path("key").asText()) || keyOrPath.equals(node.path("path").asText())) {
      return node;
    }
    for (JsonNode child : node.path("children")) {
      JsonNode hit = findDeep(child, keyOrPath);
      if (hit != null) {
        return hit;
      }
    }
    return null;
  }

  /** 容器下按 path 找子节点（找不到直接抛，供断言用）。 */
  private static JsonNode childByPath(JsonNode container, String path) {
    for (JsonNode child : container.get("children")) {
      if (path.equals(child.get("path").asText())) {
        return child;
      }
    }
    throw new AssertionError("容器 " + container.path("key").asText() + " 里没有 " + path + "：" + container);
  }

  private static JsonNode firstChild(JsonNode node) {
    assertTrue(node.get("children").size() > 0, "节点没有子节点：" + node);
    return node.get("children").get(0);
  }
}
