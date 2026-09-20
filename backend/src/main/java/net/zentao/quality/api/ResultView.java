package net.zentao.quality.api;

import java.time.Instant;
import net.zentao.quality.domain.Result;
import io.swagger.v3.oas.annotations.media.Schema;

/** 执行结果视图（quality 卡 §3.5；清单与单条登记共用，用例摘要批量 IN 联结）。 */
public record ResultView(
    long id,
    long testRunId,
    long testCaseId,
    int version,
    String assignee,
    @Schema(allowableValues = {"blocked", "fail", "n/a", "pass"}) String result,
    String runner,
    Instant runAt,
    String caseTitle,
    Integer casePriority) {

  public static ResultView of(Result result) {
    return of(result, null, null);
  }

  public static ResultView of(Result result, String caseTitle, Integer casePriority) {
    return new ResultView(
        result.id(),
        result.testRunId(),
        result.testCaseId(),
        result.version(),
        result.assignee(),
        result.result(),
        result.runner(),
        result.runAt(),
        caseTitle,
        casePriority);
  }
}
