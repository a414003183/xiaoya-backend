package net.zentao.platform.meta;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 自定义字段注册表（platform 卡 §3.10 / A-05）：field_def 全量加载，按域分组、域内按 sort 升序。
 * 行由 SQL/种子维护（无在线 UI），变更后需重启或调 {@link #reload()}。
 * ponytail: 首次访问时加载（非 @PostConstruct）——手动 MyBatis 装配下 Flyway 与 mapper 无 depends-on 接线，
 * 启动期查库存在表未建的窗口；量小（单表全量）一次加载后常驻。
 */
@Component
public class FieldDefRegistry {

  private static final TypeReference<List<FieldDef.FieldDefOption>> OPTION_LIST = new TypeReference<>() {};

  private final FieldDefRepository repository;
  private final JsonMapper jsonMapper;
  private volatile Map<String, List<FieldDef>> byDomain;

  public FieldDefRegistry(FieldDefRepository repository, JsonMapper jsonMapper) {
    this.repository = repository;
    this.jsonMapper = jsonMapper;
  }

  /** 域内定义（sort 升序，稳定序 itemKey 兜底）；未定义域返回空表。 */
  public List<FieldDef> byDomain(String domain) {
    Map<String, List<FieldDef>> snapshot = byDomain;
    if (snapshot == null) {
      synchronized (this) {
        if (byDomain == null) {
          byDomain = load();
        }
        snapshot = byDomain;
      }
    }
    return snapshot.getOrDefault(domain, List.of());
  }

  /** 重新加载（测试种子后用）。 */
  public void reload() {
    synchronized (this) {
      byDomain = load();
    }
  }

  private Map<String, List<FieldDef>> load() {
    return repository.findAll().stream()
        .map(this::toDef)
        .collect(Collectors.groupingBy(FieldDef::domain, Collectors.collectingAndThen(
            Collectors.toList(), defs -> defs.stream()
                .sorted(Comparator.comparingInt(FieldDef::sort).thenComparing(FieldDef::itemKey))
                .toList())));
  }

  private FieldDef toDef(FieldDefPO po) {
    JsonNode options = parseTree(po.getOptions());
    List<FieldDef.FieldDefOption> parsed = options != null && options.isArray()
        ? jsonMapper.convertValue(options, OPTION_LIST)
        : List.of();
    return new FieldDef(po.getDomain(), po.getItemKey(), po.getType(),
        po.getRequired() != null && po.getRequired() == 1, parsed, parseTree(po.getVisibleWhen()),
        po.getSort() == null ? 0 : po.getSort());
  }

  /** 宽容解析：坏 JSON 视为 null，不炸读路径（与 DataScope.AclParser 同口径）。 */
  private JsonNode parseTree(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return jsonMapper.readTree(json);
    } catch (Exception e) {
      return null;
    }
  }
}
