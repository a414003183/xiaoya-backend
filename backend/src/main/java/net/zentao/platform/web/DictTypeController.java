package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditDiff;
import net.zentao.platform.meta.DictQueryService;
import net.zentao.platform.meta.SaveDictHandler;
import net.zentao.platform.rbac.RequirePrivilege;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典类型管理（T16 P1-4）：DB 字典只**扩展**代码注册的内置字典（撞名的 code 创建时即 422）。
 * 权限码复用 `setting-manage`（同属系统配置面）；删类型级联删数据项。
 */
@RestController
@RequestMapping("/api/v1")
public class DictTypeController {

  private final DictQueryService queryService;
  private final SaveDictHandler saveHandler;

  public DictTypeController(DictQueryService queryService, SaveDictHandler saveHandler) {
    this.queryService = queryService;
    this.saveHandler = saveHandler;
  }

  @GetMapping("/dict-types")
  @Operation(operationId = "listDictTypes")
  @RequirePrivilege("setting-manage")
  public DataEnvelope<DictQueryService.DictTypeList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.pageTypes(request.getParameterMap()));
  }

  @PostMapping("/dict-types")
  @Operation(operationId = "createDictType")
  @RequirePrivilege("setting-manage")
  @Audit(action = "dict-type-create", objectType = "dictType")
  public DataEnvelope<DictQueryService.DictTypeView> create(
      @RequestBody SaveDictHandler.DictTypeRequest body) {
    return DataEnvelope.of(saveHandler.createType(body));
  }

  @PatchMapping("/dict-types/{code}")
  @Operation(operationId = "updateDictType")
  @RequirePrivilege("setting-manage")
  @Audit(action = "dict-type-update", objectType = "dictType")
  @AuditDiff(objectType = "dictType", idParam = "code")
  public DataEnvelope<DictQueryService.DictTypeView> update(@PathVariable String code,
      @RequestBody SaveDictHandler.DictTypeUpdateRequest body) {
    return DataEnvelope.of(saveHandler.updateType(code, body));
  }

  @DeleteMapping("/dict-types/{code}")
  @Operation(operationId = "deleteDictType")
  @RequirePrivilege("setting-manage")
  @Audit(action = "dict-type-delete", objectType = "dictType")
  @AuditDiff(objectType = "dictType", idParam = "code")
  public DataEnvelope<Void> delete(@PathVariable String code) {
    saveHandler.deleteType(code);
    return DataEnvelope.empty();
  }
}
