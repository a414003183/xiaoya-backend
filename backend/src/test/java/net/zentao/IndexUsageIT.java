package net.zentao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T53：热查询索引在**真 MySQL** 上被优化器选中（Testcontainers MySQL 8.4，Flyway 迁移已建索引）。
 *
 * <p>为什么需要这条 IT：H2 的 schema 断言（{@code SchemaIndexTest}）只证明索引"存在"，不证明"用得上"——
 * 列序写歪、或优化器改选全表扫（`type=ALL`），单测全绿也看不出来。这里读 `EXPLAIN` 的 `key` 直接对照。
 *
 * <p>三条纪律（都是被上一次失败教训出来的）：
 * <ol>
 *   <li><b>数据分布要像生产</b>：附件是"很多对象、每个对象几个附件"，不是"少数对象、附件密布全表"——
 *       后者下"倒序扫主键直到凑够 LIMIT"是真的更便宜，优化器选主键扫是**对**的，逼它选索引就是测一个假象。
 *   <li><b>灌完数要 `ANALYZE TABLE`</b>：InnoDB 的统计是采样的，批量灌完不刷新统计，优化器拿的是旧基数。
 *   <li><b>一个事务里灌</b>：autocommit + 上万条单行提交在容器里要几分钟（fsync 每次都真落盘）。
 * </ol>
 *
 * <p>这条用例钉住的是 mysql:8.4 上的优化器选择；升级 MySQL 若计划变了会红——那是要重新审视的**信号**，
 * 不是把断言放宽就完事。
 */
class IndexUsageIT extends MySqlContainerSupport {

  private static final int OBJECTS = 2_000;
  private static final int PER_OBJECT = 10;
  private static final int ACTIVITY_ROWS = 20_000;
  private static final int AUDIT_ROWS = 20_000;
  private static final int DOC_CHAPTERS = 2_000;
  private static final int DOCS_PER_CHAPTER = 10;
  private static final long HOT_OBJECT_ID = 12_345L;

  private static boolean seeded;

  @Autowired
  DataSource dataSource;

