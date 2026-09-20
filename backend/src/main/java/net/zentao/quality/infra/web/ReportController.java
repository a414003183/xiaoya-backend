package net.zentao.quality.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.quality.api.ReportList;
import net.zentao.quality.api.ReportView;
import net.zentao.quality.app.ReportHandlers;
import net.zentao.quality.app.ReportQueryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 测试报告端点（quality 卡 §5 Report 4 行）。executionId/projectId/productId 不可改 → 40001（§8）。 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

  private final ReportQueryService queryService;
  private final ReportHandlers handlers;
  private final SessionResolver resolver;

  public ReportController(ReportQueryService queryService, ReportHandlers handlers, SessionResolver resolver) {
    this.queryService = queryService;
    this.handlers = handlers;
    this.resolver = resolver;
  }

  @GetMapping("/executions/{executionId}/reports")
  @Operation(operationId = "listExecutionReports")
  @RequirePrivilege("report-view")
  public DataEnvelope<ReportList> list(@PathVariable long executionId, HttpServletRequest request) {
    return DataEnvelope.of(
        queryService.pageByExecution(resolver.resolve(request), executionId, request.getParameterMap()));
  }

  @PostMapping("/executions/{executionId}/reports")
  @Operation(operationId = "createExecutionReport")
  @RequirePrivilege("report-create")
  public DataEnvelope<ReportView> create(@PathVariable long executionId,
      @RequestBody ReportHandlers.ReportCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.create(resolver.resolve(request), executionId, body));
  }

  @GetMapping("/reports/{reportId}")
  @Operation(operationId = "getReport")
  @RequirePrivilege("report-view")
  public DataEnvelope<ReportView> detail(@PathVariable long reportId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.detail(resolver.resolve(request), reportId));
  }

  @PatchMapping("/reports/{reportId}")
  @Operation(operationId = "updateReport")
  @RequirePrivilege("report-edit")
  public DataEnvelope<ReportView> update(@PathVariable long reportId,
      @RequestBody ReportHandlers.ReportUpdateRequest body, HttpServletRequest request) {
    if (body.touchesImmutable()) {
      throw ApiException.badRequest("executionId/projectId/productId 创建后不可改。");
    }
    return DataEnvelope.of(handlers.update(resolver.resolve(request), reportId, body));
  }

  @DeleteMapping("/reports/{reportId}")
  @Operation(operationId = "deleteReport")
  @RequirePrivilege("report-delete")
  public DataEnvelope<Void> delete(@PathVariable long reportId, HttpServletRequest request) {
    handlers.delete(resolver.resolve(request), reportId);
    return DataEnvelope.empty();
  }
}
