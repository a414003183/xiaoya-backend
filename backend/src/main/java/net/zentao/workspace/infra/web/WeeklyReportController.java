package net.zentao.workspace.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.workspace.api.WeeklyReportList;
import net.zentao.workspace.api.WeeklyReportView;
import net.zentao.workspace.app.WeeklyReportQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 项目周报端点（workspace 卡 §5 weekly-reports 两行）。 */
@RestController
@RequestMapping("/api/v1")
public class WeeklyReportController {

  private final WeeklyReportQueryService queryService;
  private final SessionResolver resolver;

  public WeeklyReportController(WeeklyReportQueryService queryService, SessionResolver resolver) {
    this.queryService = queryService;
    this.resolver = resolver;
  }

  @GetMapping("/projects/{projectId}/weekly-reports")
  @Operation(operationId = "listWeeklyReports")
  @RequirePrivilege("weekly-report-view")
  public DataEnvelope<WeeklyReportList> history(@PathVariable long projectId, HttpServletRequest request) {
    return DataEnvelope.of(
        queryService.history(resolver.resolve(request), projectId, request.getParameterMap()));
  }

  @GetMapping("/projects/{projectId}/weekly-reports/current")
  @Operation(operationId = "getCurrentWeeklyReport")
  @RequirePrivilege("weekly-report-view")
  public DataEnvelope<WeeklyReportView> current(@PathVariable long projectId,
      @RequestParam(required = false) LocalDate date, HttpServletRequest request) {
    return DataEnvelope.of(queryService.current(resolver.resolve(request), projectId, date));
  }
}
