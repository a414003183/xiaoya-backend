package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import net.zentao.platform.monitor.ServerMetricsQueryService;
import net.zentao.platform.rbac.RequirePrivilege;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务监控（T17 P1-5）：聚合端点出这台机器的负载快照。
 * 不直暴露 actuator（只开 health/info）——聚合后的形状是本仓自己的契约，换实现（actuator/Micrometer）不动调用方。
 */
@RestController
@RequestMapping("/api/v1")
public class ServerMetricsController {

  private final ServerMetricsQueryService queryService;

  public ServerMetricsController(ServerMetricsQueryService queryService) {
    this.queryService = queryService;
  }

  @GetMapping("/monitor/server")
  @Operation(operationId = "getServerMetrics")
  @RequirePrivilege("monitor-view")
  public DataEnvelope<ServerMetricsQueryService.ServerMetricsView> server() {
    return DataEnvelope.of(queryService.snapshot());
  }
}
