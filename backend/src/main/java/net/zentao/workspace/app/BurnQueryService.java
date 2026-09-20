package net.zentao.workspace.app;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.api.ExecutionApi;
import net.zentao.project.api.ProjectView;
import net.zentao.task.api.TaskApi;
import net.zentao.task.api.TaskSummaryView;
import net.zentao.workspace.api.BurnReport;
import net.zentao.workspace.domain.Burn;
import net.zentao.workspace.domain.BurnRepository;
import net.zentao.workspace.domain.BurnSeries;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 燃尽报表（workspace 卡 §3.3/§5）：当日快照缺失时逐任务懒算落库（同日 upsert 幂等），
 * ideal 为直线、remaining 取执行级日行（缺失日沿用上值）。数据权限随执行可见性（40302）。
 */
@Component
public class BurnQueryService {

  private final BurnRepository repository;
  private final TaskApi taskApi;
  private final ExecutionApi executionApi;

  public BurnQueryService(BurnRepository repository, TaskApi taskApi, ExecutionApi executionApi) {
    this.repository = repository;
    this.taskApi = taskApi;
    this.executionApi = executionApi;
  }

  @Transactional
  public BurnReport report(SessionPrincipal principal, long executionId) {
    ProjectView execution = executionApi.requireExecution(principal, executionId);
    LocalDate today = LocalDate.now();
    if (repository.findByExecutionAndDate(executionId, today).isEmpty()) {
      snapshot(executionId, today, execution.beginDate() == null ? today : execution.beginDate());
    }
    List<Burn> rows = repository.findByExecution(executionId);
    LocalDate beginDate = execution.beginDate() == null ? today : execution.beginDate();
    LocalDate endDate = execution.endDate() == null ? today : execution.endDate();
    List<LocalDate> dates = BurnSeries.dates(beginDate, endDate);

    Map<LocalDate, BigDecimal> dailyLeft = new LinkedHashMap<>();
    rows.stream().filter(row -> row.taskId() == 0)
        .forEach(row -> dailyLeft.put(row.burnDate(), row.leftHours()));
    BigDecimal startLeft = dailyLeft.values().stream().filter(Objects::nonNull).findFirst()
        .orElseGet(() -> currentLeft(rows));

    return new BurnReport(beginDate, endDate, dates, BurnSeries.ideal(startLeft, dates.size()),
        BurnSeries.remaining(dates, dailyLeft, startLeft));
  }

  /** 当日逐任务日行 + 执行级汇总行（task_id=0）；执行首日无行时以当日值补记基线。 */
  private void snapshot(long executionId, LocalDate date, LocalDate baselineDate) {
    List<TaskSummaryView> tasks = taskApi.activeByExecution(executionId);
    BigDecimal estimate = BigDecimal.ZERO;
    BigDecimal consumed = BigDecimal.ZERO;
    BigDecimal left = BigDecimal.ZERO;
    for (TaskSummaryView task : tasks) {
      BigDecimal taskEstimate = zeroIfNull(task.estimateHours());
      BigDecimal taskConsumed = zeroIfNull(task.consumedHours());
      BigDecimal taskLeft = zeroIfNull(task.leftHours());
      repository.upsert(new Burn(0, executionId, date, task.id(), taskEstimate, taskConsumed, taskLeft,
          BigDecimal.ZERO));
      estimate = estimate.add(taskEstimate);
      consumed = consumed.add(taskConsumed);
      left = left.add(taskLeft);
    }
    repository.upsert(Burn.summary(executionId, date, estimate, consumed, left, BigDecimal.ZERO));
    // ponytail: 首日基线 = 首次访问时以当日值补记（旧 fixFirst 语义），非真实历史快照
    if (!baselineDate.equals(date) && repository.findByExecutionAndDate(executionId, baselineDate).isEmpty()) {
      for (Burn row : repository.findByExecutionAndDate(executionId, date)) {
        repository.upsert(new Burn(0, executionId, baselineDate, row.taskId(), row.estimateHours(),
            row.consumedHours(), row.leftHours(), row.storyPoint()));
      }
    }
  }

  private static BigDecimal currentLeft(List<Burn> rows) {
    return rows.stream().filter(row -> row.taskId() != 0).map(Burn::leftHours).filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static BigDecimal zeroIfNull(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
