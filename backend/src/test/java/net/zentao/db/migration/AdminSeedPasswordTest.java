package net.zentao.db.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 06 A7-5 验收（env 注入分支）：属性覆盖模拟 ZENTAO_ADMIN_INITIAL_PASSWORD，干净库起服后以配置口令登录。
 * 显式配置 = 运维/CI 自己给的口令 → 不置首登强制改密标记（e2e 与容器化部署依赖此语义）。
 *
 * <p>独立 H2 库（不用共享 zentao 库）：本用例改 admin 口令，落在共享库会污染其余以
 * admin/admin123 为基线的用例类。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:a7-5-seed-env;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "zentao.admin.initial-password=SeedPass123",
    })
class AdminSeedPasswordTest extends ApiTestSupport {

  @Test
  @DisplayName("有配置：以配置口令登录、/me 不置标记、种子口令作废、改密后新口令生效")
  void configuredInitialPasswordWins() throws Exception {
    assertEquals(401, send("POST", "/api/v1/session",
        "{\"account\":\"admin\",\"password\":\"admin123\"}", null).statusCode(), "V3 种子口令必须已被替换");

    String cookie = login("admin", "SeedPass123");
    JsonNode me = data(send("GET", "/api/v1/me", null, cookie));
    long accountId = me.at("/account/id").asLong();
    assertFalse(me.at("/account/mustChangePassword").asBoolean(), "显式配置的初始口令不强制改密");

    // 业务端点可正常访问（拦截只发生在前端会话门禁，后端不全局拦，否则改密端点自身会被拦死）
    assertEquals(200, send("GET", "/api/v1/accounts?limit=1", null, cookie).statusCode());

    HttpResponse<String> changed = send("POST", "/api/v1/accounts/" + accountId + "/password",
        "{\"oldPassword\":\"SeedPass123\",\"newPassword\":\"RotatedPass123\"}", cookie);
    assertEquals(200, changed.statusCode(), changed.body());
    assertFalse(json.readTree(changed.body()).at("/data/mustChangePassword").asBoolean());
    assertFalse(data(send("GET", "/api/v1/me", null, cookie)).at("/account/mustChangePassword").asBoolean());

    login("admin", "RotatedPass123");
  }
}
