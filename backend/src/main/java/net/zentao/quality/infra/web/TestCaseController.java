package net.zentao.quality.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import net.zentao.platform.activity.ActivityQueryService;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.BatchActionRequest;
import net.zentao.platform.web.BatchActionResult;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.quality.api.BatchCreateResult;
import net.zentao.quality.api.TestCaseList;
import net.zentao.quality.api.TestCaseView;
import net.zentao.quality.app.BatchCreateTestCaseHandler;
import net.zentao.quality.app.BatchTestCaseActionHandler;
import net.zentao.quality.app.CreateTestCaseHandler;
import net.zentao.quality.app.DeleteTestCaseHandler;
import net.zentao.quality.app.ImportFromLibraryHandler;
import net.zentao.quality.app.ReviewTestCaseHandler;
import net.zentao.quality.app.TestCaseQueryService;
import net.zentao.quality.app.UpdateTestCaseHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用例端点（quality 卡 §5 TestCase 11 行：产品面 8 + 库内面 2 + 导入 1）。 */
@RestController
@RequestMapping("/api/v1")
public class TestCaseController {

  private final TestCaseQueryService queryService;
  private final CreateTestCaseHandler createHandler;
  private final UpdateTestCaseHandler updateHandler;
  private final DeleteTestCaseHandler deleteHandler;
  private final ReviewTestCaseHandler reviewHandler;
  private final BatchCreateTestCaseHandler batchCreateHandler;
  private final BatchTestCaseActionHandler batchHandler;
  private final ImportFromLibraryHandler importHandler;
  private final ActivityQueryService activityQueryService;
  private final SessionResolver resolver;

  public TestCaseController(TestCaseQueryService queryService, CreateTestCaseHandler createHandler,
      UpdateTestCaseHandler updateHandler, DeleteTestCaseHandler deleteHandler,
      ReviewTestCaseHandler reviewHandler, BatchCreateTestCaseHandler batchCreateHandler,
      BatchTestCaseActionHandler batchHandler, ImportFromLibraryHandler importHandler,
      ActivityQueryService activityQueryService, SessionResolver resolver) {
    this.queryService = queryService;
    this.createHandler = createHandler;
    this.updateHandler = updateHandler;
    this.deleteHandler = deleteHandler;
    this.reviewHandler = reviewHandler;
    this.batchCreateHandler = batchCreateHandler;
    this.batchHandler = batchHandler;
    this.importHandler = importHandler;
    this.activityQueryService = activityQueryService;
    this.resolver = resolver;
  }

  @GetMapping("/products/{productId}/test-cases")
  @Operation(operationId = "listTestCases")
  @RequirePrivilege("testcase-view")
  public DataEnvelope<TestCaseList> list(@PathVariable long productId, HttpServletRequest request) {
    return DataEnvelope.of(
        queryService.pageByProduct(productId, resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/products/{productId}/test-cases")
  @Operation(operationId = "createTestCase")
  @RequirePrivilege("testcase-create")
  public DataEnvelope<TestCaseView> create(@PathVariable long productId,
      @RequestBody CreateTestCaseHandler.TestCaseCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(createHandler.handle(resolver.resolve(request), productId, 0, body));
  }

  @PostMapping("/products/{productId}/test-cases/batch")
  @Operation(operationId = "batchCreateTestCases")
  @RequirePrivilege("testcase-create")
  public DataEnvelope<BatchCreateResult> batchCreate(@PathVariable long productId,
      @RequestBody TestCaseBatchCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(batchCreateHandler.handle(resolver.resolve(request), productId, 0, body.items()));
  }

  @PostMapping("/products/{productId}/test-cases/import-from-library")
  @Operation(operationId = "importTestCasesFromLibrary")
  @RequirePrivilege("testcase-create")
  public DataEnvelope<ImportFromLibraryHandler.TestCaseImportResult> importFromLibrary(
      @PathVariable long productId, @RequestBody ImportFromLibraryHandler.TestCaseImportRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(importHandler.handle(resolver.resolve(request), productId, body));
  }

  @GetMapping("/libraries/{libraryId}/test-cases")
  @Operation(operationId = "listLibraryCases")
  @RequirePrivilege("library-view")
  public DataEnvelope<TestCaseList> listLibraryCases(@PathVariable long libraryId, HttpServletRequest request) {
    return DataEnvelope.of(
        queryService.pageByLibrary(libraryId, resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/libraries/{libraryId}/test-cases")
  @Operation(operationId = "createLibraryCase")
  @RequirePrivilege("library-edit")
  public DataEnvelope<TestCaseView> createLibraryCase(@PathVariable long libraryId,
      @RequestBody CreateTestCaseHandler.TestCaseCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(createHandler.handle(resolver.resolve(request), 0, libraryId, body));
  }

  @GetMapping("/test-cases/{caseId}")
  @Operation(operationId = "getTestCase")
  @RequirePrivilege("testcase-view")
  public DataEnvelope<TestCaseView> detail(@PathVariable long caseId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.detail(resolver.resolve(request), caseId));
  }

  @PatchMapping("/test-cases/{caseId}")
  @Operation(operationId = "updateTestCase")
  @RequirePrivilege("testcase-edit")
  public DataEnvelope<TestCaseView> update(@PathVariable long caseId,
      @RequestBody UpdateTestCaseHandler.TestCaseUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(updateHandler.handle(resolver.resolve(request), caseId, body));
  }

  @DeleteMapping("/test-cases/{caseId}")
  @Operation(operationId = "deleteTestCase")
  @RequirePrivilege("testcase-delete")
  public DataEnvelope<Void> delete(@PathVariable long caseId, HttpServletRequest request) {
    deleteHandler.handle(resolver.resolve(request), caseId);
    return DataEnvelope.empty();
  }

  @PostMapping("/test-cases/{caseId}/review")
  @Operation(operationId = "reviewTestCase")
  @RequirePrivilege("testcase-review")
  public DataEnvelope<TestCaseView> review(@PathVariable long caseId,
      @RequestBody ReviewTestCaseHandler.TestCaseReviewRequest body, HttpServletRequest request) {
    return DataEnvelope.of(reviewHandler.handle(resolver.resolve(request), caseId, body));
  }

  @PostMapping("/test-cases/batch")
  @Operation(operationId = "batchOperateTestCases")
  public DataEnvelope<BatchActionResult> batch(@RequestBody BatchActionRequest body, HttpServletRequest request) {
    return DataEnvelope.of(batchHandler.handle(resolver.resolve(request), body));
  }

  @GetMapping("/test-cases/{caseId}/activities")
  @Operation(operationId = "listTestCaseActivities")
  @RequirePrivilege("testcase-view")
  public DataEnvelope<ActivityQueryService.ActivityList> activities(@PathVariable long caseId,
      @RequestParam(required = false) Integer limit, @RequestParam(required = false) Long beforeId,
      HttpServletRequest request) {
    return DataEnvelope.of(activityQueryService.list("testCase", caseId, null, limit, beforeId));
  }

  public record TestCaseBatchCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CreateTestCaseHandler.TestCaseCreateRequest> items) {}
}
