package net.zentao.platform.filters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;

/**
 * filters DSL 解析（03 §3）——纯函数，无 Spring/Web 依赖：
 * <pre>
 * GET /x?page=2&limit=20&sort=-priority,id&filters[status]=active&filters[id]=1,2,3&filters[createdAt]=a..b&q=关键词
 * </pre>
 * 值形态：等值 | 逗号 IN | `..` 闭/半开区间 | @me/@myDepartment/@null/@notNull 特殊量。
 * 数值/日期的列类型强转在编译层（FilterPredicate）按域列定义做，P0 以字符串承载。
 */
public record Filters(List<FilterClause> clauses, List<SortKey> sortKeys, int page, int limit, String q) {

  public static final int DEFAULT_PAGE = 1;
  public static final int DEFAULT_LIMIT = 20;
  public static final int MAX_LIMIT = 200;
  /** A-04：format=csv 导出请求的 limit 上限（行数仍受 CSV 横切 5000 行闸门约束）。 */
  public static final int CSV_MAX_LIMIT = 5000;

  private static final String FILTERS_PREFIX = "filters[";
  private static final String RANGE_SEPARATOR = "..";
  private static final String NULL = "@null";
  private static final String NOT_NULL = "@notNull";

  public Filters {
    clauses = List.copyOf(clauses);
    sortKeys = List.copyOf(sortKeys);
  }

  public int offset() {
    return (page - 1) * limit;
  }

  public enum Op {
    EQ,
    IN,
    RANGE,
    IS_NULL,
    NOT_NULL
  }

  /** RANGE 时 values 恰为两个元素，边界可为 null（半开区间）。 */
  public record FilterClause(String field, Op op, List<String> values) {
    public FilterClause {
      values = List.copyOf(values);
    }
  }

  public record SortKey(String field, boolean descending) {}

  public static Filters parse(Map<String, String[]> params, FieldRegistry registry) {    List<FilterClause> clauses = new ArrayList<>();
    for (Map.Entry<String, String[]> entry : params.entrySet()) {
      String name = entry.getKey();
      if (!name.startsWith(FILTERS_PREFIX) || !name.endsWith("]")) {
        continue;
      }
      String field = name.substring(FILTERS_PREFIX.length(), name.length() - 1);
      if (!registry.isFilterable(field)) {
        throw ApiException.badRequest("未注册的过滤字段：" + field);
      }
      for (String value : entry.getValue()) {
        clauses.add(parseClause(field, value));
      }
    }

    List<SortKey> sortKeys = new ArrayList<>();
    String[] sorts = params.getOrDefault("sort", new String[0]);
    if (sorts.length > 0) {
      for (String part : sorts[sorts.length - 1].split(",")) {
        String trimmed = part.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        boolean descending = trimmed.startsWith("-");
        String field = descending ? trimmed.substring(1) : trimmed;
        if (!registry.isSortable(field)) {
          throw ApiException.badRequest("未注册的排序字段：" + field);
        }
        sortKeys.add(new SortKey(field, descending));
      }
    }

    int page = intParam(params, "page", DEFAULT_PAGE);
    int limit = intParam(params, "limit", DEFAULT_LIMIT);
    if (page < 1) {
      throw ApiException.badRequest("page 从 1 开始。");
    }
    if (limit < 1) {
      throw ApiException.badRequest("limit 至少为 1。");
    }
    limit = Math.min(limit, "csv".equals(param(params, "format")) ? CSV_MAX_LIMIT : MAX_LIMIT);

    String[] qs = params.getOrDefault("q", new String[0]);
    String q = qs.length == 0 || qs[0].isBlank() ? null : qs[0];
    return new Filters(clauses, sortKeys, page, limit, q);
  }

  /** 追加一个恒定的等值过滤（workspace 卡 §3.4：/my/* 的 role → 目标域字段 = @me），同名字段以注入值为准。 */
  public static Map<String, String[]> withFilter(Map<String, String[]> params, String field, String value) {
    Map<String, String[]> merged = new java.util.LinkedHashMap<>(params);
    merged.put(FILTERS_PREFIX + field + "]", new String[] {value});
    return merged;
  }

  private static FilterClause parseClause(String field, String value) {
    if (NULL.equals(value)) {
      return new FilterClause(field, Op.IS_NULL, List.of());
    }
    if (NOT_NULL.equals(value)) {
      return new FilterClause(field, Op.NOT_NULL, List.of());
    }
    if (value.contains(RANGE_SEPARATOR)) {
      int separator = value.indexOf(RANGE_SEPARATOR);
      String low = value.substring(0, separator);
      String high = value.substring(separator + RANGE_SEPARATOR.length());
      if (low.isEmpty() && high.isEmpty()) {
        throw ApiException.badRequest("区间过滤两端不能同时为空：" + field);
      }
      return new FilterClause(field, Op.RANGE, List.of(low, high));
    }
    if (value.contains(",")) {
      List<String> items = List.of(value.split(","));
      return new FilterClause(field, Op.IN, items);
    }
    return new FilterClause(field, Op.EQ, List.of(value));
  }

  private static int intParam(Map<String, String[]> params, String name, int fallback) {
    String[] raw = params.getOrDefault(name, new String[0]);
    if (raw.length == 0 || raw[raw.length - 1].isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(raw[raw.length - 1]);
    } catch (NumberFormatException e) {
      throw ApiException.badRequest("参数 " + name + " 必须是整数。");
    }
  }

  private static String param(Map<String, String[]> params, String name) {
    String[] raw = params.getOrDefault(name, new String[0]);
    return raw.length == 0 ? null : raw[raw.length - 1];
  }
}
