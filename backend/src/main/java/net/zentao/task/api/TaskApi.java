package net.zentao.task.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.zentao.platform.session.SessionPrincipal;

/** 任务域对外接口（A2：workspace 域待办 objectTitle、我的聚合、周报 EVM 与燃尽取数）。 */
public interface TaskApi {

  /** 标题联查（workspace 卡 §3.1 objectTitle 现算）：id → 标题，缺失/已删的 id 不出现在结果中。 */
  Map<Long, String> titlesByIds(List<Long> ids);

  /**
   * 按角色字段分页（workspace 卡 §3.4 /my/tasks）：等价于执行域列表加 {@code filters[<roleField>]=@me}，
   * 目标域全部 filterable/sortable 与 DataScope 原样生效；roleField 取值由调用方（workspace）按卡映射。
   */
  TaskList pageByRole(SessionPrincipal principal, String roleField, Map<String, String[]> params);

  /** 执行下全部未删任务（燃尽逐任务行与报表取数）。 */
  List<TaskSummaryView> activeByExecution(long executionId);

  /** EVM 事实行（workspace 卡 §3.2：项目下全部未删执行的非父任务，排除 cancel 在计算侧）。 */
  record EvmTask(
      long id,
      String type,
      String status,
      BigDecimal estimateHours,
      BigDecimal consumedHours,
      BigDecimal leftHours,
      LocalDate beginDate,
      LocalDate endDate) {}

  List<EvmTask> evmTasks(long projectId);

  /** 删除守卫（A-07，2026-09-19）：该需求下是否存在未删任务（story 删除前 42203 判定）。 */
  boolean hasActiveTasksByStory(long storyId);

  /** 删除守卫（A-07，2026-09-19）：该执行下是否存在未删任务（execution 删除前 42203 判定）。 */
  boolean hasActiveTasksByExecution(long executionId);

  /** 周报取数（workspace 卡 §3.2）：工时聚合 + 三张任务表。 */
  record WeeklyFacts(
      BigDecimal consumedUntil,
      BigDecimal consumedThisWeek,
      int staffThisWeek,
      Map<String, BigDecimal> workload,
      List<TaskSummaryView> finished,
      List<TaskSummaryView> postponed,
      List<TaskSummaryView> nextWeek) {}

  WeeklyFacts weeklyFacts(long projectId, LocalDate weekStart, LocalDate weekEnd);

  /** 在办任务数（org 卡 §5 Personnel 节口径：assignee 且 status ∉ done,closed,cancel）。 */
  Map<String, Long> openTaskCounts(List<String> accounts);

  /** 工作量聚合行（effort.workDate ∈ [from,to] 按 assignee；finishedTaskCount = 区间内完成的任务数）。 */
  record AssigneeWorkload(String account, BigDecimal consumedHours, long finishedTaskCount) {}

  List<AssigneeWorkload> workload(LocalDate from, LocalDate to, List<String> accounts);
}
