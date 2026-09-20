package net.zentao.platform.meta;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.zentao.platform.error.ApiException;
import org.springframework.stereotype.Component;

/**
 * 自定义字段校验（platform 卡 §3.10 / A-05）：写路径在域侧挂钩，本类是唯一规则真源。
 * map null/空直接过；未知键/必填缺失/类型不符/枚举越界 → 42201（fields 逐条给出）。
 */
@Component
public class FieldDefValidator {

  private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

  private final FieldDefRegistry registry;

  public FieldDefValidator(FieldDefRegistry registry) {
    this.registry = registry;
  }

  /** create=true 时执行 required 检查；update 时只校验已传键的值。 */
  public void validate(String domain, Map<String, Object> customFields, boolean create) {
    if (customFields == null || customFields.isEmpty()) {
      return;
    }
    List<FieldDef> defs = registry.byDomain(domain);
    Map<String, FieldDef> byKey = new LinkedHashMap<>();
    defs.forEach(def -> byKey.put(def.itemKey(), def));

    Map<String, String> errors = new LinkedHashMap<>();
    for (String key : customFields.keySet()) {
      if (!byKey.containsKey(key)) {
        errors.put(key, "unknown");
      }
    }
    for (FieldDef def : defs) {
      if (def.required() && create && !customFields.containsKey(def.itemKey())) {
        errors.put(def.itemKey(), "required");
      }
    }
    customFields.forEach((key, value) -> {
      FieldDef def = byKey.get(key);
      if (def != null && !checkValue(def, value)) {
        errors.put(key, "invalid");
      }
    });
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
  }

  private boolean checkValue(FieldDef def, Object value) {
    return switch (typeOf(def)) {
      case "select" -> isOptionItem(def, value);
      case "multiselect", "checkbox" -> isOptionValues(def, value);
      case "number", "decimal" -> value instanceof Number;
      case "date" -> isDate(value);
      case "datetime" -> isDateTime(value);
      default -> value instanceof String; // text/textarea
    };
  }

  private static String typeOf(FieldDef def) {
    return def.type() == null ? "" : def.type().toLowerCase(Locale.ROOT);
  }

  /** 非空数组且各 ∈ options；checkbox 无 options 时任意标量。 */
  private static boolean isOptionValues(FieldDef def, Object value) {
    if (!(value instanceof List<?> values) || values.isEmpty()) {
      return false;
    }
    boolean checkboxWithoutOptions = "checkbox".equals(typeOf(def)) && def.options().isEmpty();
    return values.stream().allMatch(item -> checkboxWithoutOptions ? isScalar(item) : isOptionItem(def, item));
  }

  private static boolean isOptionItem(FieldDef def, Object item) {
    return item instanceof String option && def.options().stream().anyMatch(o -> o.value().equals(option));
  }

  private static boolean isScalar(Object value) {
    return value instanceof String || value instanceof Number || value instanceof Boolean;
  }

  private static boolean isDate(Object value) {
    if (!(value instanceof String date) || !DATE.matcher(date).matches()) {
      return false;
    }
    try {
      java.time.LocalDate.parse(date);
      return true;
    } catch (DateTimeParseException e) {
      return false;
    }
  }

  /** ISO-8601：带偏移或裸本地时间均接受。 */
  private static boolean isDateTime(Object value) {
    if (!(value instanceof String datetime)) {
      return false;
    }
    try {
      OffsetDateTime.parse(datetime);
      return true;
    } catch (DateTimeParseException offsetFailed) {
      try {
        LocalDateTime.parse(datetime);
        return true;
      } catch (DateTimeParseException localFailed) {
        return false;
      }
    }
  }
}
