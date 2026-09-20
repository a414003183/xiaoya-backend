package db.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * org 域种子（T-1）：内置 admin 账号 + id=1 超管组 + 成员关联。
 * BCrypt 哈希须运行时计算，故用 Java 迁移而非 SQL（platform 卡 §4.1 / org 卡 §7）。
 *
 * <p>历史迁移不可改（Flyway 已执行脚本冻结）：此处的 admin123 硬编码保持原样，
 * 初始口令外置与首登强制改密由 {@code V22__admin_initial_password} 在其后修正（06 A7-5）。
 */
public class V3__org_seed extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    String hash = new BCryptPasswordEncoder().encode("admin123");
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "INSERT INTO account (account, password, real_name, gender, status, created_by) VALUES ('admin', '"
              + hash
              + "', '管理员', 'm', 'active', 'system')");
      statement.execute("INSERT INTO auth_group (id, name, description) VALUES (1, '管理员', '内置超管组')");
      statement.execute("INSERT INTO auth_group (id, name, description) VALUES (2, '成员', '基础只读')");
      statement.execute("INSERT INTO group_priv (group_id, priv_code) VALUES (2, 'account-view'), (2, 'department-view')");
      statement.execute("INSERT INTO user_group (account_id, group_id) SELECT id, 1 FROM account WHERE account = 'admin'");
    }
  }
}
