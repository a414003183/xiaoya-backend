package net.zentao.quality.infra.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.web.DataEnvelope;
import net.zentao.quality.api.SuiteList;
import net.zentao.quality.api.SuiteView;
import net.zentao.quality.app.SuiteHandlers;
import net.zentao.quality.app.SuiteQueryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 套件端点（quality 卡 §5 Suite 6 行；type≠library 面）。 */
@RestController
@RequestMapping("/api/v1")
public class SuiteController {

  private final SuiteQueryService queryService;
  private final SuiteHandlers handlers;
  private final SessionResolver resolver;

  public SuiteController(SuiteQueryService queryService, SuiteHandlers handlers, SessionResolver resolver) {
    this.queryService = queryService;
    this.handlers = handlers;
    this.resolver = resolver;
  }

  @GetMapping("/products/{productId}/suites")
  @Operation(operationId = "listSuites")
  @RequirePrivilege("suite-view")
  public DataEnvelope<SuiteList> list(@PathVariable long productId, HttpServletRequest request) {
    return DataEnvelope.of(
        queryService.pageByProduct(productId, resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/products/{productId}/suites")
  @Operation(operationId = "createSuite")
  @RequirePrivilege("suite-create")
  public DataEnvelope<SuiteView> create(@PathVariable long productId,
      @RequestBody SuiteHandlers.SuiteCreateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.createSuite(resolver.resolve(request), productId, body));
  }

  @GetMapping("/suites/{suiteId}")
  @Operation(operationId = "getSuite")
  @RequirePrivilege("suite-view")
  public DataEnvelope<SuiteView> detail(@PathVariable long suiteId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.detailSuite(resolver.resolve(request), suiteId));
  }

  @PatchMapping("/suites/{suiteId}")
  @Operation(operationId = "updateSuite")
  @RequirePrivilege("suite-edit")
  public DataEnvelope<SuiteView> update(@PathVariable long suiteId,
      @RequestBody SuiteHandlers.SuiteUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.updateSuite(resolver.resolve(request), suiteId, body));
  }

  @DeleteMapping("/suites/{suiteId}")
  @Operation(operationId = "deleteSuite")
  @RequirePrivilege("suite-delete")
  public DataEnvelope<Void> delete(@PathVariable long suiteId, HttpServletRequest request) {
    handlers.deleteSuite(resolver.resolve(request), suiteId);
    return DataEnvelope.empty();
  }

  @PostMapping("/suites/{suiteId}/link-cases")
  @Operation(operationId = "linkSuiteCases")
  @RequirePrivilege("suite-link-case")
  public DataEnvelope<SuiteView> linkCases(@PathVariable long suiteId,
      @RequestBody SuiteHandlers.SuiteLinkCasesRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.linkCases(resolver.resolve(request), suiteId, body));
  }

  @PostMapping("/suites/{suiteId}/unlink-cases")
  @Operation(operationId = "unlinkSuiteCases")
  @RequirePrivilege("suite-link-case")
  public DataEnvelope<SuiteView> unlinkCases(@PathVariable long suiteId,
      @RequestBody SuiteHandlers.SuiteLinkCasesRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.unlinkCases(resolver.resolve(request), suiteId, body));
  }
}
