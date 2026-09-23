package net.zentao.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * B1 §H2：actuator 只开探活面——health 匿名可读（探针），其余一律先要会话；
 * 是否「存在」再由 exposure.include 决定（登录后访问未暴露端点才是 404）。
 */
class ActuatorHealthTest extends ApiTestSupport {

  @Test
  @DisplayName("匿名 /actuator/health 返回 UP（探针免会话）")
  void healthIsAnonymous() throws Exception {
    HttpResponse<String> response = send("GET", "/actuator/health", null, null);
    assertEquals(200, response.statusCode(), response.body());
    assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
  }

  @Test
  @DisplayName("匿名访问非探活端点一律 401（SurfaceGuardFilter 先于暴露面判定，未暴露的也不给探测）")
  void anonymousNonHealthIsUnauthorized() throws Exception {
    assertEquals(401, send("GET", "/actuator/info", null, null).statusCode());
    assertEquals(401, send("GET", "/actuator/env", null, null).statusCode());
    assertEquals(401, send("GET", "/actuator", null, null).statusCode());
  }

  @Test
  @DisplayName("已登录：info 可读（200），未暴露的 env 仍是 404")
  void loggedInRespectsExposure() throws Exception {
    String cookie = login("admin", "admin123");
    assertEquals(200, send("GET", "/actuator/info", null, cookie).statusCode());
    assertEquals(404, send("GET", "/actuator/env", null, cookie).statusCode());
  }
}
