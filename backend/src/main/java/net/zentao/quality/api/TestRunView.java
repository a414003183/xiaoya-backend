package net.zentao.quality.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.zentao.quality.domain.TestRun;
import io.swagger.v3.oas.annotations.media.Schema;

/** 测试单视图（quality 卡 §3.4 全字段；字段集合与 contract TestRunView 一一对应）。 */
public record TestRunView(
    long id,
    long productId,
    long projectId,
    long executionId,
    long buildId,
    String name,
    String owner,
    int priority,
    @Schema(allowableValues = {"acceptance", "integrate", "performance", "safety", "system"}) String type,
    LocalDate beginDate,
    LocalDate endDate,
    Instant realBeganAt,
    Instant realFinishedAt,
    String description,
    List<String> members,
    List<String> notifyAccounts,
    @Schema(allowableValues = {"blocked", "doing", "done", "wait"}) String status,
    Long reportId,
    Map<String, Object> customFields,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    int lockVersion) {

  public static TestRunView of(TestRun run) {
    return new TestRunView(
        run.id(),
        run.productId(),
        run.projectId(),
        run.executionId(),
        run.buildId(),
        run.name(),
        run.owner(),
        run.priority(),
        run.type(),
        run.beginDate(),
        run.endDate(),
        run.realBeganAt(),
        run.realFinishedAt(),
        run.description(),
        run.members(),
        run.notifyAccounts(),
        run.status(),
        run.reportId(),
        run.customFields(),
        run.createdBy(),
        run.createdAt(),
        run.updatedBy(),
        run.updatedAt(),
        run.lockVersion());
  }
}
