package net.zentao.platform.error;

import jakarta.validation.ConstraintViolation;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * 字段级**原因码**唯一登记处（T63 / AUDIT BE-09）：错误信封 `fields` 的「字段 → 原因」里，原因值一律取自本词表。
 * 产出方两路：手写守卫（{@link ApiException#validation} 的调用点）与 Bean Validation 映射
 * （{@code platform/web/ApiExceptionMapper} 的校验异常入口）；消费方是前端
 * {@code shared/form-fields.tsx} 的 `SERVER_FIELD_ERROR_KEYS` 通用表（原因码 → 文案键）。
 *
 * <p><b>词表（机器可判的最小集，与前端通用表逐词对齐）</b>：
 *
 * <ul>
 *   <li>{@code required} —— 必填缺失（jakarta：{@code @NotNull}/{@code @NotBlank}/{@code @NotEmpty}）；</li>
 *   <li>{@code duplicate} —— 业务重复（唯一键/同名守卫，非 jakarta 派生，只由手写守卫产出）；</li>
 *   <li>{@code invalid} —— 兜底不合法（未知约束、取值域不符等）；</li>
 *   <li>{@code tooLong} —— 超上限（{@code @Size} 越 max：字符串长度/集合元素数）；</li>
 *   <li>{@code tooSmall} —— 低于下限（{@code @Size} 低 min、{@code @Min}/{@code @DecimalMin}/
 *       {@code @Positive}/{@code @PositiveOrZero}）；</li>
 *   <li>{@code tooLarge} —— 超上限（{@code @Max}/{@code @DecimalMax}/{@code @Negative}/
 *       {@code @NegativeOrZero}）；</li>
 *   <li>{@code pattern} —— 格式不符（{@code @Pattern}/{@code @Email}）。</li>
 * </ul>
 *
 * <p><b>jakarta 约束 → 原因码映射规则</b>：按约束注解简单名查表（{@link #ofConstraint}）；
 * {@code @Size} 按越界方向分档（{@link #of} 里比对失效值的实际长度/元素数与 min/max 属性）；
 * 未登记的约束一律 {@code invalid}——新增约束先在本表登记，不许在映射入口散写字符串。
 */
public final class FieldReasons {

  public static final String REQUIRED = "required";
  public static final String DUPLICATE = "duplicate";
  public static final String INVALID = "invalid";
  public static final String TOO_LONG = "tooLong";
  public static final String TOO_SMALL = "tooSmall";
  public static final String TOO_LARGE = "tooLarge";
  public static final String PATTERN = "pattern";

  private FieldReasons() {}

  /** jakarta 约束违例 → 原因码；{@code @Size} 按越界方向分 {@code tooLong}/{@code tooSmall}。 */
  public static String of(ConstraintViolation<?> violation) {
    String constraint =
        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
    if ("Size".equals(constraint)) {
      return sizeReason(violation);
    }
    return ofConstraint(constraint);
  }

  /**
   * 约束注解简单名 → 原因码（{@code Size} 无方向信息时走不到这里，见 {@link #of}）。
   * 非约束码（如绑定失败的 {@code typeMismatch}）落 {@code invalid}。
   */
  public static String ofConstraint(String constraint) {
    return switch (constraint) {
      case "NotNull", "NotBlank", "NotEmpty" -> REQUIRED;
      case "Min", "DecimalMin", "Positive", "PositiveOrZero" -> TOO_SMALL;
      case "Max", "DecimalMax", "Negative", "NegativeOrZero" -> TOO_LARGE;
      case "Pattern", "Email" -> PATTERN;
      default -> INVALID;
    };
  }

  /** {@code @Size} 越界方向：失效值实际长度/元素数与约束的 min/max 比。 */
  private static String sizeReason(ConstraintViolation<?> violation) {
    int actual = sizeOf(violation.getInvalidValue());
    if (actual < 0) {
      return INVALID;
    }
    Map<String, Object> attributes = violation.getConstraintDescriptor().getAttributes();
    int min = ((Number) attributes.getOrDefault("min", 0)).intValue();
    int max = ((Number) attributes.getOrDefault("max", Integer.MAX_VALUE)).intValue();
    if (actual > max) {
      return TOO_LONG;
    }
    return actual < min ? TOO_SMALL : INVALID;
  }

  /** 可度量长度的值：字符串、集合、Map、数组；其余返回 -1（落 {@code invalid}）。 */
  private static int sizeOf(Object value) {
    if (value instanceof CharSequence text) {
      return text.length();
    }
    if (value instanceof Collection<?> items) {
      return items.size();
    }
    if (value instanceof Map<?, ?> map) {
      return map.size();
    }
    if (value != null && value.getClass().isArray()) {
      return Array.getLength(value);
    }
    return -1;
  }
}
