package net.zentao.quality.app;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.quality.api.ResultView;
import net.zentao.quality.domain.Result;
import net.zentao.quality.domain.ResultRepository;
import net.zentao.quality.domain.TestCase;
import net.zentao.quality.domain.TestCaseRepository;
import net.zentao.quality.domain.TestRun;
import net.zentao.quality.domain.TestRunRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登记执行结果（quality 卡 §4.3）：同 (testRun, case) 幂等 upsert（覆写 result/runner/runAt）；
 * 同事务同步 test_case.lastRun 三字段；测试单非 doing → 42202（状态机 from 守卫）；
 * 动态流 runCase（extra=result）经 workflow yml。
 */
@Component
public class RecordResultHandler {

  static final Set<String> RESULTS = Set.of("pass", "fail", "blocked", "n/a");

  private final TestRunRepository runRepository;
  private final ResultRepository resultRepository;
  private final TestCaseRepository caseRepository;
  private final TestRunHandlers handlers;

  public RecordResultHandler(TestRunRepository runRepository, ResultRepository resultRepository,
      TestCaseRepository caseRepository, TestRunHandlers handlers) {
    this.runRepository = runRepository;
    this.resultRepository = resultRepository;
    this.caseRepository = caseRepository;
    this.handlers = handlers;
  }

  public record RecordResultRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"blocked", "fail", "n/a",
          "pass"}) String result,
      String comment) {}

  @Transactional
  public ResultView handle(SessionPrincipal actor, long testRunId, long caseId, RecordResultRequest command) {
    if (command.result() == null || !RESULTS.contains(command.result())) {
      throw ApiException.validation(Map.of("result", "invalid"));
    }
    TestRun run = handlers.require(actor, testRunId);
    TestCase testCase = caseRepository.findActiveByIds(List.of(caseId)).stream().findFirst()
        .orElseThrow(() -> ApiException.validation(Map.of("caseId", "notFound")));
    if (testCase.productId() != run.productId() || testCase.libraryId() != 0) {
      throw ApiException.validation(Map.of("caseId", "crossProduct"));
    }

    Instant now = Instant.now();
    // 状态机裁决（doing 守卫 + runCase 动态流 extra=result）
    handlers.fireResult(actor, run, command.comment(), command.result());
    Result result = resultRepository.find(testRunId, caseId).orElseGet(() -> new Result(
        0, testRunId, caseId, 1, null, null, null, null));
    result.record(command.result(), actor.account(), now);
    Result saved = result.id() == 0 ? resultRepository.insert(result) : upserted(result);
    // 用例 lastRun 三字段同步（§3.5 副作用）
    caseRepository.updateLastRun(List.of(caseId), command.result(), actor.account(), now);
    return ResultView.of(saved, testCase.title(), testCase.priority());
  }

  private Result upserted(Result result) {
    resultRepository.update(result);
    return result;
  }

  TestRunRepository runRepository() {
    return runRepository;
  }
}
