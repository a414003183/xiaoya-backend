package net.zentao.quality.app;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.zentao.platform.meta.FieldDefValidator;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.quality.api.TestCaseView;
import net.zentao.quality.domain.TestCase;
import net.zentao.quality.domain.TestCaseRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建用例（quality 卡 §3.2/§5）：产品面 {@code productId≠0/libraryId=0}，库内面 {@code productId=0}；
 * needReview=true 进 wait，否则 normal；步骤 sort 缺省按行号。
 */
@Component
public class CreateTestCaseHandler {

  private final TestCaseRepository repository;
  private final ActivityRecorder activityRecorder;
  private final FieldDefValidator fieldDefValidator;

  public CreateTestCaseHandler(TestCaseRepository repository, ActivityRecorder activityRecorder,
      FieldDefValidator fieldDefValidator) {
    this.fieldDefValidator = fieldDefValidator;
    this.repository = repository;
    this.activityRecorder = activityRecorder;
  }

  public record TestCaseCreateRequest(
      Long branchId, Long categoryId, Long storyId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title, String precondition, String keywords,
      Integer priority,
      @Schema(allowableValues = {"config", "feature", "install", "interface", "other", "performance",
          "security", "unit"}) String type,
      List<String> stage, List<TestCaseFields.StepInput> steps, Boolean needReview,
      Map<String, Object> customFields) {}

  @Transactional
  public TestCaseView handle(SessionPrincipal actor, long productId, long libraryId,
      TestCaseCreateRequest command) {
    if (command.title() == null || command.title().trim().isEmpty()) {
      throw ApiException.validation(Map.of("title", "required"));
    }
    TestCaseFields.validate(command.title(), command.keywords(), command.priority(), command.type(),
        command.stage(), command.steps());
    boolean needReview = command.needReview() != null && command.needReview();
    fieldDefValidator.validate("testCase", command.customFields() == null ? java.util.Map.of() : command.customFields(), true);
    TestCase testCase = repository.insert(new TestCase(
        0,
        productId,
        command.branchId() == null ? 0 : command.branchId(),
        libraryId,
        command.categoryId() == null ? 0 : command.categoryId(),
        command.storyId() == null || command.storyId() == 0 ? null : command.storyId(),
        command.title().trim(),
        command.precondition(),
        command.keywords(),
        command.priority() == null ? 3 : command.priority(),
        command.type() == null ? "feature" : command.type(),
        command.stage(),
        needReview ? "wait" : "normal",
        TestCaseFields.normalizeSteps(command.steps()),
        null,
        null,
        null,
        null,
        null,
        null,
        1,
        command.customFields(),
        actor.account(),
        Instant.now(),
        null,
        null,
        0));
    activityRecorder.record(actor.account(), "testCase", testCase.id(), "created", null, null);
    return TestCaseView.of(testCase);
  }
}
