package net.zentao.quality.app;

import java.util.Set;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.api.TestCaseView;
import net.zentao.quality.domain.TestCaseRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评审用例（quality 卡 §4.2）：仅 wait（否则 42202 由状态机裁决）；result=pass → normal 且
 * reviewers 追加当前人、reviewedAt 落；clarify 保持 wait。
 */
@Component
public class ReviewTestCaseHandler {

  private final TestCaseRepository repository;
  private final ProductApi productApi;
  private final WorkflowEngine engine;

  public ReviewTestCaseHandler(TestCaseRepository repository, ProductApi productApi, WorkflowEngine engine) {
    this.repository = repository;
    this.productApi = productApi;
    this.engine = engine;
  }

  public record TestCaseReviewRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"clarify", "pass"}) String result,
      String comment) {}

  @Transactional
  public TestCaseView handle(SessionPrincipal actor, long caseId, TestCaseReviewRequest command) {
    if (command.result() == null || !Set.of("pass", "clarify").contains(command.result())) {
      throw ApiException.validation(java.util.Map.of("result", "invalid"));
    }
    return TestCaseActionSupport.fire(actor, repository, productApi, engine, caseId, "review",
        command.comment(), testCase -> {
          testCase.reviewResult(command.result());
          if ("pass".equals(command.result())) {
            testCase.reviewedBy(actor.account());
          }
        });
  }
}
