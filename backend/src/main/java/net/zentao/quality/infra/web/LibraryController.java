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

/** 用例库端点（quality 卡 §5 Library 4 行；type=library、productId=0 面，读全员写需权限码）。 */
@RestController
@RequestMapping("/api/v1")
public class LibraryController {

  private final SuiteQueryService queryService;
  private final SuiteHandlers handlers;
  private final SessionResolver resolver;

  public LibraryController(SuiteQueryService queryService, SuiteHandlers handlers, SessionResolver resolver) {
    this.queryService = queryService;
    this.handlers = handlers;
    this.resolver = resolver;
  }

  @GetMapping("/libraries")
  @Operation(operationId = "listLibraries")
  @RequirePrivilege("library-view")
  public DataEnvelope<SuiteList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.pageLibraries(resolver.resolve(request), request.getParameterMap()));
  }

  @PostMapping("/libraries")
  @Operation(operationId = "createLibrary")
  @RequirePrivilege("library-create")
  public DataEnvelope<SuiteView> create(@RequestBody SuiteHandlers.LibraryCreateRequest body,
      HttpServletRequest request) {
    return DataEnvelope.of(handlers.createLibrary(resolver.resolve(request), body));
  }

  @GetMapping("/libraries/{libraryId}")
  @Operation(operationId = "getLibrary")
  @RequirePrivilege("library-view")
  public DataEnvelope<SuiteView> detail(@PathVariable long libraryId, HttpServletRequest request) {
    return DataEnvelope.of(queryService.detailLibrary(resolver.resolve(request), libraryId));
  }

  @PatchMapping("/libraries/{libraryId}")
  @Operation(operationId = "updateLibrary")
  @RequirePrivilege("library-edit")
  public DataEnvelope<SuiteView> update(@PathVariable long libraryId,
      @RequestBody SuiteHandlers.LibraryUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.updateLibrary(resolver.resolve(request), libraryId, body));
  }

  @DeleteMapping("/libraries/{libraryId}")
  @Operation(operationId = "deleteLibrary")
  @RequirePrivilege("library-delete")
  public DataEnvelope<Void> delete(@PathVariable long libraryId, HttpServletRequest request) {
    handlers.deleteLibrary(resolver.resolve(request), libraryId);
    return DataEnvelope.empty();
  }
}
