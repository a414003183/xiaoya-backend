package net.zentao.platform.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T63 原因词表映射（BE-09）：jakarta 约束 → 原因码逐条锁死——信封 `fields` 的值是机器可判的词表码，
 * 不是注解默认文案。新增约束必须先在 {@link FieldReasons} 登记，这里补一条用例。
 */
class FieldReasonsTest {

  record Sample(
      @NotBlank String name,
      @Size(max = 3) String code,
      @Size(min = 2) String tag,
      @Min(1) Integer low,
      @Max(4) Integer high,
      @Pattern(regexp = "^[a-z]+$") String slug,
      @Email String email,
      @AssertTrue boolean flag) {}

  private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("jakarta 约束族 → 词表原因码逐条对映")
  void constraintFamilyMapsToVocabulary() {
    Map<String, String> reasons = new HashMap<>();
    for (ConstraintViolation<Sample> violation : VALIDATOR.validate(
        new Sample(null, "abcd", "a", 0, 9, "A1", "nope", false))) {
      reasons.put(violation.getPropertyPath().toString(), FieldReasons.of(violation));
    }
    assertEquals("required", reasons.get("name"));
    assertEquals("tooLong", reasons.get("code"));
    assertEquals("tooSmall", reasons.get("tag"));
    assertEquals("tooSmall", reasons.get("low"));
    assertEquals("tooLarge", reasons.get("high"));
    assertEquals("pattern", reasons.get("slug"));
    assertEquals("pattern", reasons.get("email"));
    assertEquals("invalid", reasons.get("flag"));
  }

  @Test
  @DisplayName("null/空值不触发长度与格式约束（与手写守卫的 null=不改口径一致）")
  void nullableFieldsPassSizeAndPattern() {
    assertEquals(0, VALIDATOR.validate(new Sample("ok", null, null, null, null, null, null, true)).size());
  }

  @Test
  @DisplayName("无方向/未知约束 → invalid；词表七词各就各位")
  void fallbackAndVocabulary() {
    assertEquals(FieldReasons.INVALID, FieldReasons.ofConstraint("Size"), "@Size 无越界方向时的兜底");
    assertEquals(FieldReasons.INVALID, FieldReasons.ofConstraint("typeMismatch"), "绑定失败不是约束违例");
    assertEquals(FieldReasons.INVALID, FieldReasons.ofConstraint("AssertTrue"));
    assertEquals("required", FieldReasons.ofConstraint("NotBlank"));
    assertEquals("duplicate", FieldReasons.DUPLICATE);
  }
}
