package db.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 角色统一（T23）：把两类角色合成一张 `role` 表。
 *
 * <p>旧模型有两套「角色」：权限角色 `auth_group`（权限码矩阵 + 成员 + 数据权限）与岗位角色
 * `account_role`（账号资料上的一个标签，带多语言名字）。管理端于是有两个入口、两套字段、两套守卫，
 * 用户判定为重复——统一后一个角色就是「一组权限码 + 一批成员 + 一份数据权限」，账号的角色是成员关系。
 *
 * <p>三张新表：`role`（角色本体，`acl` 是数据权限 JSON，`builtin` 标记内置角色不可删）、
 * `user_role`（账号 ↔ 角色，原 `user_group`）、`role_priv`（角色 ↔ 权限码，原 `group_priv`）。
 *
 * <p>迁移用 Java 而非 SQL：`account_role.labels` 是「语言码 → 角色名」的 JSON，挑一个语言当角色名
 * 需要在 Java 里解析（SQL 里解析 JSON 依赖具体数据库）。id 沿用旧权限组，故超管组仍是 id=1，
 * `user_group` 的成员关系原样搬过来。岗位角色的持有者（`account.role`）转成对应角色的成员。
 */
public class V33__role_unify extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("""
          CREATE TABLE role (
              id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
              code         VARCHAR(32)  NULL,
              name         VARCHAR(60)  NOT NULL,
              description  VARCHAR(255) NOT NULL DEFAULT '',
              acl          TEXT         NULL,
              builtin      TINYINT      NOT NULL DEFAULT 0,
              sort         INT          NOT NULL DEFAULT 0,
              created_by   VARCHAR(64)  NULL,
              created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_by   VARCHAR(64)  NULL,
              updated_at   TIMESTAMP    NULL,
              lock_version INT          NOT NULL DEFAULT 0,
              CONSTRAINT uq_role_name UNIQUE (name),
              CONSTRAINT uq_role_code UNIQUE (code)
          )
          """);
      statement.execute("""
          CREATE TABLE user_role (
              id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
              account_id BIGINT NOT NULL,
              role_id    BIGINT NOT NULL,
              CONSTRAINT uq_user_role UNIQUE (account_id, role_id)
          )
          """);
      statement.execute("""
          CREATE TABLE role_priv (
              id        BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
              role_id   BIGINT      NOT NULL,
              priv_code VARCHAR(64) NOT NULL,
              CONSTRAINT uq_role_priv UNIQUE (role_id, priv_code)
          )
          """);

      // 权限角色 → role（同 id：超管组 id=1 的内置身份与 user_group 的引用都不变）
      statement.execute("""
          INSERT INTO role (id, code, name, description, acl, builtin, sort, created_by, created_at, updated_by,
                            updated_at, lock_version)
          SELECT id, NULL, name, description, acl, CASE WHEN id = 1 THEN 1 ELSE 0 END, id, created_by, created_at,
                 updated_by, updated_at, lock_version
          FROM auth_group
          """);
      statement.execute("INSERT INTO user_role (account_id, role_id) SELECT account_id, group_id FROM user_group");
      statement.execute("INSERT INTO role_priv (role_id, priv_code) SELECT group_id, priv_code FROM group_priv");

      // 岗位角色 → role（多语言名字挑一个：zh-CN → 任意非空 → code），持有者转成该角色的成员
      Map<String, Long> roleIdByCode = migrateAccountRoles(context);
      try (PreparedStatement select = context.getConnection().prepareStatement(
          "SELECT id, role FROM account WHERE role IS NOT NULL AND role <> ''");
          PreparedStatement insert = context.getConnection().prepareStatement(
              "INSERT INTO user_role (account_id, role_id) VALUES (?, ?)")) {
        try (ResultSet rows = select.executeQuery()) {
          while (rows.next()) {
            Long roleId = roleIdByCode.get(rows.getString("role"));
            if (roleId != null) {
              insert.setLong(1, rows.getLong("id"));
              insert.setLong(2, roleId);
              insert.addBatch();
            }
          }
        }
        insert.executeBatch();
      }

      // 旧表与旧列退场：留下两套「角色」等于没统一
      statement.execute("DROP TABLE group_priv");
      statement.execute("DROP TABLE user_group");
      statement.execute("DROP TABLE account_role");
      statement.execute("DROP TABLE auth_group");
      statement.execute("ALTER TABLE account DROP COLUMN role");
    }
  }

  /** 逐行搬岗位角色：name 取 labels 里的中文名（没有就取任一个非空值，再没有就用 code），重名加后缀。 */
  private Map<String, Long> migrateAccountRoles(Context context) throws Exception {
    Map<String, Long> idByCode = new LinkedHashMap<>();
    Set<String> takenNames = new LinkedHashSet<>();
    try (Statement statement = context.getConnection().createStatement();
        ResultSet rows = statement.executeQuery("SELECT code, labels, sort, builtin, created_by FROM account_role")) {
      while (rows.next()) {
        String code = rows.getString("code");
        String name = uniqueName(labelOf(rows.getString("labels"), code), takenNames);
        try (PreparedStatement insert = context.getConnection().prepareStatement(
            "INSERT INTO role (code, name, description, builtin, sort, created_by) VALUES (?, ?, '', ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS)) {
          insert.setString(1, code);
          insert.setString(2, name);
          insert.setInt(3, rows.getInt("builtin"));
          insert.setInt(4, rows.getInt("sort"));
          insert.setString(5, rows.getString("created_by"));
          insert.executeUpdate();
          try (ResultSet keys = insert.getGeneratedKeys()) {
            if (keys.next()) {
              idByCode.put(code, keys.getLong(1));
            }
          }
        }
      }
    }
    return idByCode;
  }

  private static String labelOf(String labelsJson, String fallback) {
    if (labelsJson == null || labelsJson.isBlank()) {
      return fallback;
    }
    try {
      JsonNode root = JsonMapper.builder().build().readTree(labelsJson);
      JsonNode zh = root.get("zh-CN");
      if (zh != null && !zh.asText().isBlank()) {
        return zh.asText();
      }
      for (JsonNode value : root) {
        if (!value.asText().isBlank()) {
          return value.asText();
        }
      }
    } catch (Exception e) {
      // 坏 JSON 不该拦住迁移：退回 code 当名字
      return fallback;
    }
    return fallback;
  }

  private static String uniqueName(String name, Set<String> taken) {
    String candidate = name.length() > 60 ? name.substring(0, 60) : name;
    int suffix = 2;
    while (!taken.add(candidate)) {
      String tail = " (" + suffix + ")";
      candidate = (name.length() > 60 - tail.length() ? name.substring(0, 60 - tail.length()) : name) + tail;
      suffix += 1;
    }
    return candidate;
  }
}
