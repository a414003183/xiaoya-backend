package net.zentao.quality.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.activity.ActivityQueryService;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.CommentRequest;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.quality.api.ResultView;
import net.zentao.quality.api.RunCaseList;
import net.zentao.quality.api.TestRunList;
import net.zentao.quality.api.TestRunView;
import net.zentao.quality.app.RecordResultHandler;
import net.zentao.quality.app.SuiteHandlers.SuiteLinkCasesRequest;
import net.zentao.quality.app.TestRunHandlers;
import net.zentao.quality.app.TestRunQueryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 测试单端点（quality 卡 §5 TestRun 14 行）。 */
@RestController
@RequestMapping("/api/v1")
public class TestRunController {

  private final TestRunQueryService queryService;
  private final TestRunHandlers handlers;
  private final RecordResultHandler recordHandler;
  private final ActivityQueryService activityQueryService;
  private final SessionResolver resolver;

  public TestRunController(TestRunQueryService queryService, TestRunHandlers handlers,
      RecordResultHandler recordHandler, ActivityQueryService activityQueryService, SessionResolver resolver) {
    this.queryService = queryService;
    this.handlers = handlers;
    this.recordHandler = recordHandler;
    this.activityQueryService = activityQueryService;
    this.resolver = resolver;
  }

  @GetMapping("/products/{productId}/test-runs")
  @Operation(operationId = "listTestRuns")
  @RequirePrivilege("testrun-view")
  public DataEnvelope<TestRunList> list(@PathVariable long productId, HttpServletRequest request) {
    return DataEnvelope.of(
        queryService.pageByProduct(productId, resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/products/{productId}/test-runs")
  @Operation(operationId = "createTestRun")
  @RequirePrivilege("testrun-create")
  public DataEnvelope<TestRunView> create(@PathVariable long productId,
      @RequestBody TestRunHandlers.TestRunCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.create(resolver.resolve(request), productId, body));
  }

  @GetMapping("/test-runs/{testRunId}")
  @Operation(operationId = "getTestRun")
  @RequirePrivilege("testrun-view")
  public DataEnvelope<TestRunView> detail(@PathVariable long testRunId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.detail(resolver.resolve(request), testRunId));
  }

  @PatchMapping("/test-runs/{testRunId}")
  @Operation(operationId = "updateTestRun")
  @RequirePrivilege("testrun-edit")
  public DataEnvelope<TestRunView> update(@PathVariable long testRunId,
      @RequestBody TestRunHandlers.TestRunUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.update(resolver.resolve(request), testRunId, body));
  }

  @DeleteMapping("/test-runs/{testRunId}")
  @Operation(operationId = "deleteTestRun")
  @RequirePrivilege("testrun-delete")
  public DataEnvelope<Void> delete(@PathVariable long testRunId, HttpServletRequest request) {
    handlers.delete(resolver.resolve(request), testRunId);
    return DataEnvelope.empty();
  }

  @PostMapping("/test-runs/{testRunId}/start")
  @Operation(operationId = "startTestRun")
  @RequirePrivilege("testrun-start")
  public DataEnvelope<TestRunView> start(@PathVariable long testRunId, HttpServletRequest request) {
    return DataEnvelope.of(handlers.start(resolver.resolve(request), testRunId));
  }

  @PostMapping("/test-runs/{testRunId}/block")
  @Operation(operationId = "blockTestRun")
  @RequirePrivilege("testrun-block")
  public DataEnvelope<TestRunView> block(@PathVariable long testRunId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.block(resolver.resolve(request), testRunId, body));
  }

  @PostMapping("/test-runs/{testRunId}/activate")
  @Operation(operationId = "activateTestRun")
  @RequirePrivilege("testrun-activate")
  public DataEnvelope<TestRunView> activate(@PathVariable long testRunId,
      @RequestBody(required = false) CommentRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.activate(resolver.resolve(request), testRunId, body));
  }

  @PostMapping("/test-runs/{testRunId}/close")
  @Operation(operationId = "closeTestRun")
  @RequirePrivilege("testrun-close")
  public DataEnvelope<TestRunView> close(@PathVariable long testRunId,
      @RequestBody TestRunHandlers.TestRunCloseRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.close(resolver.resolve(request), testRunId, body));
  }

  @GetMapping("/test-runs/{testRunId}/cases")
  @Operation(operationId = "listTestRunCases")
  @RequirePrivilege("testrun-view")
  public DataEnvelope<RunCaseList> runCases(@PathVariable long testRunId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.runCases(resolver.resolve(request), testRunId, request.getParameterMap()));
  }

  @PostMapping("/test-runs/{testRunId}/cases")
  @Operation(operationId = "linkTestRunCases")
  @RequirePrivilege("testrun-link-case")
  public DataEnvelope<RunCaseList> linkCases(@PathVariable long testRunId,
      @RequestBody TestRunHandlers.TestRunLinkCasesRequest body, HttpServletRequest request) {
    handlers.linkCases(resolver.resolve(request), testRunId, body);
    return DataEnvelope.of(queryService.runCases(resolver.resolve(request), testRunId, request.getParameterMap()));
  }

  @PostMapping("/test-runs/{testRunId}/unlink-cases")
  @Operation(operationId = "unlinkTestRunCases")
  @RequirePrivilege("testrun-link-case")
  public DataEnvelope<RunCaseList> unlinkCases(@PathVariable long testRunId,
      @RequestBody SuiteLinkCasesRequest body, HttpServletRequest request) {
    handlers.unlinkCases(resolver.resolve(request), testRunId, body.caseIds());
    return DataEnvelope.of(queryService.runCases(resolver.resolve(request), testRunId, request.getParameterMap()));
  }

  @PostMapping("/test-runs/{testRunId}/cases/{caseId}/result")
  @Operation(operationId = "recordTestRunResult")
  @RequirePrivilege("testrun-record-result")
  public DataEnvelope<ResultView> recordResult(@PathVariable long testRunId, @PathVariable long caseId,
      @RequestBody RecordResultHandler.RecordResultRequest body, HttpServletRequest request) {
    return DataEnvelope.of(recordHandler.handle(resolver.resolve(request), testRunId, caseId, body));
  }

  @PostMapping("/test-runs/{testRunId}/cases/{caseId}/assign")
  @Operation(operationId = "assignTestRunCase")
  @RequirePrivilege("testrun-assign-case")
  public DataEnvelope<ResultView> assignCase(@PathVariable long testRunId, @PathVariable long caseId,
      @RequestBody TestRunHandlers.AssignRunCaseRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.assignCase(resolver.resolve(request), testRunId, caseId, body));
  }

  @GetMapping("/test-runs/{testRunId}/activities")
  @Operation(operationId = "listTestRunActivities")
  @RequirePrivilege("testrun-view")
  public DataEnvelope<ActivityQueryService.ActivityList> activities(@PathVariable long testRunId,
      @RequestParam(required = false) Integer limit, @RequestParam(required = false) Long beforeId,
      HttpServletRequest request) {
    return DataEnvelope.of(activityQueryService.list("testRun", testRunId, null, limit, beforeId));
  }
}
