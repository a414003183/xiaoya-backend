package net.zentao.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T14：运行时接口文档面的访问守卫（SurfaceGuardFilter）。
 *
 * <p>这条链是「未登录 40101 → 有会话无码 40301 → 有码放行」，与 /api/** 的 PrivilegeInterceptor 同口径；
 * 覆盖两条路：springdoc 的 JSON（/v3/api-docs，含 swagger-config）与 UI（/swagger-ui 静态资源）。
 * 它们曾被匿名公开——全量端点清单 + 可试调界面，等于把 API 面白送。
 */
class ApiDocGuardTest extends ApiTestSupport {

  @Test
  @DisplayName("匿名：/v3/api-docs 与 /swagger-ui/index.html 一律 40101（不再公开）")
  void anonymousIsUnauthorized() throws Exception {
    HttpResponse<String> apiDocs = send("GET", "/v3/api-docs", null, null);
    assertEquals(401, apiDocs.statusCode(), apiDocs.body());
    assertTrue(apiDocs.body().contains("40101"), apiDocs.body());

    HttpResponse<String> swaggerUi = send("GET", "/swagger-ui/index.html", null, null);
    assertEquals(401, swaggerUi.statusCode(), swaggerUi.body());
    assertTrue(swaggerUi.body().contains("40101"), swaggerUi.body());
  }

  @Test
  @DisplayName("有会话无 api-doc-view：40301（两条路都拦）")
  void withoutPrivilegeIsForbidden() throws Exception {
    String adminCookie = login("admin", "admin123");
    String cookie = accountWithPrivileges(adminCookie, "doc-nopriv-" + System.nanoTime() % 100000000, "\"file-upload\"");

    HttpResponse<String> apiDocs = send("GET", "/v3/api-docs", null, cookie);
    assertEquals(403, apiDocs.statusCode(), apiDocs.body());
    assertTrue(apiDocs.body().contains("40301"), apiDocs.body());

    HttpResponse<String> swaggerUi = send("GET", "/swagger-ui/index.html", null, cookie);
    assertEquals(403, swaggerUi.statusCode(), swaggerUi.body());
    assertTrue(swaggerUi.body().contains("40301"), swaggerUi.body());
  }

  @Test
  @DisplayName("持 api-doc-view：JSON 200 且头信息/服务地址与契约同源，UI 200")
  void withPrivilegeSeesDocs() throws Exception {
    String adminCookie = login("admin", "admin123");
    String cookie = accountWithPrivileges(adminCookie, "doc-viewer-" + System.nanoTime() % 100000000, "\"api-doc-view\"");

    HttpResponse<String> apiDocs = send("GET", "/v3/api-docs", null, cookie);
    assertEquals(200, apiDocs.statusCode(), apiDocs.body());
    // T14：换掉 springdoc 缺省的 "OpenAPI definition"/"v0"，且 servers 指到 /api/v1（try-it-out 才打得中）
    assertTrue(apiDocs.body().contains("\"title\":\"Zentao API\""), apiDocs.body());
    assertTrue(apiDocs.body().contains("\"url\":\"/api/v1\""), apiDocs.body());

    assertEquals(200, send("GET", "/swagger-ui/index.html", null, cookie).statusCode());
  }

  @Test
  @DisplayName("分组：/v3/api-docs/{域} 按包分域可见（org 组仍是契约里的同一批端点）")
  void groupedByDomain() throws Exception {
    String cookie = login("admin", "admin123");
    HttpResponse<String> org = send("GET", "/v3/api-docs/org", null, cookie);
    assertEquals(200, org.statusCode(), org.body());
    assertTrue(org.body().contains("\"/api/v1/accounts\""), "org 组含账号端点：" + org.body().substring(0, 200));
    assertEquals(200, send("GET", "/v3/api-docs/quality", null, cookie).statusCode());
  }
}