  @BeforeEach
  void seedOnce() throws Exception {
    if (seeded) {
      return;
    }
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      seedFile(connection);
      seedActivity(connection);
      seedAuditLog(connection);
      seedUserRole(connection);
      seedDoc(connection);
      connection.commit();
    }
    for (String table : new String[] {"file", "activity", "audit_log", "user_role", "doc"}) {
      exec("ANALYZE TABLE " + table);
    }
    seeded = true;
  }

  @Test
  @DisplayName("附件列表：按 (object_type, object_id) 走 idx_file_object，不全表扫")
  void fileListUsesObjectIndex() throws Exception {
    assertPlanUses("SELECT id FROM file WHERE object_type = 'story' AND object_id = " + HOT_OBJECT_ID
        + " AND deleted_at IS NULL ORDER BY id DESC LIMIT 20", "idx_file_object");
  }

  @Test
  @DisplayName("我的动态流：按 actor 走 idx_activity_actor，游标倒序不用 filesort")
  void myActivitiesUseActorIndex() throws Exception {
    assertPlanUses("SELECT id FROM activity WHERE actor = 't53-me' ORDER BY id DESC LIMIT 51", "idx_activity_actor");
  }

  @Test
  @DisplayName("审计默认列表：无过滤时按 (created_at, id) 走 idx_audit_log_created（不再全表 filesort）")
  void auditDefaultListUsesCreatedIndex() throws Exception {
    assertPlanUses("SELECT id FROM audit_log ORDER BY created_at DESC, id DESC LIMIT 20", "idx_audit_log_created");
  }

  @Test
  @DisplayName("审计按动作过滤：走 idx_audit_log_action")
  void auditActionFilterUsesActionIndex() throws Exception {
    assertPlanUses("SELECT id FROM audit_log WHERE action = 'login' ORDER BY created_at DESC, id DESC LIMIT 20",
        "idx_audit_log_action");
  }

  @Test
  @DisplayName("角色反向读：按 role_id 走 idx_user_role_role（uq_user_role 前缀是 account_id，用不上）")
  void roleMembersUseRoleIndex() throws Exception {
    assertPlanUses("SELECT account_id FROM user_role WHERE role_id = 42", "idx_user_role_role");
  }

  @Test
  @DisplayName("T56 文档子树：path 前缀走 idx_doc_path（前导通配 %prefix%% 用不上索引，只能全表扫）")
  void docSubtreeUsesPathIndex() throws Exception {
    assertPlanUses("SELECT id FROM doc WHERE path LIKE ',777,%' AND deleted_at IS NULL", "idx_doc_path");
  }

  /** `EXPLAIN` 选中了期望的索引；失败时把整行计划打出来（一眼看出优化器改选了什么）。 */
  private void assertPlanUses(String sql, String expectedIndex) throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("EXPLAIN " + sql)) {
      assertNotNull(rows);
      rows.next();
      StringBuilder plan = new StringBuilder();
      for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
        plan.append(rows.getMetaData().getColumnLabel(column)).append('=').append(rows.getString(column)).append(' ');
      }
      assertEquals(expectedIndex, rows.getString("key"), "优化器没选期望的索引，实际计划：" + plan);
    }
  }

  /** 生产形态：OBJECTS 个对象、每个对象 PER_OBJECT 个附件（热对象与冷对象同形）。 */
  private void seedFile(Connection connection) throws Exception {
    exec(connection, "DELETE FROM file");
    String sql = "INSERT INTO file (title, path, extension, size, object_type, object_id, created_by)"
        + " VALUES (?,?,?,?,?,?,?)";
    try (PreparedStatement insert = connection.prepareStatement(sql)) {
      int row = 0;
      for (int index = 0; index < OBJECTS; index++) {
        long objectId = index == 0 ? HOT_OBJECT_ID : HOT_OBJECT_ID + index;
        for (int item = 0; item < PER_OBJECT; item++) {
          insert.setString(1, "f" + row);
          insert.setString(2, "p/" + row++);
          insert.setString(3, "txt");
          insert.setLong(4, 10);
          insert.setString(5, "story");
          insert.setLong(6, objectId);
          insert.setString(7, "t53");
          insert.addBatch();
        }
      }
      insert.executeBatch();
    }
  }

  /** 一个热操作人 200 行、其余散布，模拟"我的动态流"在多用户大表上的选择率。 */
  private void seedActivity(Connection connection) throws Exception {
    exec(connection, "DELETE FROM activity");
    String sql = "INSERT INTO activity (object_type, object_id, actor, action) VALUES (?,?,?,?)";
    try (PreparedStatement insert = connection.prepareStatement(sql)) {
      for (int index = 0; index < ACTIVITY_ROWS; index++) {
        insert.setString(1, "story");
        insert.setLong(2, index);
        insert.setString(3, index % 100 == 0 ? "t53-me" : "t53-other-" + (index % 500));
        insert.setString(4, "edited");
        insert.addBatch();
      }
      insert.executeBatch();
    }
  }

  /** 审计表是单调增长的那张：20k 行按秒递增的时间戳铺开，"login" 占 1/50。 */
  private void seedAuditLog(Connection connection) throws Exception {
    exec(connection, "DELETE FROM audit_log");
    String sql = "INSERT INTO audit_log (account, action, created_at) VALUES (?,?, DATE_ADD(NOW(), INTERVAL ? SECOND))";
    try (PreparedStatement insert = connection.prepareStatement(sql)) {
      for (int index = 0; index < AUDIT_ROWS; index++) {
        insert.setString(1, "t53-" + (index % 100));
        insert.setString(2, index % 50 == 0 ? "login" : "story-edited");
        insert.setInt(3, index);
        insert.addBatch();
      }
      insert.executeBatch();
    }
  }

  /** 5k 账号挂 100 个角色：按 role_id 反查（角色成员/删除清成员）命中少量行。 */
  private void seedUserRole(Connection connection) throws Exception {
    exec(connection, "DELETE FROM user_role");
    String sql = "INSERT INTO user_role (account_id, role_id) VALUES (?,?)";
    try (PreparedStatement insert = connection.prepareStatement(sql)) {
      for (int index = 0; index < 5_000; index++) {
        insert.setLong(1, index + 1L);
        insert.setLong(2, index % 100 == 0 ? 42 : 900 + index);
        insert.addBatch();
      }
      insert.executeBatch();
    }
  }

  /**
   * 生产形态：DOC_CHAPTERS 个章节、每章 DOCS_PER_CHAPTER 篇——子树查询（T56 的 `likeLeft` 前缀）的选择性
   * 由章节数撑起来，前缀 `,777,` 命中 1 章 + 其 10 篇。
   */
  private void seedDoc(Connection connection) throws Exception {
    exec(connection, "DELETE FROM doc");
    String sql = "INSERT INTO doc (doc_space_id, parent_id, path, title, created_by) VALUES (?,?,?,?,?)";
    try (PreparedStatement insert = connection.prepareStatement(sql)) {
      int row = 0;
      for (int chapter = 1; chapter <= DOC_CHAPTERS; chapter++) {
        insert.setLong(1, 1);
        insert.setLong(2, 0);
        insert.setString(3, "," + chapter + ",");
        insert.setString(4, "chapter-" + chapter);
        insert.setString(5, "t56");
        insert.addBatch();
        for (int item = 0; item < DOCS_PER_CHAPTER; item++) {
          long docId = 1_000_000L + row++;
          insert.setLong(1, 1);
          insert.setLong(2, chapter);
          insert.setString(3, "," + chapter + "," + docId + ",");
          insert.setString(4, "doc-" + docId);
          insert.setString(5, "t56");
          insert.addBatch();
        }
      }
      insert.executeBatch();
    }
  }

  private void exec(String sql) throws Exception {    try (Connection connection = dataSource.getConnection()) {
      exec(connection, sql);
    }
  }

  private void exec(Connection connection, String sql) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
