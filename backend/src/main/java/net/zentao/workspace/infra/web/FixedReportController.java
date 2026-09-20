package net.zentao.workspace.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.workspace.api.BugDistributionReport;
import net.zentao.workspace.api.BurnReport;
import net.zentao.workspace.api.CasePassRateReport;
import net.zentao.workspace.api.StorySummaryReport;
import net.zentao.workspace.app.BurnQueryService;
import net.zentao.workspace.app.FixedReportQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 四个固定报表端点（workspace 卡 §5 reports 族）。 */
@RestController
@RequestMapping("/api/v1")
public class FixedReportController {

  private final FixedReportQueryService reportQueryService;
  private final BurnQueryService burnQueryService;
  private final SessionResolver resolver;

  public FixedReportController(FixedReportQueryService reportQueryService, BurnQueryService burnQueryService,
      SessionResolver resolver) {
    this.reportQueryService = reportQueryService;
    this.burnQueryService = burnQueryService;
    this.resolver = resolver;
  }

  @GetMapping("/products/{productId}/reports/story-summary")
  @Operation(operationId = "getStorySummaryReport")
  @RequirePrivilege("report-view")
  public DataEnvelope<StorySummaryReport> storySummary(@PathVariable long productId, HttpServletRequest request) {
    return DataEnvelope.of(reportQueryService.storySummary(resolver.resolve(request), productId));
  }

  @GetMapping("/products/{productId}/reports/bug-distribution")
  @Operation(operationId = "getBugDistributionReport")
  @RequirePrivilege("report-view")
  public DataEnvelope<BugDistributionReport> bugDistribution(@PathVariable long productId,
      HttpServletRequest request) {
    return DataEnvelope.of(reportQueryService.bugDistribution(resolver.resolve(request), productId));
  }

  @GetMapping("/executions/{executionId}/reports/burn")
  @Operation(operationId = "getBurnReport")
  @RequirePrivilege("report-view")
  public DataEnvelope<BurnReport> burn(@PathVariable long executionId, HttpServletRequest request) {
    return DataEnvelope.of(burnQueryService.report(resolver.resolve(request), executionId));
  }

  @GetMapping("/test-runs/{testRunId}/reports/case-pass-rate")
  @Operation(operationId = "getCasePassRateReport")
  @RequirePrivilege("report-view")
  public DataEnvelope<CasePassRateReport> casePassRate(@PathVariable long testRunId, HttpServletRequest request) {
    return DataEnvelope.of(reportQueryService.casePassRate(resolver.resolve(request), testRunId));
  }
}
