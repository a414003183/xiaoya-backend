package net.zentao.quality.app;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.project.api.ExecutionApi;
import net.zentao.project.api.ProjectView;
import net.zentao.quality.api.ReportView;
import net.zentao.quality.domain.Report;
import net.zentao.quality.domain.ReportRepository;
import net.zentao.quality.domain.TestRun;
import net.zentao.quality.domain.TestRunRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 测试报告命令（quality 卡 §3.6/§5）：创建同事务回填各 TestRun.reportId；
 * executionId/projectId/productId 不可改（出现即 40001）；testRunIds 变更时重指回填。
 * productId 冗余口径 = 首个关联测试单的产品（无单为 0）——STATE 决策登记。
 */
@Component
public class ReportHandlers {

  private final ReportRepository repository;
  private final TestRunRepository runRepository;
  private final ProductApi productApi;
  private final ExecutionApi executionApi;
  private final AccountApi accountApi;
  private final ActivityRecorder activityRecorder;

  public ReportHandlers(ReportRepository repository, TestRunRepository runRepository, ProductApi productApi,
      ExecutionApi executionApi, AccountApi accountApi, ActivityRecorder activityRecorder) {
    this.repository = repository;
    this.runRepository = runRepository;
    this.productApi = productApi;
    this.executionApi = executionApi;
    this.accountApi = accountApi;
    this.activityRecorder = activityRecorder;
  }

  public record ReportCreateRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title, List<Long> testRunIds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate beginDate,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate endDate, String owner, String content) {}

  /**
   * PATCH 请求体（contract：ReportUpdateRequest）。executionId/projectId/productId 仅作 40001 检测载体，
   * {@code @Schema(hidden)} 使其不出现在 springdoc 导出（契约无此三字段）。
   */
  public record ReportUpdateRequest(
      String title, List<Long> testRunIds, LocalDate beginDate, LocalDate endDate, String owner, String content,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion,
      @io.swagger.v3.oas.annotations.media.Schema(hidden = true) Long executionId,
      @io.swagger.v3.oas.annotations.media.Schema(hidden = true) Long projectId,
      @io.swagger.v3.oas.annotations.media.Schema(hidden = true) Long productId) {

    public boolean touchesImmutable() {
      return executionId != null || projectId != null || productId != null;
    }
  }

  Report require(SessionPrincipal actor, long reportId) {
    Report report = repository.findActiveById(reportId).orElseThrow(() -> ApiException.notFound("测试报告"));
    if (!productApi.canAccess(actor, report.productId())) {
      throw ApiException.dataForbidden("无权访问该测试报告。");
    }
    return report;
  }

  @Transactional
  public ReportView create(SessionPrincipal actor, long executionId, ReportCreateRequest command) {
    ProjectView execution = executionApi.requireExecution(actor, executionId);
    validate(command.title(), command.beginDate(), command.endDate(), command.owner());
    List<Long> runIds = command.testRunIds() == null ? List.of() : command.testRunIds();
    List<TestRun> runs = validateRuns(executionId, runIds);
    long productId = runs.isEmpty() ? 0 : runs.get(0).productId();
    Report report = repository.insert(new Report(
        0,
        executionId,
        execution.parentId(),
        productId,
        command.title().trim(),
        runIds,
        command.beginDate(),
        command.endDate(),
        command.owner(),
        command.content(),
        actor.account(),
        Instant.now(),
        null,
        null,
        0));
    backfill(report, runs, actor);
    return ReportView.of(report);
  }

  @Transactional
  public ReportView update(SessionPrincipal actor, long reportId, ReportUpdateRequest command) {
    Report report = require(actor, reportId);
    if (command.lockVersion() == null || command.lockVersion() != report.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新后重试。");
    }
    validate(command.title(),
        command.beginDate() == null ? report.beginDate() : command.beginDate(),
        command.endDate() == null ? report.endDate() : command.endDate(),
        command.owner());
    // 守卫先行（P2 ㊷ 约定）：testRunIds 越执行 → 42201 必须先于任何写库
    List<Long> targetRunIds = command.testRunIds() == null ? report.testRunIds() : command.testRunIds();
    List<TestRun> runs = validateRuns(report.executionId(), targetRunIds);
    report.update(command.title(), command.testRunIds(), command.beginDate(), command.endDate(),
        command.owner(), command.content());
    report.markUpdatedBy(actor.account());
    Report saved = repository.update(report)
        .orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
    // testRunIds 变更 → 重指回填（旧集合里不再包含的清空，新集合落 reportId）
    for (TestRun run : runRepository.findActiveByExecution(saved.executionId())) {
      if (saved.testRunIds().contains(run.id())) {
        continue;
      }
      if (run.reportId() != null && run.reportId() == saved.id()) {
        run.attachReport(0);
        run.markUpdatedBy(actor.account());
        runRepository.update(run);
      }
    }
    backfill(saved, runs, actor);
    return ReportView.of(saved);
  }

  private void backfill(Report report, List<TestRun> runs, SessionPrincipal actor) {
    for (TestRun run : runs) {
      run.attachReport(report.id());
      run.markUpdatedBy(actor.account());
      runRepository.update(run);
    }
  }

  /** 软删报告（A-07）：引用本报告的 test_run.reportId 清空回写（复用 update 的重指/清空口径）；删后详情 40401。 */
  @Transactional
  public void delete(SessionPrincipal actor, long reportId) {
    Report report = require(actor, reportId);
    repository.softDelete(report.id(), actor.account(), Instant.now());
    for (TestRun run : runRepository.findActiveByExecution(report.executionId())) {
      if (run.reportId() != null && run.reportId() == report.id()) {
        run.attachReport(0);
        run.markUpdatedBy(actor.account());
        runRepository.update(run);
      }
    }
    activityRecorder.record(actor.account(), "report", report.id(), "deleted", null, null);
  }

  /** 关联测试单须属于该执行（§3.6 testRunIds 校验）。 */
  private List<TestRun> validateRuns(long executionId, List<Long> runIds) {
    if (runIds.isEmpty()) {
      return List.of();
    }
    List<TestRun> executionRuns = runRepository.findActiveByExecution(executionId);
    List<TestRun> selected = new ArrayList<>();
    for (Long runId : runIds.stream().distinct().toList()) {
      TestRun run = executionRuns.stream().filter(candidate -> candidate.id() == runId).findFirst().orElse(null);
      if (run == null) {
        throw ApiException.validation(Map.of("testRunIds", "notInExecution"));
      }
      selected.add(run);
    }
    return selected;
  }

  private void validate(String title, LocalDate beginDate, LocalDate endDate, String owner) {
    Map<String, String> errors = new java.util.LinkedHashMap<>();
    if (title != null) {
      String trimmed = title.trim();
      if (trimmed.isEmpty()) {
        errors.put("title", "required");
      } else if (trimmed.length() > 255) {
        errors.put("title", "maxLength");
      }
    }
    if (beginDate != null && endDate != null && endDate.isBefore(beginDate)) {
      errors.put("endDate", "beforeBegin");
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    if (owner != null && !accountApi.missingAccounts(List.of(owner)).isEmpty()) {
      throw ApiException.validation(Map.of("owner", "notFound"));
    }
  }
}
