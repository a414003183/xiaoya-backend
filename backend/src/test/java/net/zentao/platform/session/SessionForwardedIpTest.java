package net.zentao.platform.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.sql.DataSource;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 代理部署下审计/登录日志记真实客户端 IP（T51 · SEC-10 部分）：把 RemoteIpValve 的两个头配置上
 * （= 生产里设 `ZENTAO_FORWARDED_IP_HEADER` / `ZENTAO_FORWARDED_PROTO_HEADER`），Tomcat 即按
 * `X-Forwarded-For` 记客户端 IP——且只信来自内网/代理段的转发头（测试客户端走回环故被信）。
 *
 * <p>与 {@code SessionSecurityTest#forwardedHeaderIgnoredByDefault} 是一对：默认关（防伪造）与显式开
 * （代理场景）各有断言。开关的**唯一判据是这两个属性非空**（实测 `server.forward-headers-strategy`
 * 管不了 Tomcat 的阀，故本卡不引入那个装饰性属性）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "server.tomcat.remoteip.remote-ip-header=x-forwarded-for",
    "server.tomcat.remoteip.protocol-header=x-forwarded-proto"})
class SessionForwardedIpTest extends ApiTestSupport {

  @Autowired
  DataSource dataSource;

  @Test
  @DisplayName("strategy=native：X-Forwarded-For 进库为客户端真实 IP")
  void forwardedHeaderHonoredWhenEnabled() throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/session"))
        .header("Content-Type", "application/json")
        .header("X-Requested-With", "fetch")
        .header("X-Forwarded-For", "203.0.113.7");
    HttpResponse<String> login = http.send(
        builder.POST(HttpRequest.BodyPublishers.ofString("{\"account\":\"admin\",\"password\":\"admin123\"}")).build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, login.statusCode(), login.body());

    String token = login.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
    token = token.substring(token.indexOf('=') + 1);

    try (var connection = dataSource.getConnection();
        var query = connection.prepareStatement("SELECT ip FROM session WHERE id = ?")) {
      query.setString(1, SessionTokenHash.of(token));
      var resultSet = query.executeQuery();
      resultSet.next();
      assertEquals("203.0.113.7", resultSet.getString(1), "开启后记的是转发头里的客户端 IP");
    }
  }
}
