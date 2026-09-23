package net.zentao.quality.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.platform.error.ApiException;
import net.zentao.quality.domain.TestCase;

/** 用例字段校验（quality 卡 §3.2 校验/取值列；create 与 update 同源，null = 不改不校验）。 */
final class TestCaseFields {

  static final Set<String> TYPES =
      Set.of("unit", "interface", "feature", "install", "config", "performance", "security", "other");
  static final Set<String> STAGES =
      Set.of("unittest", "feature", "intergrate", "system", "smoke", "bvt");
  static final Set<String> REVIEW_RESULTS = Set.of("pass", "clarify");
  static final Set<String> LAST_RUN_RESULTS = Set.of("pass", "fail", "blocked", "n/a");

  private TestCaseFields() {}

  static void validate(String title, String keywords, Integer priority, String type, List<String> stage,
      List<StepInput> steps) {
    Map<String, String> errors = new LinkedHashMap<>();
    if (title != null) {
      String trimmed = title.trim();
      if (trimmed.isEmpty()) {
        errors.put("title", "required");
      } else if (trimmed.length() > 255) {
        errors.put("title", "maxLength");
      }
    }
    if (keywords != null && keywords.length() > 255) {
      errors.put("keywords", "maxLength");
    }
    if (priority != null && (priority < 1 || priority > 4)) {
      errors.put("priority", "invalidRange");
    }
    if (type != null && !TYPES.contains(type)) {
      errors.put("type", "invalid");
    }
    if (stage != null && !STAGES.containsAll(stage)) {
      errors.put("stage", "invalid");
    }
    if (steps != null) {
      if (steps.size() > TestCase.STEPS_MAX) {
        errors.put("steps", "tooMany");
      } else {
        for (int i = 0; i < steps.size(); i++) {
          StepInput step = steps.get(i);
          if (step.description() == null || step.description().isBlank()) {
            errors.put("steps[" + i + "].description", "required");
          } else if (step.description().length() > 2000) {
            errors.put("steps[" + i + "].description", "maxLength");
          }
          if (step.expects() != null && step.expects().length() > 2000) {
            errors.put("steps[" + i + "].expects", "maxLength");
          }
        }
      }
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
  }

  /** 请求体步骤入参（sort 可空 = 按行号）。 */
  record StepInput(Integer sort, String description, String expects) {}

  /**
   * 规范化步骤：sort 缺省按行号（≥1 单调递增）；**请求直给的 sort 不许重复**（BE-19）——
   * 重复落到 `uk_case_step(case_id, sort)` 只能给通用 42201（fields 空），用户看不到哪个字段错。
   *
   * ponytail: 只查「请求直给」之间的重复；null 填充值仍可能与直给值撞（如 [{sort:2},null] → [2,2]），
   * 撞了仍由全局 DuplicateKeyException 映射给通用 42201（与现状一致）。升级路径：定重排语义后把
   * 填充值与直给值放同一查重池（会改可接受输入面，需另开卡）。
   */
  static List<TestCase.Step> normalizeSteps(List<StepInput> steps) {
    if (steps == null) {
      return List.of();
    }
    java.util.ArrayList<TestCase.Step> normalized = new java.util.ArrayList<>();
    java.util.Set<Integer> requested = new java.util.HashSet<>();
    for (int i = 0; i < steps.size(); i++) {
      StepInput step = steps.get(i);
      if (step.sort() != null && !requested.add(step.sort())) {
        throw ApiException.validation(Map.of("steps", "duplicate"));
      }
      normalized.add(new TestCase.Step(step.sort() == null ? i + 1 : step.sort(),
          step.description(), step.expects()));
    }
    return List.copyOf(normalized);
  }
}
