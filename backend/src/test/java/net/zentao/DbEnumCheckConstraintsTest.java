package net.zentao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DB 细节看护（T67 / AUDIT DB-10 + DB-17 精度半）：枚举列 CHECK 约束与工时精度口径钉成断言。
 *
 * <p>为什么值得用例：CHECK 是**静默失效率最高**的那类东西——迁移里少一条、有人顺手删约束、
 * 或工时列被改回旧口径，功能全绿、只有直写库的 bug 或 2038/截断那天才炸。这里守三件事：
 * <ul>
 *   <li>50 条 {@code ck_<表>_<列>} **一条不少、一条不多**（名字集精确相等：删一条/改名即红）；</ul>
 *   <li>抽样列**真挡非法值、真放合法值**（H2 强制 CHECK——合法值被挡在门外同样是事故，故两向都断言）；
 *   <li>工时家族存储口径 = {@code DECIMAL(12,2)}（V41 只放宽不收窄的统一值）。
 * </ul>
 * 真 MySQL 侧同一链路由 MigrationCliIT（-Pit）回放验证。
 */
class DbEnumCheckConstraintsTest extends H2TestSupport {

  /** V40 全量约束名（改动迁移里的约束 = 改这里，review 时对得上）。 */
  private static final Set<String> EXPECTED_CHECKS = Set.of(
      "ck_account_gender", "ck_account_status",
      "ck_menu_status", "ck_menu_node_type",
      "ck_dict_type_status", "ck_dict_data_status", "ck_lang_import_status",
      "ck_doc_space_type", "ck_doc_space_acl", "ck_doc_space_doc_sort",
      "ck_doc_type", "ck_doc_status", "ck_doc_acl",
      "ck_product_type", "ck_product_status", "ck_product_acl",
      "ck_branch_status", "ck_category_type", "ck_plan_status", "ck_plan_closed_reason",
      "ck_product_release_status",
      "ck_project_type", "ck_project_model", "ck_project_acl", "ck_project_budget_unit",
      "ck_stage_type", "ck_board_space_type", "ck_board_status", "ck_board_card_status", "ck_stakeholder_type",
      "ck_story_type", "ck_story_source", "ck_story_status", "ck_story_closed_reason",
      "ck_task_type", "ck_task_status",
      "ck_bug_type", "ck_bug_status", "ck_bug_resolution",
      "ck_test_case_type", "ck_test_case_status", "ck_test_case_last_run_result",
      "ck_suite_type", "ck_test_run_type", "ck_test_run_status", "ck_test_run_case_result",
      "ck_todo_type", "ck_todo_status",
      // V43（T04 审计 2.0）：分类 9 类 / 结果 3 值（真源 = AuditCatalog 与 AuditResult）
      "ck_audit_log_category", "ck_audit_log_result");

