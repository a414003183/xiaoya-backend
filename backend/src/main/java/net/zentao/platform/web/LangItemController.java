package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import net.zentao.platform.meta.LangItemQueryService;
import net.zentao.platform.meta.SaveLangItemHandler;
import net.zentao.platform.rbac.RequirePrivilege;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET/PUT/DELETE /lang-items/{domain}/{field}（platform 卡 §5；写/删需 lang-manage）。 */
@RestController
@RequestMapping("/api/v1")
public class LangItemController {

  private final LangItemQueryService queryService;
  private final SaveLangItemHandler saveHandler;

  public LangItemController(LangItemQueryService queryService, SaveLangItemHandler saveHandler) {
    this.queryService = queryService;
    this.saveHandler = saveHandler;
  }

  @GetMapping("/lang-items/{domain}/{field}")
  @Operation(operationId = "getLangItems")
  public DataEnvelope<LangItemView> getLangItems(
      @PathVariable String domain,
      @PathVariable String field,
      @RequestParam(defaultValue = "zh-cn") String lang) {
    var merged = queryService.merged(lang, domain, field);
    return DataEnvelope.of(new LangItemView(merged.items(), merged.overridden()));
  }

  @PutMapping("/lang-items/{domain}/{field}")
  @Operation(operationId = "putLangItems")
  @RequirePrivilege("lang-manage")
  public DataEnvelope<LangItemView> putLangItems(
      @PathVariable String domain,
      @PathVariable String field,
      @RequestBody LangItemsUpdateRequest body,
      @RequestParam(defaultValue = "zh-cn") String lang) {
    saveHandler.save(lang, domain, field, body.items());
    return DataEnvelope.of(new LangItemView(body.items(), true));
  }

  @DeleteMapping("/lang-items/{domain}/{field}")
  @Operation(operationId = "deleteLangItems")
  @RequirePrivilege("lang-manage")
  public DataEnvelope<LangItemView> deleteLangItems(
      @PathVariable String domain,
      @PathVariable String field,
      @RequestParam(defaultValue = "zh-cn") String lang) {
    return DataEnvelope.of(new LangItemView(saveHandler.restoreDefaults(lang, domain, field), false));
  }

  public record LangItemView(Map<String, String> items, boolean overridden) {}

  public record LangItemsUpdateRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, String> items) {}
}
