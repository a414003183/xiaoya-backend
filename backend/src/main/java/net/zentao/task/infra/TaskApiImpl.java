package net.zentao.task.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.task.api.TaskApi;
import net.zentao.task.api.TaskList;
import net.zentao.task.api.TaskSummaryView;
import net.zentao.task.app.TaskQueryService;
import net.zentao.task.domain.Effort;
import net.zentao.task.domain.EffortRepository;
import net.zentao.task.domain.Task;
import net.zentao.task.domain.TaskRepository;
import org.springframework.stereotype.Component;

/** TaskApi 真实现（A2 跨域只读：workspace 聚合/周报/燃尽消费面）。 */
@Component
public class TaskApiImpl implements TaskApi {

  private static final Set<String> TERMINAL_STATUSES = Set.of("done", "closed", "cancel");
  private static final List<String> OPEN_STATUSES = List.of("wait", "doing", "pause");

  /** ponytail: 跨域聚合的内存上限（账号集有界）；超限请改 SQL 聚合。 */
  private static final int AGGREGATE_CAP = 10_000;

  private final TaskRepository repository;
  private final EffortRepository effortRepository;
  private final TaskQueryService queryService;

  public TaskApiImpl(TaskRepository repository, EffortRepository effortRepository, TaskQueryService queryService) {
    this.repository = repository;
    this.effortRepository = effortRepository;
    this.queryService = queryService;
  }

