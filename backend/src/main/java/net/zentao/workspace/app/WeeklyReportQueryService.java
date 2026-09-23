package net.zentao.workspace.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.filters.LikePatterns;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.api.ProjectApi;
import net.zentao.project.api.ProjectView;
import net.zentao.task.api.TaskApi;
import net.zentao.workspace.api.WeeklyReportList;
import net.zentao.workspace.api.WeeklyReportView;
import net.zentao.workspace.domain.EvmCalculator;
import net.zentao.workspace.domain.WeeklyReport;
import net.zentao.workspace.domain.WeeklyReportRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 周报读取与幂等重算（workspace 卡 §3.2/§5）：current 读取时按 EVM 口径重算并整行覆写，
 * 历史列表只读已落库快照；数据权限随项目可见性（不可见 → 40302）。
 */
@Component
public class WeeklyReportQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("weekStart"), Set.of("weekStart"), Set.of());
  private static final Map<String, String> COLUMNS = Map.of("weekStart", "week_start");

  private final WeeklyReportRepository repository;
  private final TaskApi taskApi;
  private final ProjectApi projectApi;
  private final MessageResolver messages;

  public WeeklyReportQueryService(WeeklyReportRepository repository, TaskApi taskApi, ProjectApi projectApi,
      MessageResolver messages) {
    this.repository = repository;
    this.taskApi = taskApi;
    this.projectApi = projectApi;
    this.messages = messages;
  }

  /** 指定周周报（date 传周内任意一天，缺省本周，归一到周一）。 */
  @Transactional
  public WeeklyReportView current(SessionPrincipal principal, long projectId, LocalDate date) {
    ProjectView project = projectApi.requireVisible(principal, projectId, "project");
    LocalDate weekStart = mondayOf(date == null ? LocalDate.now() : date);
    LocalDate weekEnd = weekStart.plusDays(6);

    TaskApi.WeeklyFacts facts = taskApi.weeklyFacts(projectId, weekStart, weekEnd);
    List<TaskApi.EvmTask> tasks = taskApi.evmTasks(projectId);
    BigDecimal pv = EvmCalculator.pv(tasks, weekStart, weekEnd);
    BigDecimal ev = EvmCalculator.ev(tasks);
    BigDecimal ac = facts.consumedUntil();
    BigDecimal sv = EvmCalculator.variance(ev, pv);
    BigDecimal cv = EvmCalculator.variance(ev, ac);

    WeeklyReport report = repository.find(projectId, weekStart)
        .orElseGet(() -> new WeeklyReport(0, projectId, weekStart, pv, ev, ac, sv, cv, 0, Map.of(), null));
    report.recompute(pv, ev, ac, sv, cv, facts.staffThisWeek(), facts.workload(), Instant.now());
    WeeklyReport saved = repository.upsert(report);

    return WeeklyReportView.of(saved, weekSN(project, weekStart), weekEnd, facts.finished(), facts.postponed(),
        facts.nextWeek(), messages);
  }

  /** 历史周快照列表（只读已落库快照，不触发重算）。 */
  public WeeklyReportList history(SessionPrincipal principal, long projectId, Map<String, String[]> params) {
    ProjectView project = projectApi.requireVisible(principal, projectId, "project");
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition injected = new QueryColumn("project_id").eq(projectId);
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> java.util.Optional.empty(), injected);
    List<WeeklyReport> rows = repository.queryPage(query, filters.offset(), filters.limit());
    QueryWrapper countQuery =
        FilterPredicate.compile(filters.forCount(), COLUMNS::get, value -> java.util.Optional.empty(), injected);
    List<WeeklyReportView> items = rows.stream()
        .map(row -> WeeklyReportView.of(row, weekSN(project, row.weekStart()), row.weekStart().plusDays(6),
            List.of(), List.of(), List.of(), messages))
        .toList();
    return new WeeklyReportList(items, repository.countByQuery(countQuery));
  }

  /** 任意日期 → 当周周一（周报周导航与 date 归一）。 */
  public static LocalDate mondayOf(LocalDate date) {
    LocalDate monday = date.with(DayOfWeek.MONDAY);
    return monday.isAfter(date) ? monday.minusWeeks(1) : monday;
  }

  private static int weekSN(ProjectView project, LocalDate weekStart) {
    if (project.beginDate() == null) {
      return 1;
    }
    long weeks = ChronoUnit.WEEKS.between(mondayOf(project.beginDate()), weekStart);
    return (int) Math.max(weeks, 0) + 1;
  }
}
