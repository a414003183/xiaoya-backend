package net.zentao.task.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.api.ExecutionApi;
import net.zentao.task.api.EffortView;
import net.zentao.task.domain.EffortRepository;
import net.zentao.task.domain.TaskRepository;
import org.springframework.stereotype.Component;

/**
 * 工时明细（task 卡 §3b/§5）：filters[account]/filters[workDate] 区间 + q LIKE work；
 * 默认排序 -workDate,-id（meta list.defaultSort 同源）；读不限本人，任务可见即可读（§7）。
 */
@Component
public class EffortQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("account", "workDate", "id"),
      Set.of("id", "workDate"),
      Set.of("work"));

  private static final Map<String, String> COLUMNS = Map.of(
      "account", "account",
      "workDate", "work_date",
      "id", "id",
      "work", "work");

  private final EffortRepository effortRepository;
  private final TaskRepository taskRepository;
  private final ExecutionApi executionApi;

  public EffortQueryService(EffortRepository effortRepository, TaskRepository taskRepository,
      ExecutionApi executionApi) {
    this.effortRepository = effortRepository;
    this.taskRepository = taskRepository;
    this.executionApi = executionApi;
  }

  /** EffortList 载荷（contract：items + total）。 */
  public record EffortList(List<EffortView> items, long total) {}

  public EffortList page(SessionPrincipal principal, long taskId, Map<String, String[]> params) {
    TaskGuard.requireReadable(taskRepository, executionApi, principal, taskId);
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition injected = new QueryColumn("deleted_at").isNull().and(new QueryColumn("task_id").eq(taskId));
    String q = filters.q();
    if (q != null && !q.isBlank()) {
      injected = injected.and(new QueryColumn("work").like("%" + q + "%"));
    }
    java.util.function.Function<String, Optional<String>> specials =
        value -> "@me".equals(value) ? Optional.of(principal.account()) : Optional.empty();
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, specials, injected);
    if (filters.sortKeys().isEmpty()) {
      query = query.orderBy(new QueryColumn("work_date").desc(), // banned-words-ok：MyBatis-Flex 构造器方法名
          new QueryColumn("id").desc());
    }
    List<EffortView> items = effortRepository.queryPage(query, filters.offset(), filters.limit()).stream()
        .map(EffortView::of)
        .toList();
    Filters countFilters = new Filters(filters.clauses(), List.of(), 1, 1, filters.q());
    QueryWrapper countQuery = FilterPredicate.compile(countFilters, COLUMNS::get, specials, injected);
    return new EffortList(items, effortRepository.countByQuery(countQuery));
  }
}
