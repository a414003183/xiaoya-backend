package net.zentao.db.migration;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 06 A7-5：admin 初始口令外置 + 首登强制改密标记的落地迁移。
 *
 * <p>历史迁移不改（V3__org_seed 的 admin/admin123 硬编码保留原样，Flyway 禁止改已执行脚本），
 * 本迁移在其后修正：仅当 admin 行仍持有 V3 种子口令（BCrypt 随盐比对通过）时才动手——
 * <ul>
 *   <li>配置了 {@code ZENTAO_ADMIN_INITIAL_PASSWORD}（映射为 zentao.admin.initial-password）：
 *       口令置为配置值，标记保持 0（口令由运维显式给定，不再强制改；e2e/容器化部署依赖此语义）；</li>
 *   <li>未配置：生成 16 位随机初始口令，标记置 1，口令以 WARN 日志**只打印这一次**
 *       （首登必须改密，前端会话门禁据此拦截业务路由）。</li>
 * </ul>
 * 已自行改过口令的既有库（哈希与种子不符）一律不动；全新库 V3 刚种下 admin，本迁移紧随其后生效。
 *
 * <p>实现方式：本类是 Spring 组件，由 Spring Boot 的 Flyway 自动装配按 {@code JavaMigration} Bean
 * 交给 Flyway（V3 那类“按包扫描”的迁移取不到配置，故本迁移改走 Bean 注入）。类名即版本号
 * （V22__admin_initial_password），包名刻意错开 {@code db.migration}，避免被 Flyway 的包扫描重复登记。
 */
@Component
public class V22__admin_initial_password extends BaseJavaMigration {

  private static final Logger LOG = LoggerFactory.getLogger(V22__admin_initial_password.class);

  /** V3__org_seed 写入的种子口令；用于识别“口令从未改过”的库。 */
  static final String SEED_PASSWORD = "admin123";

  /** 无歧义字符集（去掉 0/O/1/l/I），16 位含字母与数字，满足 06 A7-4 口令策略（≥8 且字母+数字）。 */
  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
  private static final int PASSWORD_LENGTH = 16;

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

  private final String configuredPassword;

  public V22__admin_initial_password(
      @Value("${zentao.admin.initial-password:}") String configuredPassword) {
    this.configuredPassword = configuredPassword == null ? "" : configuredPassword.strip();
  }

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Long adminId = null;
    String currentHash = null;
    try (PreparedStatement select = connection.prepareStatement(
        "SELECT id, password FROM account WHERE account = 'admin' AND deleted_at IS NULL");
         ResultSet rows = select.executeQuery()) {
      if (rows.next()) {
        adminId = rows.getLong("id");
        currentHash = rows.getString("password");
      }
    }
    if (adminId == null || currentHash == null || !ENCODER.matches(SEED_PASSWORD, currentHash)) {
      return; // 无 admin 行（迁移库由旧数据接管）或口令已自定义：不动
    }
    if (!configuredPassword.isEmpty()) {
      if (!ENCODER.matches(configuredPassword, currentHash)) {
        update(connection, adminId, ENCODER.encode(configuredPassword), false);
      }
      LOG.info("内置 admin 初始口令已按 zentao.admin.initial-password 配置生效。");
      return;
    }
    String generated = generatePassword();
    update(connection, adminId, ENCODER.encode(generated), true);
    // 一次性口令只在此打印一次（迁移只执行一次；此后哈希已变，重启不再命中）
    LOG.warn("内置 admin 随机初始口令 = {}（仅本次启动打印一次，首次登录必须修改）", generated);
  }

  /** 随机初始口令：16 位无歧义字符，含字母与数字（与 tools/migration 生成口径一致）。 */
  static String generatePassword() {
    StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
    do {
      password.setLength(0);
      for (int index = 0; index < PASSWORD_LENGTH; index++) {
        password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
      }
    } while (!hasLetterAndDigit(password));
    return password.toString();
  }

  private static boolean hasLetterAndDigit(CharSequence value) {
    boolean letter = false;
    boolean digit = false;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (Character.isLetter(character)) {
        letter = true;
      } else if (Character.isDigit(character)) {
        digit = true;
      }
      if (letter && digit) {
        return true;
      }
    }
    return false;
  }

  private static void update(Connection connection, long adminId, String hash, boolean mustChange)
      throws SQLException {
    try (PreparedStatement update = connection.prepareStatement(
        "UPDATE account SET password = ?, must_change_password = ? WHERE id = ?")) {
      update.setString(1, hash);
      update.setInt(2, mustChange ? 1 : 0);
      update.setLong(3, adminId);
      update.executeUpdate();
    }
  }
}
