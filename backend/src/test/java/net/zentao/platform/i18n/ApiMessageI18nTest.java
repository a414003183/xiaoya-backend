package net.zentao.platform.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 后端错误文案按请求语言返回（T06 / 用户事项 5）：Accept-Language 优先、`?lang=` 可单请求覆盖，
 * `code` 与 HTTP 状态不随语言变（前端按码本地化的既有链路零改动）。
 */
class ApiMessageI18nTest extends ApiTestSupport {

  private HttpResponse<String> request(String method, String path, String body, String cookie, String acceptLanguage)
      throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Content-Type", "application/json");
    if (cookie != null) {
      builder.header("Cookie", cookie);
    }
    if (acceptLanguage != null) {
      builder.header("Accept-Language", acceptLanguage);
    }
    builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String messageOf(HttpResponse<String> response) throws Exception {
    return json.readTree(response.body()).at("/error/message").asText();
  }

  @Test
  @DisplayName("登录失败：Accept-Language: en 回英文，不带头回中文；code 恒为 40101")
  void loginFailureFollowsAcceptLanguage() throws Exception {
    String body = "{\"account\":\"nobody-i18n\",\"password\":\"wrong\"}";

    HttpResponse<String> zh = request("POST", "/api/v1/session", body, null, null);
    assertEquals(401, zh.statusCode(), zh.body());
    assertEquals("账号或密码错误。", messageOf(zh));
    assertEquals(40101, json.readTree(zh.body()).at("/error/code").asInt());

    HttpResponse<String> en = request("POST", "/api/v1/session", body, null, "en-US,en;q=0.9");
    assertEquals(401, en.statusCode(), en.body());
    assertEquals("Incorrect account or password.", messageOf(en));
    assertEquals(40101, json.readTree(en.body()).at("/error/code").asInt());
  }

  @Test
  @DisplayName("?lang= 覆盖 Accept-Language（单请求开关）")
  void queryParamOverridesHeader() throws Exception {
    String body = "{\"account\":\"nobody-i18n\",\"password\":\"wrong\"}";

    HttpResponse<String> forcedZh = request("POST", "/api/v1/session?lang=zh-CN", body, null, "en");
    assertEquals("账号或密码错误。", messageOf(forcedZh));

    HttpResponse<String> forcedEn = request("POST", "/api/v1/session?lang=en", body, null, null);
    assertEquals("Incorrect account or password.", messageOf(forcedEn));
  }

  @Test
  @DisplayName("notFound 模板随语言（{0} 占位）：账号不存在 → Account not found.")
  void notFoundTemplateFollowsLanguage() throws Exception {
    String cookie = login("admin", "admin123");

    HttpResponse<String> zh = request("GET", "/api/v1/accounts/99999999", null, cookie, null);
    assertEquals(404, zh.statusCode(), zh.body());
    assertEquals("账号不存在。", messageOf(zh));

    HttpResponse<String> en = request("GET", "/api/v1/accounts/99999999", null, cookie, "en");
    assertEquals("Account not found.", messageOf(en));
    assertTrue(
        json.readTree(en.body()).at("/error/code").asInt() == json.readTree(zh.body()).at("/error/code").asInt(),
        "code 不随语言变");
  }
}
