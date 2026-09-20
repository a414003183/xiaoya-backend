package net.zentao.quality.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.zentao.quality.domain.TestCase;
import io.swagger.v3.oas.annotations.media.Schema;

/** 用例视图（quality 卡 §3.2 全字段；字段集合与 contract TestCaseView 一一对应）。 */
public record TestCaseView(
    long id,
    long productId,
    long branchId,
    long libraryId,
    long categoryId,
    Long storyId,
    String title,
    String precondition,
    String keywords,
    int priority,
    @Schema(allowableValues = {"config", "feature", "install", "interface", "other", "performance",
        "security", "unit"}) String type,
    List<String> stage,
    @Schema(allowableValues = {"blocked", "investigate", "normal", "wait"}) String status,
    List<StepView> steps,
    Long fromBugId,
    @Schema(allowableValues = {"blocked", "fail", "n/a", "pass"}) String lastRunResult,
    String lastRunner,
    Instant lastRunAt,
    List<String> reviewers,
    Instant reviewedAt,
    int version,
    Map<String, Object> customFields,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    int lockVersion) {

  public record StepView(Integer sort, String description, String expects) {

    public static StepView of(TestCase.Step step) {
      return new StepView(step.sort(), step.description(), step.expects());
    }
  }

  public static TestCaseView of(TestCase testCase) {
    return new TestCaseView(
        testCase.id(),
        testCase.productId(),
        testCase.branchId(),
        testCase.libraryId(),
        testCase.categoryId(),
        testCase.storyId(),
        testCase.title(),
        testCase.precondition(),
        testCase.keywords(),
        testCase.priority(),
        testCase.type(),
        testCase.stage(),
        testCase.status(),
        testCase.steps().stream().map(StepView::of).toList(),
        testCase.fromBugId(),
        testCase.lastRunResult(),
        testCase.lastRunner(),
        testCase.lastRunAt(),
        testCase.reviewers(),
        testCase.reviewedAt(),
        testCase.version(),
        testCase.customFields(),
        testCase.createdBy(),
        testCase.createdAt(),
        testCase.updatedBy(),
        testCase.updatedAt(),
        testCase.lockVersion());
  }
}
