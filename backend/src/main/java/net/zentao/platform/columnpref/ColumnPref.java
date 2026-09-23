package net.zentao.platform.columnpref;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.zentao.platform.error.ApiException;

/**
 * 列设置聚合（platform「列设置」能力）：某账号在某列表资源上的有序列偏好。
 *
 * <p>resource 是列表页身份（如 {@code quality-bugs}），与前端 ListCard 的 columnSettingKey 同值；
 * columns 顺序即展示顺序，是**个人级偏好**——读写恒按会话账号，不存在跨账号面。
 * 本聚合只表达「已存在的设置」；「未设置」由仓储返回空 Optional 表达（前端用页面默认列）。
 */
public record ColumnPref(String resource, List<ColumnPrefItem> columns) {

  /** resource 上限（与 user_column_pref.resource 列同宽）。 */
  public static final int RESOURCE_MAX = 64;
  /** 单列 key 上限。 */
  public static final int KEY_MAX = 64;
  /** 单表列数上限（防超长 JSON 落库；任何真实列表都远小于此）。 */
  public static final int COLUMNS_MAX = 200;

  private static final Pattern RESOURCE_PATTERN = Pattern.compile("^[a-z0-9-]+$");
  private static final Set<String> FIXED_VALUES = Set.of(ColumnPrefItem.FIXED_LEFT, ColumnPrefItem.FIXED_RIGHT);

  /** 路径参数规整与校验（`^[a-z0-9-]{1,64}$`）；非法 → 42201 带 fields.resource。 */
  public static String normalizeResource(String resource) {
    String trimmed = resource == null ? "" : resource.trim();
    if (trimmed.isEmpty() || trimmed.length() > RESOURCE_MAX || !RESOURCE_PATTERN.matcher(trimmed).matches()) {
      throw ApiException.validation(Map.of("resource", "invalid"));
    }
    return trimmed;
  }

  /**
   * 请求体列项规整与校验：去键空白、保留请求顺序、重复键即非法、fixed 归一到 left/right/null。
   * 非法（键空/超长/重复/数量超限/fixed 枚举外值/整表缺失）→ 42201 带 fields.columns。
   */
  public static List<ColumnPrefItem> normalizeColumns(List<ColumnPrefItem> columns) {
    if (columns == null || columns.isEmpty()) {
      throw ApiException.validation(Map.of("columns", "empty"));
    }
    if (columns.size() > COLUMNS_MAX) {
      throw ApiException.validation(Map.of("columns", "tooMany"));
    }
    List<ColumnPrefItem> normalized = new ArrayList<>(columns.size());
    Set<String> seen = new LinkedHashSet<>();
    for (ColumnPrefItem item : columns) {
      if (item == null) {
        throw ApiException.validation(Map.of("columns", "itemEmpty"));
      }
      String key = item.key() == null ? "" : item.key().trim();
      if (key.isEmpty() || key.length() > KEY_MAX) {
        throw ApiException.validation(Map.of("columns", "keyInvalid"));
      }
      if (!seen.add(key)) {
        throw ApiException.validation(Map.of("columns", "keyDuplicated"));
      }
      normalized.add(new ColumnPrefItem(key, item.visible(), normalizeFixed(item)));
    }
    return List.copyOf(normalized);
  }

  /** fixed 归一：null/空白 = 不固定；枚举外值 → 42201。 */
  private static String normalizeFixed(ColumnPrefItem item) {
    String fixed = item.fixed() == null ? null : item.fixed().trim();
    if (fixed == null || fixed.isEmpty()) {
      return null;
    }
    if (!FIXED_VALUES.contains(fixed)) {
      throw ApiException.validation(Map.of("columns", "fixedInvalid"));
    }
    return fixed;
  }
}
