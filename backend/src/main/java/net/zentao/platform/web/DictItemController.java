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
 * 字典数据项管理（T16 P1-4）：列表挂在类型下（`/dict-types/{code}/items`，作用域恒为路径里的类型），
 * 单条改动按 id 直寻（`/dict-items/{id}`）——数据项可能被换类型，但 id 是稳的。
 */
@RestController
@RequestMapping("/api/v1")
public class DictItemController {

  private final DictQueryService queryService;
  private final SaveDictHandler saveHandler;

  public DictItemController(DictQueryService queryService, SaveDictHandler saveHandler) {
    this.queryService = queryService;
    this.saveHandler = saveHandler;
  }

  @GetMapping("/dict-types/{code}/items")
  @Operation(operationId = "listDictItems")
  @RequirePrivilege("setting-manage")
  public DataEnvelope<DictQueryService.DictDataList> list(@PathVariable String code, HttpServletRequest request) {
    return DataEnvelope.of(queryService.pageData(code, request.getParameterMap()));
  }

  @PostMapping("/dict-types/{code}/items")
  @Operation(operationId = "createDictItem")
  @RequirePrivilege("setting-manage")
  @Audit(action = "dict-item-create", objectType = "dictItem")
  public DataEnvelope<DictQueryService.DictDataView> create(@PathVariable String code,
      @RequestBody SaveDictHandler.DictDataRequest body) {
    return DataEnvelope.of(saveHandler.createData(code, body));
  }

  @PatchMapping("/dict-items/{id}")
  @Operation(operationId = "updateDictItem")
  @RequirePrivilege("setting-manage")
  @Audit(action = "dict-item-update", objectType = "dictItem")
  @AuditDiff(objectType = "dictItem")
  public DataEnvelope<DictQueryService.DictDataView> update(@PathVariable long id,
      @RequestBody SaveDictHandler.DictDataUpdateRequest body) {
    return DataEnvelope.of(saveHandler.updateData(id, body));
  }

  @DeleteMapping("/dict-items/{id}")
  @Operation(operationId = "deleteDictItem")
  @RequirePrivilege("setting-manage")
  @Audit(action = "dict-item-delete", objectType = "dictItem")
  @AuditDiff(objectType = "dictItem")
  public DataEnvelope<Void> delete(@PathVariable long id) {
    saveHandler.deleteData(id);
    return DataEnvelope.empty();
  }
}
