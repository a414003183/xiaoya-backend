package net.zentao.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.zentao.platform.meta.SaveSettingsHandler;
import net.zentao.platform.meta.SettingQueryService;
import net.zentao.platform.session.SessionResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET/PUT /settings（platform 卡 §5；系统键写需 setting-manage，个人键 notify.* 放行）。 */
@RestController
@RequestMapping("/api/v1")
public class SettingController {

  private final SettingQueryService queryService;
  private final SaveSettingsHandler saveHandler;
  private final SessionResolver resolver;

  public SettingController(SettingQueryService queryService, SaveSettingsHandler saveHandler, SessionResolver resolver) {
    this.queryService = queryService;
    this.saveHandler = saveHandler;
    this.resolver = resolver;
  }

  @GetMapping("/settings")
  @Operation(operationId = "getSettings")
  public DataEnvelope<SettingView> getSettings(@RequestParam String keys, HttpServletRequest request) {
    List<String> flatKeys = Arrays.stream(keys.split(",")).map(String::trim).filter(key -> !key.isEmpty()).toList();
    return DataEnvelope.of(new SettingView(queryService.get(resolver.resolve(request), flatKeys)));
  }

  @PutMapping("/settings")
  @Operation(operationId = "putSettings")
  public DataEnvelope<SettingView> putSettings(@RequestBody SettingsUpdateRequest body, HttpServletRequest request) {
    return DataEnvelope.of(new SettingView(saveHandler.save(resolver.resolve(request), body.settings())));
  }

  public record SettingView(Map<String, Object> settings) {}

  public record SettingsUpdateRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> settings) {}
}
