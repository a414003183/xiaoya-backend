package net.zentao.platform.meta;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * meta 端点载荷（03 §5）：域字段元数据 + 列默认 + 动作目录 + 状态可视化。
 * actions[].allowedStatus 与 workflow YAML 同源（platform 卡 §4.3）；
 * visibleWhen 为 field_def 自定义字段的显隐条件（A-05，解析后的 JSON 树透传）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetaView(
    String domain,
    List<MetaField> fields,
    MetaList list,
    List<MetaAction> actions,
    Map<String, MetaStatusVisual> statusVisuals) {

  public MetaView {
    fields = fields == null ? List.of() : List.copyOf(fields);
    actions = actions == null ? List.of() : List.copyOf(actions);
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MetaField(
      String key,
      String type,
      Boolean required,
      Integer maxLength,
      String i18n,
      String source,
      Boolean multiple,
      List<Map<String, Object>> options,
      Object visibleWhen) {

    /** 兼容各域 Registrar 的既有八参构造（visibleWhen 缺省 null）。 */
    public MetaField(
        String key, String type, Boolean required, Integer maxLength, String i18n, String source,
        Boolean multiple, List<Map<String, Object>> options) {
      this(key, type, required, maxLength, i18n, source, multiple, options, null);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MetaList(List<String> defaultColumns, String defaultSort) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MetaAction(String code, String action, String i18n, List<String> allowedStatus) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MetaStatusVisual(String tone, String i18n) {}
}
