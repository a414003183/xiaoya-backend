package net.zentao.platform.meta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.regex.Pattern;
import net.zentao.platform.error.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 参数管理写入口（T15 P1-3）：只碰系统行（owner=system）。
 *
 * <p>值必须是**合法 JSON 文本**（`setting.item_value` 存的就是 JSON，读取方按 JSON 解析）：
 * 挡住「把 Asia/Shanghai 裸写进去」这类当下能存、之后读取方崩在半路的值。
 * 值为 `null` 文本合法（JSON null），空串不算。
 */
@Component
public class SaveSettingEntryHandler {

  /** domain 段：小写字母开头（与既有键 common/notify/dashboard 同形）。 */
  private static final Pattern DOMAIN = Pattern.compile("^[a-z][a-z0-9-]*$");
  /** 列宽（domain 60 / item_key 60 / item_value TEXT）：超了先在应用层挡住，别等 DB 报错。 */
  private static final int MAX_PART = 60;
  private static final int MAX_VALUE = 8000;

  private final SettingRepository repository;
  private final JsonMapper jsonMapper;

  public SaveSettingEntryHandler(SettingRepository repository, JsonMapper jsonMapper) {
    this.repository = repository;
    this.jsonMapper = jsonMapper;
  }

  public record SettingEntryCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String key,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String value) {}

  public record SettingEntryUpdateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String value) {}

  @Transactional
  public SettingEntryQueryService.SettingEntryView create(SettingEntryCreateRequest command) {
    String[] parts = validatedParts(command.key());
    String value = validatedValue(command.value());
    if (repository.findSystem(parts[0], parts[1]).isPresent()) {
      throw ApiException.validation(Map.of("key", "duplicate"));
    }
    repository.upsert(SettingRepository.SYSTEM_OWNER, parts[0], parts[1], value);
    return new SettingEntryQueryService.SettingEntryView(
        SettingEntryQueryService.SettingEntryView.flat(parts[0], parts[1]), parts[0], parts[1], value);
  }

  @Transactional
  public SettingEntryQueryService.SettingEntryView update(String flatKey, SettingEntryUpdateRequest command) {
    String[] parts = validatedParts(flatKey);
    String value = validatedValue(command.value());
    repository.findSystem(parts[0], parts[1]).orElseThrow(() -> ApiException.notFound("entity.setting"));
    repository.upsert(SettingRepository.SYSTEM_OWNER, parts[0], parts[1], value);
    return new SettingEntryQueryService.SettingEntryView(
        SettingEntryQueryService.SettingEntryView.flat(parts[0], parts[1]), parts[0], parts[1], value);
  }

  @Transactional
  public void delete(String flatKey) {
    String[] parts = validatedParts(flatKey);
    if (repository.deleteSystem(parts[0], parts[1]) == 0) {
      throw ApiException.notFound("entity.setting");
    }
  }

  /** 键形状校验：`<domain>.<key>`、两段各自非空且不超列宽、domain 小写字母开头（不合形一律 42201 fields.key）。 */
  private static String[] validatedParts(String flatKey) {
    if (flatKey == null || flatKey.isBlank()) {
      throw ApiException.validation(Map.of("key", "required"));
    }
    String trimmed = flatKey.trim();
    int dot = trimmed.indexOf('.');
    if (dot <= 0 || dot == trimmed.length() - 1) {
      throw ApiException.validation(Map.of("key", "pattern"));
    }
    String domain = trimmed.substring(0, dot);
    String itemKey = trimmed.substring(dot + 1);
    if (!DOMAIN.matcher(domain).matches() || domain.length() > MAX_PART || itemKey.length() > MAX_PART) {
      throw ApiException.validation(Map.of("key", "pattern"));
    }
    return new String[] {domain, itemKey};
  }

  private String validatedValue(String value) {
    if (value == null) {
      throw ApiException.validation(Map.of("value", "required"));
    }
    if (value.length() > MAX_VALUE) {
      throw ApiException.validation(Map.of("value", "size"));
    }
    try {
      jsonMapper.readTree(value);
    } catch (JacksonException invalidJson) {
      throw ApiException.validation(Map.of("value", "invalidJson"));
    }
    return value;
  }
}
