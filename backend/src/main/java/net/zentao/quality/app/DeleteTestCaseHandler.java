package net.zentao.quality.app;

import java.time.Instant;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.quality.domain.TestCase;
import net.zentao.quality.domain.TestCaseRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 软删用例（A-07，quality 卡 §5 deleteTestCase）：test_run_case 历史行保留；删后详情 40401。 */
@Component
public class DeleteTestCaseHandler {

  private final TestCaseRepository repository;
  private final ProductApi productApi;
  private final ActivityRecorder activityRecorder;

  public DeleteTestCaseHandler(TestCaseRepository repository, ProductApi productApi,
      ActivityRecorder activityRecorder) {
    this.repository = repository;
    this.productApi = productApi;
    this.activityRecorder = activityRecorder;
  }

  @Transactional
  public void handle(SessionPrincipal actor, long caseId) {
    TestCase testCase = TestCaseActionSupport.require(actor, repository, productApi, caseId);
    repository.softDelete(testCase.id(), actor.account(), Instant.now());
    activityRecorder.record(actor.account(), "testCase", testCase.id(), "deleted", null, null);
  }
}
