package net.zentao.platform.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import db.migration.V42__menu_seed;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import net.zentao.H2TestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T03 整树播种（{@link V42__menu_seed}）的看护：注册表能算出整树、重复执行幂等、父子关系与「按钮不播种」的口径不漂。
 *
 * <p>H2 与其它用例共享同一个库，故这里只断言**与播种相关**的事实（key 存在性、重复播种零新增、无重复 key），
 * 不数全表行数。
 */
class MenuSeedTest extends H2TestSupport {

  @Autowired
  private DataSource dataSource;

  @Test
  @DisplayName("播种幂等 + 覆盖容器/页面/隐藏页，且不播种按钮（按钮是代码派生的）")
  void seedIsIdempotent() throws Exception {
    MenuPageRegistry registry = new MenuPageRegistry();
    assertTrue(registry.pages().size() >= 100, "注册表要含全部页面：" + registry.pages().size());
    try (Connection connection = dataSource.getConnection()) {
      int before = countRows(connection);
      int first = V42__menu_seed.seed(connection, registry);
      int afterFirst = countRows(connection);
      int second = V42__menu_seed.seed(connection, registry);
      int afterSecond = countRows(connection);

      assertEquals(first, second, "两次播种处理的行数应一致");
      assertEquals(afterFirst, afterSecond, "重复播种不该新增行");
      assertEquals(0, duplicateKeys(connection), "node_key 唯一约束之外不该出现同 key 两行");
      assertTrue(first >= 100, "整树至少 100 个节点（容器+页面+隐藏页）：" + first);

      // 容器（组/分区）与页面都在
      assertEquals("dir", nodeType(connection, "admin"));
      assertEquals("dir", nodeType(connection, "admin/system"));
      assertEquals("menu", nodeType(connection, "/admin/dicts"));
      assertEquals("SettingPage", componentOf(connection, "/admin/settings"));
      assertEquals("nav.group.admin", titleOf(connection, "admin"), "播种保留注册表里的 i18n 键");

      // 隐藏页也播种：parent_key = 高亮目标（宿主页面），否则管理树会丢详情页
      assertEquals("menu", nodeType(connection, "/admin/roles/:roleId/members"));
      assertEquals("/admin/roles", parentOf(connection, "/admin/roles/:roleId/members"));
      assertEquals("RoleMembersPage", componentOf(connection, "/admin/roles/:roleId/members"));

      // 按钮不播种：存在性来自代码（页面 HasPerm + 编目按域归属），由 MenuQueryService 挂上
      assertFalse(hasKey(connection, "/admin/roles#role-create"), "按钮不该进 DB 种子");
      assertTrue(hasKey(connection, "/admin/roles"), "按钮的宿主页面在");
    }
  }

  @Test
  @DisplayName("播种只补结构：管理员改过的标题/图标/排序不被覆盖")
  void seedPreservesAdminEdits() throws Exception {
    MenuPageRegistry registry = new MenuPageRegistry();
    try (Connection connection = dataSource.getConnection()) {
      V42__menu_seed.seed(connection, registry);
      try (PreparedStatement ps = connection.prepareStatement(
          "UPDATE menu SET title = ?, icon = ?, order_no = 42 WHERE node_key = ?")) {
        ps.setString(1, "管理员改的标题");
        ps.setString(2, "BulbOutlined");
        ps.setString(3, "/admin/settings");
        ps.executeUpdate();
      }
      V42__menu_seed.seed(connection, registry);
      assertEquals("管理员改的标题", titleOf(connection, "/admin/settings"), "重复播种不许冲掉管理员的标题");
      assertEquals("BulbOutlined", iconOf(connection, "/admin/settings"), "图标同理");
      assertEquals(42, orderOf(connection, "/admin/settings"));
    }
  }

  private static int countRows(Connection connection) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM menu");
        ResultSet rs = ps.executeQuery()) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private static int duplicateKeys(Connection connection) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT COUNT(*) FROM (SELECT node_key FROM menu GROUP BY node_key HAVING COUNT(*) > 1) dup");
        ResultSet rs = ps.executeQuery()) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private static boolean hasKey(Connection connection, String nodeKey) throws SQLException {
    return valueOf(connection, "node_key", nodeKey) != null;
  }

  private static String nodeType(Connection connection, String nodeKey) throws SQLException {
    return valueOf(connection, "node_type", nodeKey);
  }

  private static String titleOf(Connection connection, String nodeKey) throws SQLException {
    return valueOf(connection, "title", nodeKey);
  }

  private static String componentOf(Connection connection, String nodeKey) throws SQLException {
    return valueOf(connection, "component", nodeKey);
  }

  private static String parentOf(Connection connection, String nodeKey) throws SQLException {
    return valueOf(connection, "parent_key", nodeKey);
  }

  private static String iconOf(Connection connection, String nodeKey) throws SQLException {
    return valueOf(connection, "icon", nodeKey);
  }

  private static Integer orderOf(Connection connection, String nodeKey) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("SELECT order_no FROM menu WHERE node_key = ?")) {
      ps.setString(1, nodeKey);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : null;
      }
    }
  }

  private static String valueOf(Connection connection, String column, String nodeKey) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("SELECT " + column + " FROM menu WHERE node_key = ?")) {
      ps.setString(1, nodeKey);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getString(1) : null;
      }
    }
  }
}
