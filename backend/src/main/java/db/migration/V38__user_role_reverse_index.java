package db.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 角色反向读索引（T53 第 6 条索引）：`WHERE role_id = ?` 的三个查询面——角色删除清成员、角色成员列表、
 * 成员全量替换（`RoleRepositoryImpl`）与「角色下账号」（`AccountRepositoryImpl`）——而
 * `uq_user_role (account_id, role_id)` 的前缀是 account_id，按 role_id 单查用不上它。
 *
 * <p><b>为什么是 Java 迁移而不是 SQL</b>：`user_role` 由 V33（同为 Java 迁移）创建，而旧库导入 CLI 的
 * Flyway 只跑 `--schema-dir` 里的 SQL（Java 迁移在那边无法编译执行，见 `Main#markJavaSeedMigrated`），
 * 于是 SQL 链里引用 user_role 会让**旧库导入在 V37 处直接断链**（实测：`Table "user_role" not found`；
 * 后端单测与 CI 都不跑那条链，只有 `tools/migration` 的 119 个迁移单测会红）。放这里两边都对：
 * CLI 跳过它（那个库里还没有 user_role，与 V33 一致），backend 启动时按版本序 V33 → V38 依次应用。
 *
 * <p>不需要幂等守卫：Flyway 对每个版本只应用一次。
 */
public class V38__user_role_reverse_index extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("CREATE INDEX idx_user_role_role ON user_role (role_id)");
    }
  }
}
