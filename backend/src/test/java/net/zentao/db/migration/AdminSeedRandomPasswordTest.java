package net.zentao.db.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 06 A7-5 验收（无 env 分支，即卡面「干净库起服」场景）：不配置初始口令 → V22 迁移把 V3 的
 * admin/admin123 换成 16 位随机口令并置首登强制改密标记（随机口令与日志形态由
 * {@link AdminInitialPasswordTest} 直测迁移逻辑，此处验 Flyway 装配后的真实库效果），
 * 本人改密后标记清零。
 *
 * <p>随机口令不落任何持久化介质，HTTP 层无法用它登录；故此处把库内口令换成测试已知值（标记保持 1，
 * 即「首登时的一次性口令状态」），再走 /me → 改密 → /me 全链路。
 *
 * <p>独立 H2 库：本用例会替换 admin 口令，落在共享库会污染其余以 admin/admin123 为基线的用例。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:a7-5-seed-random;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "zentao.admin.initial-password=",
    })
class AdminSeedRandomPasswordTest extends ApiTestSupport {

  private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

  @Autowired
  private javax.sql.DataSource dataSource;

  @Test
  @DisplayName("无配置：种子口令被随机口令替换且标记为真；改密后标记为假、新口令可登录")
  void randomInitialPasswordForcesChange() throws Exception {
    String hash = adminHash();
    assertFalse(ENCODER.matches("admin123", hash), "V3 种子口令必须已被随机口令替换");
    assertEquals(1, adminFlag(), "随机初始口令必须置首登强制改密标记");
    assertEquals(401, send("POST", "/api/v1/session",
        "{\"account\":\"admin\",\"password\":\"admin123\"}", null).statusCode(), "种子硬编码口令必须作废");

    // 模拟首登持有一次性口令的状态：库内换成测试已知口令，标记保持为真
    try (Connection connection = dataSource.getConnection();
         var update = connection.prepareStatement("UPDATE account SET password = ? WHERE account = 'admin'")) {
      update.setString(1, ENCODER.encode("OneTimePass123"));
      update.executeUpdate();
    }

    String cookie = login("admin", "OneTimePass123");
    JsonNode me = data(send("GET", "/api/v1/me", null, cookie));
    long accountId = me.at("/account/id").asLong();
    assertTrue(me.at("/account/mustChangePassword").asBoolean(), "首登时 /me 应带强制改密标记");

    // 改密前业务端点照常可用（拦截只发生在前端会话门禁）
    assertEquals(200, send("GET", "/api/v1/accounts?limit=1", null, cookie).statusCode());

    HttpResponse<String> changed = send("POST", "/api/v1/accounts/" + accountId + "/password",
        "{\"oldPassword\":\"OneTimePass123\",\"newPassword\":\"RotatedPass123\"}", cookie);
    assertEquals(200, changed.statusCode(), changed.body());
    assertFalse(json.readTree(changed.body()).at("/data/mustChangePassword").asBoolean(), "改密响应标记即为假");
    assertFalse(data(send("GET", "/api/v1/me", null, cookie)).at("/account/mustChangePassword").asBoolean(),
        "改密后 /me 标记清零");
    assertEquals(0, adminFlag(), "标记必须落库清零（否则会话门禁会一直重定向）");

    login("admin", "RotatedPass123");
  }

  private String adminHash() throws Exception {
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement();
         ResultSet rows = statement.executeQuery("SELECT password FROM account WHERE account = 'admin'")) {
      assertTrue(rows.next());
      assertTrue(rows.getString(1).startsWith("$2"), "库内应为 BCrypt 哈希");
      return rows.getString(1);
    }
  }

  private int adminFlag() throws Exception {
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement();
         ResultSet rows = statement.executeQuery(
             "SELECT must_change_password FROM account WHERE account = 'admin'")) {
      assertTrue(rows.next());
      return rows.getInt(1);
    }
  }
}
