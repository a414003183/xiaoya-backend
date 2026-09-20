package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.audit.AuditLogQueryService;
import net.zentao.platform.rbac.RequirePrivilege;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志单端点（B1 §H3 读侧；platform 卡列表 DSL 见 03 §3）：审计流水分页列表。
 * 只读——行由写端点的审计横切（{@code AuditRecorder}）追加，此处无写入口；
 * 登录 40101 与缺码 40301 同兄弟端点，由 @RequirePrivilege 拦截器统一判定。
 */
@RestController
@RequestMapping("/api/v1")
public class AuditLogController {

  private final AuditLogQueryService queryService;

  public AuditLogController(AuditLogQueryService queryService) {
    this.queryService = queryService;
  }

  @GetMapping("/audit-logs")
  @Operation(operationId = "listAuditLogs")
  @RequirePrivilege("audit-log-view")
  public DataEnvelope<AuditLogQueryService.AuditLogList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(request.getParameterMap()));
  }
}