  @Override
  public Map<Long, String> titlesByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    Map<Long, String> titles = new LinkedHashMap<>();
    for (Task task : repository.findActiveByIds(ids.stream().distinct().toList())) {
      titles.put(task.id(), task.title());
    }
    return titles;
  }

  @Override
  public TaskList pageByRole(SessionPrincipal principal, String roleField, Map<String, String[]> params) {
    return queryService.pageAll(principal, Filters.withFilter(params, roleField, "@me"));
  }

  @Override
  public List<TaskSummaryView> activeByExecution(long executionId) {
    return repository.findActiveByExecution(executionId).stream().map(TaskSummaryView::of).toList();
  }

  @Override
  public boolean hasActiveTasksByStory(long storyId) {
    return !repository.findActiveByStory(storyId).isEmpty();
  }

  @Override
  public boolean hasActiveTasksByExecution(long executionId) {
    return !repository.findActiveByExecution(executionId).isEmpty();
  }

  @Override
  public List<EvmTask> evmTasks(long projectId) {
    return activeTasks(projectId).stream()
        .map(task -> new EvmTask(task.id(), task.type(), task.status(), task.estimateHours(),
            task.consumedHours(), task.leftHours(), task.estStartedDate(), task.deadline()))
        .toList();
  }

  @Override
  public WeeklyFacts weeklyFacts(long projectId, LocalDate weekStart, LocalDate weekEnd) {
    List<Task> tasks = activeTasks(projectId);
    List<Effort> efforts = effortRepository.findActiveByProject(projectId);

    BigDecimal consumedUntil = sum(efforts.stream()
        .filter(effort -> withinUntil(effort.workDate(), weekEnd)).toList());
    List<Effort> weekEfforts = efforts.stream().filter(effort -> within(effort.workDate(), weekStart, weekEnd)).toList();
    Set<String> staff = new LinkedHashSet<>();
    weekEfforts.forEach(effort -> staff.add(effort.account()));

    return new WeeklyFacts(
        consumedUntil,
        sum(weekEfforts),
        staff.size(),
        workload(tasks, weekStart, weekEnd),
        tasks.stream().filter(task -> finishedWithin(task, weekStart, weekEnd)).map(TaskSummaryView::of).toList(),
        tasks.stream().filter(task -> postponed(task, weekStart, weekEnd)).map(TaskSummaryView::of).toList(),
        tasks.stream().filter(task -> plannedNextWeek(task, weekStart, weekEnd)).map(TaskSummaryView::of).toList());
  }

  @Override
  public Map<String, Long> openTaskCounts(List<String> accounts) {
    if (accounts == null || accounts.isEmpty()) {
      return Map.of();
    }
    Map<String, Long> counts = new LinkedHashMap<>();
    QueryWrapper query = QueryWrapper.create().where(new QueryColumn("deleted_at").isNull()
        .and(new QueryColumn("assignee").in(new ArrayList<Object>(accounts)))
        .and(new QueryColumn("status").in(new ArrayList<Object>(OPEN_STATUSES))));
    // ponytail: 账号集有界，内存聚合；升级路径 = SQL GROUP BY assignee
    for (Task task : repository.queryPage(query, 0, AGGREGATE_CAP)) {
      counts.merge(task.assignee(), 1L, Long::sum);
    }
    return counts;
  }

  @Override
  public List<AssigneeWorkload> workload(LocalDate from, LocalDate to, List<String> accounts) {
    if (accounts == null || accounts.isEmpty()) {
      return List.of();
    }
    QueryWrapper effortQuery = QueryWrapper.create().where(new QueryColumn("deleted_at").isNull()
        .and(new QueryColumn("account").in(new ArrayList<Object>(accounts)))
        .and(new QueryColumn("work_date").ge(from))
        .and(new QueryColumn("work_date").le(to)));
    Map<String, BigDecimal> hours = new LinkedHashMap<>();
    // ponytail: 同 openTaskCounts，内存聚合（升级路径 = SQL SUM/GROUP BY）
    for (Effort effort : effortRepository.queryPage(effortQuery, 0, AGGREGATE_CAP)) {
      BigDecimal consumed = effort.consumedHours() == null ? BigDecimal.ZERO : effort.consumedHours();
      hours.merge(effort.account(), consumed, BigDecimal::add);
    }

    QueryWrapper finishedQuery = QueryWrapper.create().where(new QueryColumn("deleted_at").isNull()
        .and(new QueryColumn("finished_by").in(new ArrayList<Object>(accounts)))
        .and(new QueryColumn("finished_at").ge(from.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()))
        .and(new QueryColumn("finished_at")
            .lt(to.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())));
    Map<String, Long> finished = new LinkedHashMap<>();
    for (Task task : repository.queryPage(finishedQuery, 0, AGGREGATE_CAP)) {
      finished.merge(task.finishedBy(), 1L, Long::sum);
    }

    return accounts.stream()
        .map(account -> new AssigneeWorkload(account, hours.getOrDefault(account, BigDecimal.ZERO),
            finished.getOrDefault(account, 0L)))
        .toList();
  }

  /** 统计范围：项目下未删执行的非父任务（排除 cancel 由 EVM 计算侧承担，此处只剔父任务）。 */  private List<Task> activeTasks(long projectId) {
    return repository.findActiveByProject(projectId).stream().filter(task -> !task.isParent()).toList();
  }

  /** workload：与本周区间重叠的任务按类型汇总预计工时（workspace 卡 §3.2 workload 口径）。 */
  private static Map<String, BigDecimal> workload(List<Task> tasks, LocalDate weekStart, LocalDate weekEnd) {
    Map<String, BigDecimal> result = new LinkedHashMap<>();
    for (Task task : tasks) {
      if (task.estStartedDate() == null || task.deadline() == null
          || task.estStartedDate().isAfter(weekEnd) || task.deadline().isBefore(weekStart)) {
        continue;
      }
      BigDecimal estimate = task.estimateHours() == null ? BigDecimal.ZERO : task.estimateHours();
      result.merge(task.type() == null ? "misc" : task.type(), estimate, BigDecimal::add);
    }
    return result;
  }

  private static boolean finishedWithin(Task task, LocalDate weekStart, LocalDate weekEnd) {
    if (task.finishedAt() == null) {
      return false;
    }
    LocalDate finished = LocalDate.ofInstant(task.finishedAt(), java.time.ZoneId.systemDefault());
    return !finished.isBefore(weekStart) && !finished.isAfter(weekEnd);
  }

  /** postponed：本周应完成（deadline 落在本周）但未完成且仍有剩余工时。 */
  private static boolean postponed(Task task, LocalDate weekStart, LocalDate weekEnd) {
    if (TERMINAL_STATUSES.contains(task.status()) || task.deadline() == null) {
      return false;
    }
    if (task.deadline().isBefore(weekStart) || task.deadline().isAfter(weekEnd)) {
      return false;
    }
    return task.leftHours() != null && task.leftHours().signum() > 0;
  }

  /** nextWeek：下周计划开始的任务（estStartedDate 落在下周且未完成）。 */
  private static boolean plannedNextWeek(Task task, LocalDate weekStart, LocalDate weekEnd) {
    if (TERMINAL_STATUSES.contains(task.status()) || task.estStartedDate() == null) {
      return false;
    }
    LocalDate nextStart = weekEnd.plusDays(1);
    LocalDate nextEnd = weekEnd.plusDays(7);
    return !task.estStartedDate().isBefore(nextStart) && !task.estStartedDate().isAfter(nextEnd);
  }

  private static boolean within(LocalDate date, LocalDate from, LocalDate to) {
    return date != null && !date.isBefore(from) && !date.isAfter(to);
  }

  private static boolean withinUntil(LocalDate date, LocalDate until) {
    return date != null && !date.isAfter(until);
  }

  private static BigDecimal sum(List<Effort> efforts) {
    return efforts.stream().map(Effort::consumedHours).filter(Objects::nonNull).reduce(BigDecimal.ZERO,
        BigDecimal::add);
  }
}