  @Autowired
  DataSource dataSource;

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("DB-10：50 条枚举 CHECK 一条不少、一条不多（删/改名/漂移即红）")
  void allEnumChecksPresent() throws Exception {
    Set<String> actual = new LinkedHashSet<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement query = connection.prepareStatement(
            "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS"
                + " WHERE CONSTRAINT_SCHEMA = SCHEMA()")) {
      ResultSet rows = query.executeQuery();
      while (rows.next()) {
        String name = rows.getString(1).toLowerCase();
        if (name.startsWith("ck_")) {
          actual.add(name);
        }
      }
    }
    Set<String> missing = new LinkedHashSet<>(EXPECTED_CHECKS);
    missing.removeAll(actual);
    Set<String> unexpected = new LinkedHashSet<>(actual);
    unexpected.removeAll(EXPECTED_CHECKS);
    assertTrue(missing.isEmpty(), "缺 CHECK 约束：" + missing);
    assertTrue(unexpected.isEmpty(), "多出计划外 CHECK（登记进 V40 与本用例才合法）：" + unexpected);
  }

  @Test
  @DisplayName("CHECK 双向生效：非法枚举值进不了库，合法值进得去（抽样 8 列）")
  void enumChecksRejectIllegalAndAcceptLegal() {
    String suffix = Long.toString(System.nanoTime());
    // （表, 列值非法的 insert, 合法的 insert, 清理 SQL）——非法必须被约束挡下，合法必须放行
    assertRejected(() -> jdbcTemplate.update(
        "INSERT INTO account (account, password, real_name, gender) VALUES ('enum-a" + suffix + "', 'x', 'probe', 'x')"));
    jdbcTemplate.update("INSERT INTO account (account, password, real_name, gender) VALUES ('enum-a" + suffix + "', 'x', 'probe', 'f')");
    jdbcTemplate.update("DELETE FROM account WHERE account = 'enum-a" + suffix + "'");

    assertRejected(() -> jdbcTemplate.update(
        "INSERT INTO menu (parent_key, node_key, title, path, node_type) VALUES ('admin', 'db-enum-" + suffix
            + "', 'probe', '/enum-m" + suffix + "', 'popup')"));
    jdbcTemplate.update("INSERT INTO menu (parent_key, node_key, title, path, node_type) VALUES ('admin', 'db-enum-"
        + suffix + "', 'probe', '/enum-m" + suffix + "', 'button')");
    jdbcTemplate.update("DELETE FROM menu WHERE path = '/enum-m" + suffix + "'");

    assertRejected(() -> jdbcTemplate.update(
        "INSERT INTO project (name, budget_unit) VALUES ('enum-p" + suffix + "', 'EUR')"));
    jdbcTemplate.update("INSERT INTO project (name, budget_unit) VALUES ('enum-p" + suffix + "', 'USD')");
    jdbcTemplate.update("DELETE FROM project WHERE name = 'enum-p" + suffix + "'");

    assertRejected(() -> jdbcTemplate.update(
        "INSERT INTO task (execution_id, title, status) VALUES (999999, 'enum-t" + suffix + "', 'archived')"));
    jdbcTemplate.update("INSERT INTO task (execution_id, title, status) VALUES (999999, 'enum-t" + suffix + "', 'pause')");
    jdbcTemplate.update("DELETE FROM task WHERE title = 'enum-t" + suffix + "'");

    assertRejected(() -> jdbcTemplate.update(
        "INSERT INTO bug (product_id, title, resolution) VALUES (999999, 'enum-b" + suffix + "', 'meh')"));
    jdbcTemplate.update("INSERT INTO bug (product_id, title, resolution) VALUES (999999, 'enum-b" + suffix + "', 'fixed')");
    jdbcTemplate.update("DELETE FROM bug WHERE title = 'enum-b" + suffix + "'");

    assertRejected(() -> jdbcTemplate.update(
        "INSERT INTO lang_import (lang, file_name, status) VALUES ('zh-cn', 'enum-l" + suffix + ".xlsx', 'partial')"));
    jdbcTemplate.update("INSERT INTO lang_import (lang, file_name, status) VALUES ('zh-cn', 'enum-l" + suffix + ".xlsx', 'failed')");
    jdbcTemplate.update("DELETE FROM lang_import WHERE file_name = 'enum-l" + suffix + ".xlsx'");

    assertRejected(() -> jdbcTemplate.update(
        "INSERT INTO board_card (board_id, lane_id, name, status) VALUES (999999, 999999, 'enum-c" + suffix + "', 'blocked')"));
    jdbcTemplate.update("INSERT INTO board_card (board_id, lane_id, name, status) VALUES (999999, 999999, 'enum-c" + suffix + "', 'done')");
    jdbcTemplate.update("DELETE FROM board_card WHERE name = 'enum-c" + suffix + "'");

    assertRejected(() -> jdbcTemplate.update(
        "INSERT INTO audit_log (action, category, result) VALUES ('enum-a" + suffix + "', 'nope', 'success')"));
    jdbcTemplate.update(
        "INSERT INTO audit_log (action, category, result) VALUES ('enum-a" + suffix + "', 'auth', 'success')");
    jdbcTemplate.update("DELETE FROM audit_log WHERE action = 'enum-a" + suffix + "'");
  }

  @Test
  @DisplayName("DB-17 精度半：工时家族统一 DECIMAL(12,2)（V41 只放宽不收窄的口径）")
  void hoursPrecisionUnified() throws Exception {
    String[][] columns = {
        {"task", "estimate_hours"}, {"task", "consumed_hours"}, {"task", "left_hours"},
        {"effort", "consumed_hours"}, {"effort", "left_hours"},
        {"project", "estimate_hours"}, {"project", "consumed_hours"}, {"project", "left_hours"},
        {"story", "estimate_hours"}, {"board_card", "estimate_hours"}, {"team_member", "hours"},
    };
    try (Connection connection = dataSource.getConnection();
        PreparedStatement query = connection.prepareStatement(
            "SELECT NUMERIC_PRECISION, NUMERIC_SCALE FROM INFORMATION_SCHEMA.COLUMNS"
                + " WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
      for (String[] target : columns) {
        query.setString(1, target[0]);
        query.setString(2, target[1]);
        try (ResultSet rows = query.executeQuery()) {
          assertTrue(rows.next(), "找不到列 " + target[0] + "." + target[1]);
          assertEquals(12, rows.getInt(1), target[0] + "." + target[1] + " 精度应为 12");
          assertEquals(2, rows.getInt(2), target[0] + "." + target[1] + " 标度应为 2");
        }
      }
    }
  }

  private void assertRejected(Runnable insert) {
    assertThrows(DataIntegrityViolationException.class, insert::run,
        "非法枚举值应当被 CHECK 约束拒绝");
  }
}
