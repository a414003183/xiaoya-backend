package net.zentao.platform.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 字段级 diff 引擎（ADR-004 决策 2 的注解轨，T04）：前后两次快照 → 结构化 {@code changes}。
 *
 * <p>敏感字段（{@link #SENSITIVE} 名表）只比「变没变」，值一律写 {@code ***}——审计要能回答
 * 「谁在什么时候改了密码」，但不能把口令/令牌留在审计表里（审计表是只追加、保留 180 天的表）。
 *
 * <p>取值集（{@code keyFields} 为空时）：两次快照键的并集，按字段名字典序输出，保证同一变更的 JSON 稳定。
 */
@Component
public class AuditDiffer {

  /** 值掩码的字段名（**包含匹配、忽略大小写**）：password/newPassword/tokenHash 都会命中。 */
  private static final Set<String> SENSITIVE = Set.of("password", "secret", "token", "idcard", "mobile");

  private static final String MASK = "***";

  private final AuditSnapshotRegistry registry;
  private final JsonMapper jsonMapper;

  public AuditDiffer(AuditSnapshotRegistry registry, JsonMapper jsonMapper) {
    this.registry = registry;
    this.jsonMapper = jsonMapper;
  }

  /** 执行前取旧值（未注册对象 → null，调用方跳过 diff）。 */
  public Map<String, Object> before(String objectType, Long id) {
    return registry.snapshot(objectType, id);
  }

  /** 执行前取旧值：键寻址资源（setting 的 key / menu 的 nodeKey，T10）。 */
  public Map<String, Object> beforeKeyed(String objectType, String key) {
    return registry.snapshotKeyed(objectType, key);
  }

  /**
   * 执行后比对并序列化：{@code changes} 的 JSON 文本；无变化/取不到快照 → null（不写空数组，
   * 列表里「null = 没采到 diff」与「[] = 采到但无变化」要能区分）。
   *
   * <p>两个方向的取不到语义不同（T10 补）：**旧值取不到 = 新建**（没有旧值可比，硬造一份等于编数据）→ 不记；
   * **新值取不到而旧值在 = 这次动作后对象不存在**（软删）→ 记成 before → 空，审计要能回答「删掉的那行原来是什么」。
   * provider 抛错在注册表里与「对象不存在」同为一类（那里已留 WARN），此处按后者处理。
   */
  public String changesJson(Map<String, Object> before, String objectType, Long id, List<String> keyFields) {
    if (before == null) {
      return null;
    }
    Map<String, Object> after = registry.snapshot(objectType, id);
    List<AuditChange> changes = diff(before, after == null ? Map.of() : after, keyFields);
    return changes.isEmpty() ? null : jsonMapper.writeValueAsString(changes);
  }

  /**
   * 审批类整快照（VISION 事项 4 第 9 行）：{@code {"before":…,"after":…}} 的 JSON 文本，写进
   * {@code snapshot} 列；取不到任一侧 → null（与 changes 同口径：采不到就不写，不编造）。
   */
  public String snapshotJson(Map<String, Object> before, String objectType, Long id) {
    Map<String, Object> after = registry.snapshot(objectType, id);
    if (before == null || after == null) {
      return null;
    }
    Map<String, Object> both = new LinkedHashMap<>();
    both.put("before", before);
    both.put("after", after);
    return jsonMapper.writeValueAsString(both);
  }

  /** 键寻址资源的整快照（与 {@link #snapshotJson} 同口径，T10）。 */
  public String keyedSnapshotJson(Map<String, Object> before, String objectType, String key) {
    Map<String, Object> after = registry.snapshotKeyed(objectType, key);
    if (before == null || after == null) {
      return null;
    }
    Map<String, Object> both = new LinkedHashMap<>();
    both.put("before", before);
    both.put("after", after);
    return jsonMapper.writeValueAsString(both);
  }

  /** 键寻址资源的前后比对（T10）：与 {@link #changesJson} 同口径（旧值取不到=新建不记；新值取不到=已移除）。 */
  public String keyedChangesJson(Map<String, Object> before, String objectType, String key,
      List<String> keyFields) {
    if (before == null) {
      return null;
    }
    Map<String, Object> after = registry.snapshotKeyed(objectType, key);
    List<AuditChange> changes = diff(before, after == null ? Map.of() : after, keyFields);
    return changes.isEmpty() ? null : jsonMapper.writeValueAsString(changes);
  }
  /**
   * 前后快照比对（取值集 = keyFields 或两次快照键的并集）。
   *
   * <p>空值比对用 {@link Objects#equals}：{@code json(null)} 返回 null，直接 {@code .equals} 会在
   * 「一侧有值一侧为空」时抛 NPE——而业务对象里 assignee/deadline 这类字段本来就常为空（T10 实测）。
   */
  public List<AuditChange> diff(Map<String, Object> before, Map<String, Object> after, List<String> keyFields) {
    Set<String> fields = keyFields == null || keyFields.isEmpty()
        ? new TreeSet<>(unionOfKeys(before, after))
        : new LinkedHashSet<>(keyFields);
    List<AuditChange> changes = new ArrayList<>();
    for (String field : fields) {
      Object oldValue = before.get(field);
      Object newValue = after.get(field);
      if (isSensitive(field)) {
        if (!String.valueOf(oldValue).equals(String.valueOf(newValue))) {
          changes.add(new AuditChange(field, oldValue == null ? null : MASK, newValue == null ? null : MASK));
        }
        continue;
      }
      String oldJson = json(oldValue);
      String newJson = json(newValue);
      if (!Objects.equals(oldJson, newJson)) {
        changes.add(new AuditChange(field, oldJson, newJson));
      }
    }
    return changes;
  }

  /** changes 的 JSON 文本 → 结构化列表（详情端点用；坏 JSON 读成空并留痕）。 */
  public List<AuditChange> parse(String changesJson) {
    if (changesJson == null || changesJson.isBlank()) {
      return null;
    }
    try {
      return jsonMapper.readValue(changesJson, new tools.jackson.core.type.TypeReference<List<AuditChange>>() {});
    } catch (RuntimeException broken) {
      return null;
    }
  }

  /** 字段级变更（契约 schema AuditChange；值以 JSON 文本表达，前端直接渲染 diff 表格）。 */
  public record AuditChange(String field, String before, String after) {}

  static boolean isSensitive(String field) {
    String lower = field.toLowerCase();
    return SENSITIVE.stream().anyMatch(lower::contains);
  }

  private static Set<String> unionOfKeys(Map<String, Object> before, Map<String, Object> after) {
    Set<String> keys = new LinkedHashSet<>(before.keySet());
    keys.addAll(after.keySet());
    return keys;
  }

  /** 值的规范表达：null 保持 null，其余按 JSON 序列化（字符串带引号，便于前端区分 `"1"` 与 `1`）。 */
  private String json(Object value) {
    return value == null ? null : jsonMapper.writeValueAsString(value);
  }
}
