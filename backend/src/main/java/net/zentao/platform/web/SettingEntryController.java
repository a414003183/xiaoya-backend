package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditDiff;
import net.zentao.platform.meta.SaveSettingEntryHandler;
import net.zentao.platform.meta.SettingEntryQueryService;
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
 * 参数管理（T15 P1-3）：`setting` 表系统行的增删改查。
 *
 * <p>为什么不挂在 /settings 上：`GET /settings?keys=`（键值读取）与 `PUT /settings`（批量写）已被设置页占用，
 * 同路径同方法容不下「分页列表」这第三种语义；`/setting-entries` 是同一实体的管理面。
 * 读写一律 `setting-manage`（与 /admin/settings 同码，不再细分只读码）。
 */
@RestController
@RequestMapping("/api/v1")
public class SettingEntryController {

  private final SettingEntryQueryService queryService;
  private final SaveSettingEntryHandler saveHandler;

  public SettingEntryController(SettingEntryQueryService queryService, SaveSettingEntryHandler saveHandler) {
    this.queryService = queryService;
    this.saveHandler = saveHandler;
  }

  @GetMapping("/setting-entries")
  @Operation(operationId = "listSettingEntries")
  @RequirePrivilege("setting-manage")
  public DataEnvelope<SettingEntryQueryService.SettingEntryList> list(HttpServletRequest request) {
    return DataEnvelope.of(queryService.page(request.getParameterMap()));
  }

  @PostMapping("/setting-entries")
  @Operation(operationId = "createSettingEntry")
  @RequirePrivilege("setting-manage")
  @Audit(action = "setting-entry-create", objectType = "settingEntry")
  public DataEnvelope<SettingEntryQueryService.SettingEntryView> create(
      @RequestBody SaveSettingEntryHandler.SettingEntryCreateRequest body) {
    return DataEnvelope.of(saveHandler.create(body));
  }

  @PatchMapping("/setting-entries/{key}")
  @Operation(operationId = "updateSettingEntry")
  @RequirePrivilege("setting-manage")
  @Audit(action = "setting-entry-update", objectType = "settingEntry")
  @AuditDiff(objectType = "settingEntry", idParam = "key")
  public DataEnvelope<SettingEntryQueryService.SettingEntryView> update(@PathVariable String key,
      @RequestBody SaveSettingEntryHandler.SettingEntryUpdateRequest body) {
    return DataEnvelope.of(saveHandler.update(key, body));
  }

  @DeleteMapping("/setting-entries/{key}")
  @Operation(operationId = "deleteSettingEntry")
  @RequirePrivilege("setting-manage")
  @Audit(action = "setting-entry-delete", objectType = "settingEntry")
  @AuditDiff(objectType = "settingEntry", idParam = "key")
  public DataEnvelope<Void> delete(@PathVariable String key) {
    saveHandler.delete(key);
    return DataEnvelope.empty();
  }
}
