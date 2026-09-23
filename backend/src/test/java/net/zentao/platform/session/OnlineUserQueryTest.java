package net.zentao.platform.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 在线用户（T13 P1-1）端到端：列表出会话行 → 强退 → 该 cookie 下一请求 401。
 *
 * <p>本类盯着两条不显眼但会咬人的性质：① 对外的 id 只能是 token 摘要，cookie 凭据不得出网
 * （session 表主键就是凭据，原样回给持 online-user-view 的角色等于拱手让出任意会话）；
 * ② 强退幂等——管理意图是「这条会话没了」，重复点或对方刚登出都不是错误。
 * 同 JVM 共享 H2，故断言一律按本用例自建的唯一账号取行，不依赖全局行数。
 */
class OnlineUserQueryTest extends ApiTestSupport {

  @Test
  @DisplayName("列表：按账号取到本人会话行，id 是 token 摘要（凭据不出网），current 只标自己那条")
  void listsSessionsWithHashedId() throws Exception {
    String adminCookie = login("admin", "admin123");
    String account = "onl-list-" + suffix();
    String cookie = accountWithPrivileges(adminCookie, account, "\"online-user-view\"");
    String token = tokenOf(cookie);

    HttpResponse<String> response =
        send("GET", "/api/v1/online-users?filters%5Baccount%5D=" + account, null, adminCookie);
    JsonNode list = data(response);
    assertEquals(1, list.get("total").asInt(), "本用例自建的唯一账号只有一条会话：" + list);
    JsonNode row = list.get("items").get(0);
    assertEquals(account, row.get("account").asText());
    assertEquals(SessionTokenHash.of(token), row.get("id").asText(), "对外 id 是 token 的 sha256");
    assertFalse(row.get("current").asBoolean(), "别人的会话不标 current");
    assertTrue(row.get("createdAt").asText().length() > 0 && row.get("expiresAt").asText().length() > 0,
        "登录时间与过期时间都要出：" + row);
    assertFalse(response.body().contains(token), "cookie 凭据本身不得出现在响应里：" + response.body());

    // current 只落在请求者自己那行：admin 名下的会话里恰有一条（本次登录），且摘要对得上
    JsonNode adminRows = data(send("GET", "/api/v1/online-users?limit=200&filters%5Baccount%5D=admin", null, adminCookie));
    int flagged = 0;
    for (JsonNode item : adminRows.get("items")) {
      if (item.get("current").asBoolean()) {
        flagged += 1;
        assertEquals(SessionTokenHash.of(tokenOf(adminCookie)), item.get("id").asText());
      }
    }
    assertEquals(1, flagged, "只有请求者自己那条会话标 current：" + adminRows);
  }

  @Test
  @DisplayName("强退：删行后该 cookie 401，二次强退仍 200（幂等），动作落审计")
  void kickDeletesSessionAndIsIdempotent() throws Exception {
    String adminCookie = login("admin", "admin123");
    String account = "onl-kick-" + suffix();
    String cookie = accountWithPrivileges(adminCookie, account, "\"online-user-view\"");
    String hash = SessionTokenHash.of(tokenOf(cookie));

    assertEquals(200, send("DELETE", "/api/v1/online-users/" + hash, null, adminCookie).statusCode());
    HttpResponse<String> afterKick = send("GET", "/api/v1/me", null, cookie);
    assertEquals(401, afterKick.statusCode(), "被强退的会话下一次请求即 401：" + afterKick.body());
    assertTrue(afterKick.body().contains("40101"), afterKick.body());
    assertEquals(200, send("DELETE", "/api/v1/online-users/" + hash, null, adminCookie).statusCode(),
        "行已消失也算成功：管理意图已达成");

    JsonNode audit = data(send("GET", "/api/v1/audit-logs?filters%5Baction%5D=online-user-kick", null, adminCookie));
    assertTrue(audit.get("total").asInt() >= 1, "强退要留审计：" + audit);
    assertEquals("admin", audit.get("items").get(0).get("account").asText());
  }

  @Test
  @DisplayName("权限：无 online-user-view 看不了列表；只有 view 的账号踢不动人（两码分开授权）")
  void privilegeGate() throws Exception {
    String adminCookie = login("admin", "admin123");
    String noView = accountWithPrivileges(adminCookie, "onl-noview-" + suffix(), "\"account-view\"");
    HttpResponse<String> listForbidden = send("GET", "/api/v1/online-users", null, noView);
    assertEquals(403, listForbidden.statusCode(), listForbidden.body());
    assertTrue(listForbidden.body().contains("40301"), listForbidden.body());

    String viewOnly = accountWithPrivileges(adminCookie, "onl-viewonly-" + suffix(), "\"online-user-view\"");
    String ownHash = SessionTokenHash.of(tokenOf(viewOnly));
    HttpResponse<String> kickForbidden = send("DELETE", "/api/v1/online-users/" + ownHash, null, viewOnly);
    assertEquals(403, kickForbidden.statusCode(), kickForbidden.body());
    assertTrue(kickForbidden.body().contains("40301"), kickForbidden.body());
    assertEquals(200, send("GET", "/api/v1/me", null, viewOnly).statusCode(), "踢不动 = 自己还活着");
  }

  /** 账号名唯一后缀（账号名限 3~30 字符且全局唯一，同 JVM 共享 H2 库）。 */
  private static String suffix() {
    return String.valueOf(System.nanoTime() % 100000000);
  }

  /** cookie 串（ZT_SESSION=…）里的 token 部分。 */
  private static String tokenOf(String cookie) {
    return cookie.substring(cookie.indexOf('=') + 1);
  }
}
