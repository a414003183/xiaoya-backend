package net.zentao.task.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.search.SearchResultView;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.api.ExecutionApi;
import net.zentao.requirement.api.StoryApi;
import net.zentao.task.api.TaskList;
import net.zentao.task.api.TaskView;
import net.zentao.task.domain.Task;
import net.zentao.task.domain.TaskRepository;
import org.springframework.stereotype.Component;

/**
 * 任务列表与详情（task 卡 §3 DSL 白名单 + §7 DataScope 注入 `execution_id IN 可见执行集`）。
 * 详情附 children 摘要与 storyTitle 联查字段；列表批量补 storyTitle（一次 IN 查询，避免 N+1）。
 */
@Component
public class TaskQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("status", "type", "priority", "assignee", "storyId", "parentId", "categoryId", "closedReason",
          "isParent", "deadline", "createdBy", "createdAt", "finishedBy", "closedBy", "id"),
      Set.of("id", "priority", "status", "deadline", "estimateHours", "consumedHours", "leftHours", "createdAt"),
      Set.of("title", "keywords"));

  private static final Map<String, String> COLUMNS = Map.ofEntries(
      Map.entry("status", "status"),
      Map.entry("type", "type"),
      Map.entry("priority", "priority"),
      Map.entry("assignee", "assignee"),
      Map.entry("storyId", "story_id"),
      Map.entry("parentId", "parent_id"),
      Map.entry("categoryId", "category_id"),
      Map.entry("closedReason", "closed_reason"),
      Map.entry("isParent", "is_parent"),
      Map.entry("deadline", "deadline"),
      Map.entry("createdBy", "created_by"),
      Map.entry("createdAt", "created_at"),
      Map.entry("finishedBy", "finished_by"),
      Map.entry("closedBy", "closed_by"),
      Map.entry("id", "id"),
      Map.entry("estimateHours", "estimate_hours"),
      Map.entry("consumedHours", "consumed_hours"),
      Map.entry("leftHours", "left_hours"));

  private final TaskRepository repository;
  private final ExecutionApi executionApi;
  private final StoryApi storyApi;

  public TaskQueryService(TaskRepository repository, ExecutionApi executionApi, StoryApi storyApi) {
    this.repository = repository;
    this.executionApi = executionApi;
    this.storyApi = storyApi;
  }

  /** 执行下任务列表；filters[parentId]=@null 取顶层、=id 取子任务（§5）。 */
  public TaskList page(SessionPrincipal principal, long executionId, Map<String, String[]> params) {
    ExecutionApi.ExecutionScope scope = executionApi.executionScope(principal);
    QueryCondition scopeCondition = new QueryColumn("execution_id").eq(executionId);
    if (!scope.visibleToAll() && !scope.executionIds().contains(executionId)) {
      scopeCondition = scopeCondition.and(new QueryColumn("id").eq(-1L));
    }
    return query(principal, params, scopeCondition);
  }

  /** 跨执行任务列表（workspace 卡 §5 /my/tasks：DataScope 交集为可见执行集，不限单个执行）。 */
  public TaskList pageAll(SessionPrincipal principal, Map<String, String[]> params) {
    ExecutionApi.ExecutionScope scope = executionApi.executionScope(principal);
    QueryCondition scopeCondition = scope.visibleToAll() ? null : new QueryColumn("execution_id")
        .in(scope.executionIds().isEmpty() ? List.of(-1L) : List.copyOf(scope.executionIds()));
    return query(principal, params, scopeCondition);
  }

  private TaskList query(SessionPrincipal principal, Map<String, String[]> params, QueryCondition scopeCondition) {
    QueryCondition injected = new QueryColumn("deleted_at").isNull();
    if (scopeCondition != null) {
      injected = injected.and(scopeCondition);
    }
    Filters filters = normalizeNoneIds(Filters.parse(params, REGISTRY));
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, specials(principal), injected);
    List<Task> tasks = repository.queryPage(query, filters.offset(), filters.limit());
    List<TaskView> items = withStoryTitles(tasks);
    Filters countFilters = new Filters(filters.clauses(), List.of(), 1, 1, filters.q());
    QueryWrapper countQuery = FilterPredicate.compile(countFilters, COLUMNS::get, specials(principal), injected);
    return new TaskList(items, repository.countByQuery(countQuery));
  }

  /** 详情：含 children 摘要数组（id/title/status/assignee）。 */
  public TaskView detail(SessionPrincipal principal, long taskId) {
    Task task = TaskGuard.requireReadable(repository, executionApi, principal, taskId);
    List<TaskView.TaskChildSummary> children = repository.findActiveChildren(taskId).stream()
        .map(TaskView.TaskChildSummary::of)
        .toList();
    return TaskView.of(task, children, storyTitle(task));
  }

  /** 详情视图（供动作端点返回最新 TaskView；同样补 children/storyTitle）。 */
  public TaskView viewOf(SessionPrincipal principal, Task task) {
    List<TaskView.TaskChildSummary> children = repository.findActiveChildren(task.id()).stream()
        .map(TaskView.TaskChildSummary::of)
        .toList();
    return TaskView.of(task, children, storyTitle(task));
  }

  private List<TaskView> withStoryTitles(List<Task> tasks) {
    List<Long> storyIds = tasks.stream().map(Task::storyId).filter(id -> id != 0).distinct().toList();
    Map<Long, String> titles = new HashMap<>();
    if (!storyIds.isEmpty()) {
      storyApi.findByIds(storyIds).forEach(story -> titles.put(story.id(), story.title()));
    }
    return tasks.stream().map(task -> TaskView.of(task, null, titles.get(task.storyId()))).toList();
  }

  private String storyTitle(Task task) {
    return task.storyId() == 0 ? null : storyApi.findById(task.storyId()).map(story -> story.title()).orElse(null);
  }

  /** 0 即「无」的 id 列（§3：parentId=0 顶层、storyId=0 无关联）：filters[x]=@null 落 =0 而非 IS NULL。 */
  private static final Set<String> NONE_IDS = Set.of("parentId", "storyId", "categoryId");

  private static Filters normalizeNoneIds(Filters filters) {
    List<Filters.FilterClause> clauses = filters.clauses().stream()
        .map(clause -> clause.op() == Filters.Op.IS_NULL && NONE_IDS.contains(clause.field())
            ? new Filters.FilterClause(clause.field(), Filters.Op.EQ, List.of("0"))
            : clause)
        .toList();
    return new Filters(clauses, filters.sortKeys(), filters.page(), filters.limit(), filters.q());
  }

  /** 特殊量（03 §3）：@me 当前账号（assignee 等账号列）。 */
  private static java.util.function.Function<String, Optional<String>> specials(SessionPrincipal principal) {
    return value -> "@me".equals(value) ? Optional.of(principal.account()) : Optional.empty();
  }

  /** 只读子资源（动态流等）的可见性守卫。 */
  public void requireReadable(SessionPrincipal principal, long taskId) {
    TaskGuard.requireReadable(repository, executionApi, principal, taskId);
  }

  /**
   * 全局搜索（platform 卡 §5.2）：DataScope 先于 LIKE——可见执行集作为前置条件，
   * 命中 title/keywords，按 updatedAt 倒序取 limit 条。
   */
  public List<SearchResultView> search(String q, int limit, SessionPrincipal principal) {
    String like = "%" + q + "%";
    QueryCondition injected = new QueryColumn("deleted_at").isNull()
        .and(new QueryColumn("title").like(like).or(new QueryColumn("keywords").like(like)));
    ExecutionApi.ExecutionScope scope = executionApi.executionScope(principal);
    if (!scope.visibleToAll()) {
      injected = injected.and(new QueryColumn("execution_id")
          .in(scope.executionIds().isEmpty() ? List.of(-1L) : List.copyOf(scope.executionIds())));
    }
    QueryWrapper query = QueryWrapper.create().where(injected)
        .orderBy(new QueryColumn("updated_at").desc(), new QueryColumn("id").desc()) // banned-words-ok：MyBatis-Flex 构造器方法名
        .limit(limit);
    return repository.queryPage(query, 0, limit).stream()
        .map(task -> new SearchResultView("task", task.id(), task.title(), excerpt(task),
            task.updatedAt() == null ? task.createdAt() : task.updatedAt()))
        .toList();
  }

  private static String excerpt(Task task) {
    String description = task.description();
    if (description == null || description.isBlank()) {
      return null;
    }
    String flat = description.replaceAll("\s+", " ").trim();
    return flat.length() > 100 ? flat.substring(0, 100) : flat;
  }

  private QueryCondition keywordCondition(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    String like = "%" + q + "%";
    return new QueryColumn("title").like(like).or(new QueryColumn("keywords").like(like));
  }
}
