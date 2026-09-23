package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import net.zentao.platform.session.SessionTokenHash;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 存量会话补 token_hash（T13 P1-1）：V28 只加了列，值要按 sha256 算。
 * sha256 在 SQL 侧不可移植（MySQL 的 SHA2 在测试用的 H2 上没有），故走 Java 迁移逐行回填——
 * 不回填的话，升级前已有的会话在在线用户页里没有对外 id，既认不出也强退不了。
 */
public class V29__session_token_hash_backfill extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement select = connection.createStatement();
        ResultSet rows = select.executeQuery("SELECT id FROM session WHERE token_hash IS NULL");
        PreparedStatement update = connection.prepareStatement("UPDATE session SET token_hash = ? WHERE id = ?")) {
      while (rows.next()) {
        String id = rows.getString(1);
        update.setString(1, SessionTokenHash.of(id));
        update.setString(2, id);
        update.addBatch();
      }
      update.executeBatch();
    }
  }
}
