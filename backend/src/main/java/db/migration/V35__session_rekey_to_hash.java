package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import net.zentao.platform.session.SessionTokenHash;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 存量会话主键改摘要（T51 SEC-03）：`session.id` 原本就是 cookie 的 ZT_SESSION 值，也就是凭据本体——
 * 库泄露 = 全员会话可被直接重放。本迁移把每行 id 就地换成 sha256(id)，凭据随即从库中消失。
 *
 * <p>为什么走 Java 而不是 SQL：sha256 在 SQL 侧不可移植（MySQL 有 SHA2、测试用的 H2 没有），
 * 口径同 V29 的 token_hash 回填。**行保留**：cookie 未变，升级后下一请求按摘要仍能查到同一条会话，
 * 在线用户不被打断。V36 随后删掉与 id 逐行同值的 token_hash 冗余列。
 */
public class V35__session_rekey_to_hash extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement select = connection.createStatement();
        ResultSet rows = select.executeQuery("SELECT id FROM session");
        PreparedStatement update = connection.prepareStatement("UPDATE session SET id = ? WHERE id = ?")) {
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
