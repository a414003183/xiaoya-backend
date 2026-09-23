package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.audit.AuditLogQueryService;
import net.zentao.platform.audit.AuditQueryStatQueryService;
import net.zentao.platform.rbac.RequirePrivilege;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志四端点（B1 §H3 读侧；platform 卡列表 DSL 见 03 §3）：分页列表、单行详情、哈希链校验、
 * 查询聚合（T04）。
 *
 * <p>全部只读——行由写端点的审计横切（{@code AuditRecorder}）与查询埋点（{@code QueryStatFilter}）
 * 追加，此处无写入口。登录 40101 与缺码 40301 同兄弟端点，由 @RequirePrivilege 拦截器统一判定；
 * 校验/聚合与日志列表共用同一权限码（审计页的三个视图是一件事）。
 */
@RestController
@RequestMapping("/api/v1")
public class AuditLogController {

  private final AuditLogQueryService queryService;
  private final AuditQueryStatQueryService statQueryService;

  public AuditLogController(AuditLogQueryService queryService, AuditQueryStatQueryService statQueryService) {
    this.queryService = queryService;
    this.statQueryService = statQueryService;
  }

  @GetMapping("/audit-logs")
  @Operation(operationId = "listAuditLogs")
  @RequirePrivilege("audit-log-view")
  public DataEnvelope<AuditLogQueryService.AuditLogList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(request.getParameterMap()));
  }

  /** 详情带 changes/snapshot/extra；列表不带（体积）。路径变量是纯数字，非数字由服务层给 40001。 */
  @GetMapping("/audit-logs/{auditId}")
  @Operation(operationId = "getAuditLog")
  @RequirePrivilege("audit-log-view")
  public DataEnvelope<AuditLogQueryService.AuditLogDetail> detail(@PathVariable String auditId) {
    return DataEnvelope.of(queryService.detail(auditId));
  }

  /** 哈希链校验（防篡改工具；ADR-004 决策 4）。 */
  @GetMapping("/audit-logs/verify")
  @Operation(operationId = "verifyAuditLogs")
  @RequirePrivilege("audit-log-view")
  public DataEnvelope<AuditLogQueryService.AuditVerifyResult> verify(
      @RequestParam(required = false) String fromId,
      @RequestParam(required = false) String toId,
      @RequestParam(required = false) String maxRows) {
    return DataEnvelope.of(queryService.verify(fromId, toId, maxRows));
  }

  /** 查询聚合（query 类不进主表，见 ADR-004 决策 3）。 */
  @GetMapping("/audit-logs/query-stats")
  @Operation(operationId = "listAuditQueryStats")
  @RequirePrivilege("audit-log-view")
  public DataEnvelope<AuditQueryStatQueryService.AuditQueryStatList> queryStats(HttpServletRequest request) {
    return DataEnvelope.of(statQueryService.page(request.getParameterMap()));
  }
}
