package net.zentao.platform.filters;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryOrderBy;
import com.mybatisflex.core.query.QueryWrapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;

/**
 * 把解析后的 {@link Filters} 编译为 mybatis-flex 查询条件（01 §2.2：解析纯函数 → 编译 QueryWrapper）。
 * <p>ponytail: 值以字符串落条件，数值/日期强转待第一个真实域列接入时在列映射层做（P1）。
 */
public final class FilterPredicate {

  private static final Pattern DATE_ONLY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

  private FilterPredicate() {}

  /**
   * @param columnOf JSON 字段名 → DB 列名（无映射的字段解析层已拦截）
   * @param specialOf 特殊量求值（如 @me → 当前账号）；无法求值 → 40001
   */
  public static QueryWrapper compile(
      Filters filters,
      Function<String, String> columnOf,
      Function<String, Optional<String>> specialOf) {
    return compile(filters, columnOf, specialOf, null);
  }

  /** @param base 服务端恒注入的前置条件（如 recipient=@me），与 DSL 条件 AND 组合。 */
  public static QueryWrapper compile(
      Filters filters,
      Function<String, String> columnOf,
      Function<String, Optional<String>> specialOf,
      QueryCondition base) {
    QueryWrapper query = QueryWrapper.create();
    List<QueryCondition> conditions = new ArrayList<>();
    if (base != null) {
      conditions.add(base);
    }
    for (Filters.FilterClause clause : filters.clauses()) {
      conditions.add(condition(clause, columnOf, specialOf));
    }
    if (!conditions.isEmpty()) {
      var iterator = conditions.iterator();
      query = query.where(iterator.next());
      while (iterator.hasNext()) {
        query = query.and(iterator.next());
      }
    }
    if (!filters.sortKeys().isEmpty()) {
      query = query.orderBy(filters.sortKeys().stream() // banned-words-ok：MyBatis-Flex 构造器方法名，非请求参数
          .map(key -> key.descending()
              ? new QueryColumn(columnOf.apply(key.field())).desc()
              : new QueryColumn(columnOf.apply(key.field())).asc())
          .toList()
          .toArray(new QueryOrderBy[0]));
    }
    return query;
  }

  private static QueryCondition condition(
      Filters.FilterClause clause,
      Function<String, String> columnOf,
      Function<String, Optional<String>> specialOf) {
    QueryColumn column = new QueryColumn(columnOf.apply(clause.field()));
    return switch (clause.op()) {
      case IS_NULL -> column.isNull();
      case NOT_NULL -> column.isNotNull();
      case EQ -> column.eq(resolve(clause.values().getFirst(), specialOf));
      case IN -> column.in(clause.values().stream().map(value -> resolve(value, specialOf)).toList());
      case RANGE -> range(column, clause.values());
    };
  }

  private static QueryCondition range(QueryColumn column, List<String> values) {
    String low = values.getFirst();
    String high = values.getLast();
    QueryCondition condition = null;
    if (!low.isEmpty()) {
      condition = column.ge(low);
    }
    if (!high.isEmpty()) {
      // 日期上界按「含当日」：< 次日。时间戳列（createdAt）用 <= 当日会漏掉当天数据；DATE 列结论等价（03 §3 闭区间语义）
      QueryCondition upper = DATE_ONLY.matcher(high).matches()
          ? column.lt(LocalDate.parse(high).plusDays(1).toString())
          : column.le(high);
      condition = condition == null ? upper : condition.and(upper);
    }
    return condition;
  }

  private static String resolve(String value, Function<String, Optional<String>> specialOf) {
    if (!value.startsWith("@")) {
      return value;
    }
    return specialOf
        .apply(value)
        .orElseThrow(() -> ApiException.keyed(ErrorCode.BAD_REQUEST, "filters.special.unparsable", value));
  }
}
