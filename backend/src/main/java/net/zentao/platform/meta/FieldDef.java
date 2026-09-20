package net.zentao.platform.meta;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * 自定义字段定义（platform 卡 §3.10）：type ∈ text/textarea/select/multiselect/checkbox/number/decimal/date/datetime；
 * options 为 [{value, i18n}]；visibleWhen 为原样 JSON（解析后的树，meta 端点透传）。
 */
public record FieldDef(
    String domain,
    String itemKey,
    String type,
    boolean required,
    List<FieldDefOption> options,
    JsonNode visibleWhen,
    int sort) {

  public FieldDef {
    options = options == null ? List.of() : List.copyOf(options);
  }

  public record FieldDefOption(String value, String i18n) {}
}
