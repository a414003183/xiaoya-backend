package net.zentao.quality.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.api.BugView;
import net.zentao.quality.domain.Bug;
import net.zentao.quality.domain.BugRepository;
import net.zentao.quality.domain.TestCaseRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 部分更新 Bug（quality 卡 §5 PATCH 白名单；lockVersion 不符 → 40901）。 */
@Component
public class UpdateBugHandler {

  private final BugRepository repository;
  private final TestCaseRepository caseRepository;
  private final ProductApi productApi;
  private final AccountApi accountApi;

  public UpdateBugHandler(BugRepository repository, TestCaseRepository caseRepository, ProductApi productApi,
      AccountApi accountApi) {
    this.repository = repository;
    this.caseRepository = caseRepository;
    this.productApi = productApi;
    this.accountApi = accountApi;
  }

  public record BugUpdateRequest(
      String title, String keywords, Integer severity, Integer priority,
      @Schema(allowableValues = {"automation", "codeerror", "config", "designdefect", "install", "others",
          "performance", "security", "standard"}) String type,
      String os, String browser,
      String steps, String openedBuilds, Long categoryId, Long executionId, Long planId, Long storyId,
      Long taskId, Long testCaseId, LocalDate deadline, List<Long> relatedBugIds, List<String> notifyAccounts,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  @Transactional
  public BugView handle(SessionPrincipal actor, long bugId, BugUpdateRequest command) {
    Bug bug = BugActionSupport.require(actor, repository, productApi, bugId);
    if (command.lockVersion() == null || command.lockVersion() != bug.lockVersion()) {
      throw ApiException.lockConflict();
    }
    BugFields.validate(command.title(), command.keywords(), command.severity(), command.priority(),
        command.type(), command.os(), command.browser(), command.openedBuilds(), null, null,
        command.notifyAccounts(), accountApi);
    BugFields.validateRelatedBugs(bug.productId(), command.relatedBugIds(), repository);
    validateTestCase(bug.productId(), command.testCaseId());
    bug.update(command.title(), command.keywords(), command.severity(), command.priority(), command.type(),
        command.os(), command.browser(), command.steps(), command.openedBuilds(), command.categoryId(),
        command.executionId(), command.planId(), command.storyId(), command.taskId(), command.testCaseId(),
        command.deadline(), command.relatedBugIds(), command.notifyAccounts());
    bug.markUpdatedBy(actor.account());
    return BugView.of(BugActionSupport.save(repository, bug));
  }

  /** 来源用例（B-QUA-01）：非 0 时须为同产品未删产品面用例（库用例不可挂 Bug）。 */
  private void validateTestCase(long productId, Long testCaseId) {
    if (testCaseId == null || testCaseId == 0) {
      return;
    }
    boolean sameProduct = caseRepository.findActiveByIds(List.of(testCaseId)).stream()
        .anyMatch(testCase -> testCase.productId() == productId && testCase.libraryId() == 0);
    if (!sameProduct) {
      throw ApiException.validation(Map.of("testCaseId", "crossProduct"));
    }
  }
}
