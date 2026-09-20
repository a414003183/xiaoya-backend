package net.zentao.platform.workflow;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 守卫/分派条件表达式求值（platform 卡 §4.3 约束）：只解释内建子集，不执行任意代码。
 *
 * <p>支持的语法（{@code <field>} 通过 {@link FieldSource} 取当前对象字段值）：
 * <ul>
 *   <li>比较：{@code <field>[.size()] == | != | &gt; | &gt;= | &lt; | &lt;= <literal>}</li>
 *   <li>集合成员：{@code <field> in [a, b]} / {@code <field> not in [a, b]}</li>
 *   <li>空值判定：{@code <field> [not] empty}、{@code <field> [not] null}</li>
 *   <li>真值判定：裸 {@code <field>}（null/空集合/空串/0 → false）</li>
 * </ul>
 * 字面量：数字、{@code true/false/null}、引号字符串，裸词按字符串（{@code type == story}）。
 *
 * <p>语法不支持 → 编译期抛 {@link IllegalArgumentException}（{@link YamlStateMachineLoader} 启动即失败）。
 */
public final class GuardEvaluator {

  private GuardEvaluator() {}

  /** 字段取值来源：域对象经 {@link WorkflowTarget#field(String)} 适配。 */
  public interface FieldSource {
    Object value(String field);
  }

  /** 已编译条件。 */
  public interface Condition {
    boolean test(FieldSource source);
  }

  private static final String FIELD = "[A-Za-z_][A-Za-z0-9_]*";
  private static final String SIZE_CALL = "(\\.size\\(\\))?";

  private static final Pattern COMPARISON = Pattern.compile(
      "^(" + FIELD + ")" + SIZE_CALL + "\\s*(==|!=|>=|<=|>|<|not\\s+in|in)\\s+(.+)$");
  private static final Pattern EMPTINESS = Pattern.compile(
      "^(" + FIELD + ")" + SIZE_CALL + "\\s+(not\\s+)?(empty|null)$");
  private static final Pattern BARE = Pattern.compile("^" + FIELD + "$");

  public static Condition compile(String expression) {
    String expr = expression == null ? "" : expression.trim();
    if (expr.isEmpty()) {
      throw new IllegalArgumentException("表达式为空");
    }

    Matcher comparison = COMPARISON.matcher(expr);
    if (comparison.matches()) {
      String field = comparison.group(1);
      boolean size = comparison.group(2) != null;
      String op = comparison.group(3).replaceAll("\\s+", " ").toLowerCase();
      String raw = comparison.group(4).trim();
      if ("in".equals(op) || "not in".equals(op)) {
        boolean negated = "not in".equals(op);
        List<Object> expected = literals(raw);
        return source -> {
          Object actual = value(source, field, size);
          boolean contained = expected.stream().anyMatch(candidate -> equals(actual, candidate));
          return negated != contained;
        };
      }
      Object expected = literal(raw);
      return source -> compare(op, value(source, field, size), expected);
    }

    Matcher emptiness = EMPTINESS.matcher(expr);
    if (emptiness.matches()) {
      String field = emptiness.group(1);
      boolean size = emptiness.group(2) != null;
      boolean negated = emptiness.group(3) != null;
      boolean nullOnly = "null".equals(emptiness.group(4));
      return source -> {
        Object actual = value(source, field, size);
        boolean holds = nullOnly ? actual == null : !truthy(actual);
        return negated != holds;
      };
    }

    if (BARE.matcher(expr).matches()) {
      return source -> truthy(source.value(expr));
    }

    throw new IllegalArgumentException("不支持的表达式：" + expression);
  }

  private static Object value(FieldSource source, String field, boolean size) {
    Object actual = source.value(field);
    return size ? sizeOf(actual, field) : actual;
  }

  private static boolean compare(String op, Object actual, Object expected) {
    return switch (op) {
      case "==" -> equals(actual, expected);
      case "!=" -> !equals(actual, expected);
      default -> numeric(op, actual, expected);
    };
  }

  private static boolean equals(Object actual, Object expected) {
    if (expected == null) {
      return actual == null;
    }
    if (actual == null) {
      return false;
    }
    if (expected instanceof Boolean || actual instanceof Boolean) {
      return String.valueOf(actual).equalsIgnoreCase(String.valueOf(expected));
    }
    BigDecimal left = number(actual);
    BigDecimal right = number(expected);
    if (left != null && right != null) {
      return left.compareTo(right) == 0;
    }
    return String.valueOf(actual).equals(String.valueOf(expected));
  }

  private static boolean numeric(String op, Object actual, Object expected) {
    BigDecimal left = number(actual);
    BigDecimal right = number(expected);
    if (left == null || right == null) {
      return false;
    }
    int result = left.compareTo(right);
    return switch (op) {
      case ">" -> result > 0;
      case ">=" -> result >= 0;
      case "<" -> result < 0;
      default -> result <= 0;
    };
  }

  private static BigDecimal number(Object value) {
    if (value instanceof Number number) {
      return new BigDecimal(number.toString());
    }
    if (value instanceof String text && text.trim().matches("-?\\d+(\\.\\d+)?")) {
      return new BigDecimal(text.trim());
    }
    return null;
  }

  private static int sizeOf(Object value, String field) {
    if (value == null) {
      return 0;
    }
    if (value instanceof Collection<?> collection) {
      return collection.size();
    }
    if (value instanceof Map<?, ?> map) {
      return map.size();
    }
    if (value instanceof CharSequence text) {
      return text.length();
    }
    if (value instanceof Object[] array) {
      return array.length;
    }
    throw new IllegalStateException("字段 " + field + " 不支持 .size()：" + value.getClass().getSimpleName());
  }

  private static boolean truthy(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value instanceof Collection<?> collection) {
      return !collection.isEmpty();
    }
    if (value instanceof Map<?, ?> map) {
      return !map.isEmpty();
    }
    if (value instanceof CharSequence text) {
      return !text.toString().isBlank();
    }
    if (value instanceof Object[] array) {
      return array.length > 0;
    }
    if (value instanceof Number number) {
      return new BigDecimal(number.toString()).compareTo(BigDecimal.ZERO) != 0;
    }
    return true;
  }

  private static List<Object> literals(String raw) {
    String body = raw;
    if (body.startsWith("[") && body.endsWith("]")) {
      body = body.substring(1, body.length() - 1);
    }
    if (body.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(body.split(",")).map(GuardEvaluator::literal).toList();
  }

  private static Object literal(String raw) {
    String text = raw.trim();
    if (text.length() >= 2
        && ((text.startsWith("'") && text.endsWith("'")) || (text.startsWith("\"") && text.endsWith("\"")))) {
      return text.substring(1, text.length() - 1);
    }
    if ("true".equalsIgnoreCase(text)) {
      return Boolean.TRUE;
    }
    if ("false".equalsIgnoreCase(text)) {
      return Boolean.FALSE;
    }
    if ("null".equalsIgnoreCase(text)) {
      return null;
    }
    BigDecimal number = number(text);
    return number != null ? number : text;
  }
}
