package net.zentao.db.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 06 A7-5 种子口令外置：V22__admin_initial_password 三分支直测（H2 真连接 + 假 Context，不经 Flyway）。
 * 覆盖「无配置 → 随机口令 + 打日志 + 置首登改密标记」「有配置 → 用配置口令且不置标记」「口令已自定义 → 不动」。
 */
class AdminInitialPasswordTest {

  private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

  private Connection connection;

  @AfterEach
  void close() throws SQLException {
    if (connection != null) {
      connection.close();
    }
  }

  private Connection seededConnection(String adminHash) throws SQLException {
    connection = DriverManager.getConnection(
        "jdbc:h2:mem:v22-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
    try (Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE account (id BIGINT NOT NULL PRIMARY KEY, account VARCHAR(64) NOT NULL, "
          + "password VARCHAR(100) NOT NULL, must_change_password TINYINT NOT NULL DEFAULT 0, "
          + "deleted_at TIMESTAMP NULL)");
      statement.execute("INSERT INTO account (id, account, password) VALUES (1, 'admin', '" + adminHash + "')");
    }
    return connection;
  }

  private static Context contextOf(Connection connection) {
    return new Context() {
      @Override
      public Configuration getConfiguration() {
        return null;
      }

      @Override
      public Connection getConnection() {
        return connection;
      }
    };
  }

  private String storedHash() throws SQLException {
    try (Statement statement = connection.createStatement();
         ResultSet rows = statement.executeQuery("SELECT password FROM account WHERE account = 'admin'")) {
      assertTrue(rows.next());
      return rows.getString("password");
    }
  }

  private int mustChangeFlag() throws SQLException {
    try (Statement statement = connection.createStatement();
         ResultSet rows = statement.executeQuery(
             "SELECT must_change_password FROM account WHERE account = 'admin'")) {
      assertTrue(rows.next());
      return rows.getInt("must_change_password");
    }
  }

  @Test
  @DisplayName("无配置：随机口令（16 位含字母数字）落库 + 置标记 + 日志只打印一次该口令")
  void randomPasswordIsLoggedOnceAndRequiresChange() throws Exception {
    seededConnection(ENCODER.encode("admin123"));
    Logger logger = (Logger) LoggerFactory.getLogger(V22__admin_initial_password.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    new V22__admin_initial_password("").migrate(contextOf(connection));
    logger.detachAppender(appender);

    assertEquals(1, appender.list.size(), "随机口令只打印一条日志");
    ILoggingEvent event = appender.list.getFirst();
    assertEquals(Level.WARN, event.getLevel());
    String message = event.getFormattedMessage();
    assertTrue(message.contains("随机初始口令"), message);
    String password = message.substring(message.indexOf("= ") + 2, message.indexOf("（仅本次启动打印一次"));
    assertEquals(16, password.length());
    assertTrue(ENCODER.matches(password, storedHash()), "日志里的口令应能通过库存哈希校验");
    assertEquals(1, mustChangeFlag(), "随机初始口令必须置首登强制改密标记");
    assertNotEquals("admin123", password);
  }

  @Test
  @DisplayName("有配置：以配置口令落库、不置首登改密标记、不再打随机口令")
  void configuredPasswordWinsWithoutForceChange() throws Exception {
    seededConnection(ENCODER.encode("admin123"));
    Logger logger = (Logger) LoggerFactory.getLogger(V22__admin_initial_password.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    new V22__admin_initial_password("OpsSecret123").migrate(contextOf(connection));
    logger.detachAppender(appender);

    assertTrue(ENCODER.matches("OpsSecret123", storedHash()), "库内口令应为配置值");
    assertEquals(0, mustChangeFlag(), "显式配置的口令不强制改密");
    assertTrue(appender.list.stream().noneMatch(event -> event.getLevel() == Level.WARN));
  }

  @Test
  @DisplayName("口令已自定义的既有库：哈希与标记均不动（只对仍是种子口令的库生效）")
  void customPasswordIsLeftUntouched() throws Exception {
    String custom = ENCODER.encode("AlreadyChanged123");
    seededConnection(custom);

    new V22__admin_initial_password("").migrate(contextOf(connection));
    new V22__admin_initial_password("OpsSecret123").migrate(contextOf(connection));

    assertEquals(custom, storedHash());
    assertEquals(0, mustChangeFlag());
  }

  @Test
  @DisplayName("无 admin 行的迁移库（旧数据接管）：静默跳过不报错")
  void missingAdminRowIsSkipped() throws Exception {
    seededConnection(ENCODER.encode("admin123"));
    try (PreparedStatement delete = connection.prepareStatement("DELETE FROM account")) {
      delete.executeUpdate();
    }
    new V22__admin_initial_password("").migrate(contextOf(connection));
    try (Statement statement = connection.createStatement();
         ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM account")) {
      assertTrue(rows.next());
      assertEquals(0, rows.getInt(1), "无行可改，迁移应静默跳过");
    }
  }
}
