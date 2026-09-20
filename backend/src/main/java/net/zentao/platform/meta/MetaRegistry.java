package net.zentao.platform.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 域元数据注册表（platform 卡 §5）：各域启动时注册自己的 meta；未注册域 → 40401。不做服务端缓存。 */
@Component
public class MetaRegistry {

  private final Map<String, MetaView> definitions = new ConcurrentHashMap<>();
  private final FieldDefRegistry fieldDefRegistry;

  public MetaRegistry(FieldDefRegistry fieldDefRegistry) {
    this.fieldDefRegistry = fieldDefRegistry;
  }

  public void register(String domain, MetaView definition) {
    definitions.put(domain, definition);
  }

  /** A-05：读取时统一追加该域 field_def 生成的自定义字段（key=itemKey，i18n=customField.field.<itemKey>）。 */
  public Optional<MetaView> get(String domain) {
    MetaView view = definitions.get(domain);
    if (view == null) {
      return Optional.empty();
    }
    return Optional.of(withCustomFields(view));
  }

  private MetaView withCustomFields(MetaView view) {
    List<FieldDef> defs = fieldDefRegistry.byDomain(view.domain());
    if (defs.isEmpty()) {
      return view;
    }
    List<MetaView.MetaField> fields = new ArrayList<>(view.fields());
    for (FieldDef def : defs) {
      fields.add(new MetaView.MetaField(
          def.itemKey(),
          def.type(),
          def.required() ? Boolean.TRUE : null,
          null,
          "customField.field." + def.itemKey(),
          null,
          null,
          def.options().stream()
              .map(option -> Map.<String, Object>of("value", option.value(), "i18n", option.i18n()))
              .toList(),
          def.visibleWhen()));
    }
    return new MetaView(view.domain(), fields, view.list(), view.actions(), view.statusVisuals());
  }
}
