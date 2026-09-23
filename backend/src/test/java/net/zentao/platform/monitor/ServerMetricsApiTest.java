package net.zentao.platform.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 服务监控（T17 P1-5）：聚合端点形状 + 权限门。
 *
 * <p>数值来自运行环境，故只断言「形状与哨兵语义」：字节数非负或 -1、cpuLoad ∈ [-1,1]、采样时间在。
 * 真机上页面看到的百分比由前端从这些原始字节数算，别在这里对着 42% 断言。
 */
class ServerMetricsApiTest extends ApiTestSupport {

  @Test
  @DisplayName("admin 拿到快照：核数 ≥1、cpuLoad ∈ [-1,1]、内存/磁盘/堆字段与采样时间齐备")
  void snapshotShape() throws Exception {
    String cookie = login("admin", "admin123");
    JsonNode metrics = data(send("GET", "/api/v1/monitor/server", null, cookie));

    assertTrue(metrics.get("cpuCores").asInt() >= 1, metrics.toString());
    double cpuLoad = metrics.get("cpuLoad").asDouble();
    assertTrue(cpuLoad >= -1 && cpuLoad <= 1, "cpuLoad 是 0~1 的比例或 -1（不可用）：" + cpuLoad);
    // 内存与磁盘：要么是正数，要么是 -1（探测失败）；不允许 0 或负数混进来当「未知」
    for (String field : new String[] {"memoryTotalBytes", "memoryUsedBytes", "diskTotalBytes", "diskUsedBytes",
        "jvmHeapUsedBytes", "jvmHeapMaxBytes"}) {
      long value = metrics.get(field).asLong();
      assertTrue(value == -1 || value > 0, field + " 应为正数或 -1：" + value);
    }
    assertTrue(metrics.get("jvmHeapUsedBytes").asLong() <= metrics.get("jvmHeapMaxBytes").asLong(),
        "堆已用不该超过堆上限：" + metrics);
    assertTrue(metrics.get("uptimeSeconds").asLong() >= 0, metrics.toString());
    assertTrue(metrics.get("diskPath").asText().length() > 0, metrics.toString());
    assertTrue(metrics.get("sampledAt").asText().contains("T"), metrics.toString());
  }

  @Test
  @DisplayName("权限：无 monitor-view 的账号 40301（未登录 40101 由同一拦截器判定）")
  void privilegeGate() throws Exception {
    String adminCookie = login("admin", "admin123");
    String cookie = accountWithPrivileges(adminCookie, "t17-nopriv-" + System.nanoTime() % 100000000, "\"account-view\"");

    HttpResponse<String> forbidden = send("GET", "/api/v1/monitor/server", null, cookie);
    assertEquals(403, forbidden.statusCode(), forbidden.body());
    assertTrue(forbidden.body().contains("40301"), forbidden.body());
  }
}
