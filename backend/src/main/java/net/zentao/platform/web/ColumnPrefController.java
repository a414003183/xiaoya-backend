package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.columnpref.ColumnPrefHandlers;
import net.zentao.platform.columnpref.ColumnPrefQueryService;
import net.zentao.platform.columnpref.ColumnPrefView;
import net.zentao.platform.session.SessionResolver;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET/PUT/DELETE /column-prefs/{resource}（platform 卡「列设置」）：列表页列设置的服务端持久化。
 * 个人级偏好——三维端点恒按会话账号过滤（只读/只写本人行），故无权限码（白名单登记）。
 */
@RestController
@RequestMapping("/api/v1")
public class ColumnPrefController {

  private final ColumnPrefQueryService queryService;
  private final ColumnPrefHandlers handlers;
  private final SessionResolver resolver;

  public ColumnPrefController(
      ColumnPrefQueryService queryService, ColumnPrefHandlers handlers, SessionResolver resolver) {
    this.queryService = queryService;
    this.handlers = handlers;
    this.resolver = resolver;
  }

  @GetMapping("/column-prefs/{resource}")
  @Operation(operationId = "getColumnPref")
  public DataEnvelope<ColumnPrefView> getColumnPref(@PathVariable String resource, HttpServletRequest request) {
    return DataEnvelope.of(queryService.get(resolver.resolve(request), resource));
  }

  @PutMapping("/column-prefs/{resource}")
  @Operation(operationId = "saveColumnPref")
  public DataEnvelope<ColumnPrefView> saveColumnPref(@PathVariable String resource,
      @RequestBody ColumnPrefHandlers.ColumnPrefUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(handlers.save(resolver.resolve(request), resource, body.columns()));
  }

  @DeleteMapping("/column-prefs/{resource}")
  @Operation(operationId = "resetColumnPref")
  public DataEnvelope<Void> resetColumnPref(@PathVariable String resource, HttpServletRequest request) {
    handlers.reset(resolver.resolve(request), resource);
    return DataEnvelope.empty();
  }
}
