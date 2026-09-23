package net.zentao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 热查询索引的 schema 看护（T53）：把"哪张表上该有哪条索引、列序是什么"钉成断言。
 *
 * <p>为什么值得一条用例：索引是**静默**失效率最高的东西——迁移里少一行、将来某次重建表漏带索引、
 * 或有人"顺手"改列序，功能全绿、只有生产慢。真 MySQL 上"优化器确实选中它"由 IndexUsageIT（-Pit 才跑）
 * 负责；这里只守结构面，两边互为补充。
 */
class SchemaIndexTest extends H2TestSupport {

  @Autowired
  DataSource dataSource;

  @Test
  @DisplayName("T53 六个热查询索引都在，且列序与迁移一致（列序错=索引用不上）")
  void hotQueryIndexesExistWithExpectedColumns() throws Exception {
    Map<String, List<String>> expected = new LinkedHashMap<>();
    expected.put("idx_file_object", List.of("object_type", "object_id"));
    expected.put("idx_activity_actor", List.of("actor", "id"));
    expected.put("idx_audit_log_created", List.of("created_at", "id"));
    expected.put("idx_audit_log_action", List.of("action", "created_at"));
    expected.put("idx_audit_log_object", List.of("object_type", "object_id", "created_at"));
    expected.put("idx_user_role_role", List.of("role_id"));

    Map<String, List<String>> actual = indexColumns();
    for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
      assertTrue(actual.containsKey(entry.getKey()), "缺索引 " + entry.getKey() + "（现有：" + actual.keySet() + "）");
      assertEquals(entry.getValue(), actual.get(entry.getKey()),
          "索引 " + entry.getKey() + " 的列序变了——列序错等于索引白建");
    }
  }

  @Test
  @DisplayName("索引没有重复建设：role_priv/acl_entry 上不加（无查询面）")
  void noSpeculativeIndexesOnCoveredTables() throws Exception {
    Map<String, List<String>> actual = indexColumns();
    // role_priv 的读全走 role_id → 已被 uq_role_priv 前缀覆盖；acl_entry 的读全走 object_type+object_id → 被 uk_acl_entry 覆盖
    assertTrue(actual.keySet().stream().noneMatch(name -> name.contains("role_priv") && name.contains("code")),
        "role_priv 上不该有按 priv_code 的索引（无此查询）：" + actual.keySet());
    assertTrue(actual.keySet().stream().noneMatch(name -> name.contains("acl_entry") && name.contains("account")),
        "acl_entry 上不该有按 account 的索引（无此查询）：" + actual.keySet());
  }

  /** 索引名 → 按序排列的列名（H2 的 INFORMATION_SCHEMA，大小写按 SNAKE 是大写）。 */
  private Map<String, List<String>> indexColumns() throws Exception {
    Map<String, List<String>> byIndex = new LinkedHashMap<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement query = connection.prepareStatement(
            "SELECT INDEX_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS"
                + " WHERE TABLE_SCHEMA = SCHEMA() ORDER BY INDEX_NAME, ORDINAL_POSITION")) {
      ResultSet rows = query.executeQuery();
      while (rows.next()) {
        byIndex.computeIfAbsent(rows.getString(1).toLowerCase(), key -> new ArrayList<>())
            .add(rows.getString(2).toLowerCase());
      }
    }
    return byIndex;
  }
}
