package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.platform.menu.MenuPageRegistry;
import net.zentao.platform.menu.MenuQueryService;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * T03 菜单整树播种（ADR-001 菜单纯 DB 化）：把页面注册表（`menu/navigation.json`）里的**容器与页面**
 * （含 @hide 的隐藏页）写进 `menu` 表，此后菜单的唯一数据源是 DB——管理员可以真正地改名/换父/排序/删除。
 *
 * <p>为什么是 Java 迁移而不是 SQL：整树是 100+ 行有父子关系的结构，手抄成 SQL 必然与注册表漂移；
 * 注册表本身是 codegen 产物（与前端 routes.tsx 同源），这里读它的 classpath 副本。旧库导入 CLI
 * 只跑 SQL 迁移（Java 迁移在那边解析不到、被标记为已应用），但 `menu` 表没有旧库来源（`zt_module`
 * 迁的是 category），而导入后的首次应用启动会执行本迁移，故导入路径同样能拿到整树。
 *
 * <p>**按钮不播种**：按钮的存在性是代码派生的（页面文件 HasPerm 扫描 + 权限编目按域归属），
 * 随代码变化；它由 {@link MenuQueryService} 在装配树时挂上，DB 里同 key 的行可覆盖其标题/排序/状态。
 *
 * <p>幂等：已存在的行（管理员编辑过的内置项）**只补结构**——node_type 与空着的 parent_key/component
 * 按注册表补齐，title/path/icon/order_no/perm/status 一律不动，免得升级时把管理员的改动冲掉。
 */
public class V42__menu_seed extends BaseJavaMigration {

  /** 一行种子。 */
  record SeedRow(String nodeKey, String parentKey, String nodeType, String title, String path, String component,
      String icon, int orderNo, String perm) {}

  @Override
  public void migrate(Context context) throws Exception {
    seed(context.getConnection(), new MenuPageRegistry());
  }

  /** 播种（可重复执行）：返回处理过的行数。测试直接调它验幂等。 */
  public static int seed(Connection connection, MenuPageRegistry registry) throws SQLException {
    int touched = 0;
    for (SeedRow row : rows(registry)) {
      Long id = findId(connection, row.nodeKey());
      if (id == null) {
        insert(connection, row);
      } else {
        patch(connection, id, row);
      }
      touched += 1;
    }
    return touched;
  }

  /** 注册表 → 种子行：组/分区是容器（dir），页面是菜单项（menu），隐藏页按 activeMenu 挂到宿主页下。 */
  static List<SeedRow> rows(MenuPageRegistry registry) {
    Map<String, SeedRow> byKey = new LinkedHashMap<>();
    for (MenuPageRegistry.Node group : registry.groups()) {
      byKey.put(group.key(), container(group, null, MenuQueryService.GROUP));
      for (MenuPageRegistry.Node child : group.children()) {
        if (MenuQueryService.ITEM.equals(child.kind())) {
          byKey.putIfAbsent(child.key(), page(child.key(), child.parentKey(), registry));
          continue;
        }
        byKey.put(child.key(), container(child, group.key(), MenuQueryService.SECTION));
        for (MenuPageRegistry.Node item : child.children()) {
          byKey.putIfAbsent(item.key(), page(item.key(), child.key(), registry));
        }
      }
    }
    for (MenuPageRegistry.Page hidden : registry.hiddenPages()) {
      // 无宿主（activeMenu）的隐藏页**不进树**：T03 之前的树只挂 activeMenu 命中的那批（如 /login 就不在树里），
      // 保持整树与迁移前逐节点一致；它仍会经注册表兜底进 /menus/routes，页面照常可打开。
      String host = hidden.activeMenu();
      if (host == null || registry.page(host) == null) {
        continue;
      }
      byKey.putIfAbsent(hidden.path(), page(hidden.path(), host, registry));
    }
    return List.copyOf(byKey.values());
  }

  private static SeedRow container(MenuPageRegistry.Node node, String parentKey, String kind) {
    String type = MenuQueryService.GROUP.equals(kind) || MenuQueryService.SECTION.equals(kind) ? "dir" : "menu";
    return new SeedRow(node.key(), parentKey, type, node.title(), null, null, node.icon(), node.order(), null);
  }

  /** 页面行：key = path；component 来自注册表（页面实现是代码，管理端不可造）。 */
  private static SeedRow page(String path, String parentKey, MenuPageRegistry registry) {
    MenuPageRegistry.Page page = registry.page(path).orElse(null);
    if (page == null) {
      return new SeedRow(path, parentKey, "menu", path, path, null, null, 999, null);
    }
    return new SeedRow(path, parentKey, "menu", page.title(), page.path(), page.component(), page.icon(),
        page.order(), page.perm());
  }

  private static Long findId(Connection connection, String nodeKey) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM menu WHERE node_key = ?")) {
      ps.setString(1, nodeKey);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getLong(1) : null;
      }
    }
  }

  private static void insert(Connection connection, SeedRow row) throws SQLException {
    String sql = "INSERT INTO menu (node_key, parent_key, node_type, title, path, component, icon, order_no, perm, status)"
        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'active')";
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, row.nodeKey());
      ps.setString(2, row.parentKey());
      ps.setString(3, row.nodeType());
      ps.setString(4, row.title());
      ps.setString(5, row.path());
      ps.setString(6, row.component());
      ps.setString(7, row.icon());
      ps.setInt(8, row.orderNo());
      ps.setString(9, row.perm());
      ps.executeUpdate();
    }
  }

  /** 已有行只补结构：node_type 归位、parent_key/component 空着才填，其余字段是管理员的，不碰。 */
  private static void patch(Connection connection, long id, SeedRow row) throws SQLException {
    String sql = "UPDATE menu SET node_type = ?," + " parent_key = CASE WHEN parent_key IS NULL THEN ? ELSE parent_key END,"
        + " component = CASE WHEN component IS NULL THEN ? ELSE component END WHERE id = ?";
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, row.nodeType());
      ps.setString(2, row.parentKey());
      ps.setString(3, row.component());
      ps.setLong(4, id);
      ps.executeUpdate();
    }
  }
}
