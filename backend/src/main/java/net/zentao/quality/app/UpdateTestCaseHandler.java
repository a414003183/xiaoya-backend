package net.zentao.quality.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.api.TestCaseView;
import net.zentao.quality.domain.TestCase;
import net.zentao.quality.domain.TestCaseRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 部分更新用例（quality 卡 §5）：status 仅标记态三态互转（03 §1 唯一例外，改 wait → 40001）；
 * steps 整体替换（旧行全删，新行按 sort 落）；lockVersion 不符 → 40901。
 */
@Component
public class UpdateTestCaseHandler {

  private final TestCaseRepository repository;
  private final ProductApi productApi;

  public UpdateTestCaseHandler(TestCaseRepository repository, ProductApi productApi) {
    this.repository = repository;
    this.productApi = productApi;
  }

  public record TestCaseUpdateRequest(
      String title, String precondition, String keywords, Integer priority,
      @Schema(allowableValues = {"config", "feature", "install", "interface", "other", "performance",
          "security", "unit"}) String type,
      List<String> stage,
      Long categoryId, Long storyId,
      @Schema(allowableValues = {"blocked", "investigate", "normal"}) String status,
      List<TestCaseFields.StepInput> steps,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  @Transactional
  public TestCaseView handle(SessionPrincipal actor, long caseId, TestCaseUpdateRequest command) {
    TestCase testCase = TestCaseActionSupport.require(actor, repository, productApi, caseId);
    if (command.lockVersion() == null || command.lockVersion() != testCase.lockVersion()) {
      throw ApiException.lockConflict();
    }
    if (command.status() != null) {
      try {
        testCase.requireMarkerTransition(command.status());
      } catch (IllegalArgumentException e) {
        // 领域抛的是开发串，不透出（T63 起异常文案只走语言包键）；错误码 40001 不变
        throw ApiException.keyed(ErrorCode.BAD_REQUEST, "common.message.stateActionNotAllowed");
      }
    }
    TestCaseFields.validate(command.title(), command.keywords(), command.priority(), command.type(),
        command.stage(), command.steps());
    testCase.update(command.title(), command.precondition(), command.keywords(), command.priority(),
        command.type(), command.stage(), command.categoryId(), command.storyId(), command.status(),
        command.steps() == null ? null : TestCaseFields.normalizeSteps(command.steps()));
    testCase.markUpdatedBy(actor.account());
    return TestCaseView.of(TestCaseActionSupport.save(repository, testCase));
  }
}
