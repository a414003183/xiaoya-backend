package net.zentao.workspace.app;

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
import net.zentao.platform.rbac.DataScope;
import net.zentao.platform.search.SearchResultView;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.workspace.api.TodoList;
import net.zentao.workspace.api.TodoView;
import net.zentao.workspace.domain.Todo;
import net.zentao.workspace.domain.TodoRepository;
import org.springframework.stereotype.Component;

/**
 * 待办列表与详情（workspace 卡 §3.1 DSL 白名单 + §7 归属注入）。
 * 归属规则：列表强制 `(assignee = @me OR finishedBy = @me OR closedBy = @me)`，超管不受限；
 * objectTitle 在列表层批量现算（一次分组查询，避免 N+1）。
 */
@Component
public class TodoQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("status", "type", "priority", "date", "assignee", "createdBy", "isPrivate", "id"),
      Set.of("id", "date", "priority", "beginTime", "createdAt"),
      Set.of("title"));

  private static final Map<String, String> COLUMNS = Map.ofEntries(
      Map.entry("id", "id"),
      Map.entry("title", "title"),
      Map.entry("type", "type"),
      Map.entry("status", "status"),
      Map.entry("priority", "priority"),
      Map.entry("date", "todo_date"),
      Map.entry("beginTime", "begin_time"),
      Map.entry("isPrivate", "is_private"),
      Map.entry("assignee", "assignee"),
      Map.entry("createdBy", "created_by"),
      Map.entry("createdAt", "created_at"));

  private final TodoRepository repository;
  private final TodoTitleResolver titleResolver;
  private final DataScope dataScope;

  public TodoQueryService(TodoRepository repository, TodoTitleResolver titleResolver, DataScope dataScope) {
    this.repository = repository;
    this.titleResolver = titleResolver;
    this.dataScope = dataScope;
  }

  /** 待办列表（DSL 白名单之外的字段 → 40001，Filters 解析层拦截）。 */
  public TodoList page(SessionPrincipal principal, Map<String, String[]> params) {
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition injected = base(principal);
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> special(value, principal), injected);
    List<Todo> todos = repository.queryPage(query, filters.offset(), filters.limit());
    Map<Long, String> titles = titleResolver.resolveAll(todos);
    List<TodoView> items = todos.stream().map(todo -> TodoView.of(todo, titles.get(todo.id()))).toList();
    Filters countFilters = new Filters(filters.clauses(), List.of(), 1, 1, filters.q());
    QueryWrapper countQuery =
        FilterPredicate.compile(countFilters, COLUMNS::get, value -> special(value, principal), injected);
    return new TodoList(items, repository.countByQuery(countQuery));
  }

  /** 待办详情（§7：不存在 → 40401；私有且非当事人 → 40302）。 */
  public TodoView detail(SessionPrincipal principal, long todoId) {
    Todo todo = repository.findActiveById(todoId)
        .orElseThrow(() -> net.zentao.platform.error.ApiException.notFound("待办"));
    TodoAccess.requireReadable(principal.account(), todo, dataScope.isSuperAdmin(principal));
    return TodoView.of(todo, titleResolver.resolve(todo));
  }

  /** 全局搜索 scope=todo（platform 卡 §5.2）：searchable=title，同样先注入归属条件。 */
  public List<SearchResultView> search(String q, int limit, SessionPrincipal principal) {
    String like = "%" + q + "%";
    QueryWrapper query = QueryWrapper.create()
        .where(base(principal).and(new QueryColumn("title").like(like)))
        .orderBy(new QueryColumn("updated_at").desc(), new QueryColumn("id").desc()) // banned-words-ok：MyBatis-Flex 构造器方法名
        .limit(limit);
    return repository.queryPage(query, 0, limit).stream()
        .map(todo -> new SearchResultView("todo", todo.id(), todo.title(), excerpt(todo),
            todo.updatedAt() == null ? todo.createdAt() : todo.updatedAt()))
        .toList();
  }

  /** 列表/搜索共用前置条件：未删 + 归属（§7「不暴露他人待办枚举」对超管同样生效）。 */
  private QueryCondition base(SessionPrincipal principal) {
    String me = principal.account();
    return new QueryColumn("deleted_at").isNull()
        .and(new QueryColumn("assignee").eq(me)
            .or(new QueryColumn("finished_by").eq(me))
            .or(new QueryColumn("closed_by").eq(me)));
  }

  private QueryCondition keywordCondition(String q) {
    return q == null || q.isBlank() ? null : new QueryColumn("title").like("%" + q + "%");
  }

  private static Optional<String> special(String value, SessionPrincipal principal) {
    if ("@me".equals(value)) {
      return Optional.of(principal.account());
    }
    return Optional.empty();
  }

  private static String excerpt(Todo todo) {
    String description = todo.description();
    if (description == null || description.isBlank()) {
      return null;
    }
    String text = description.replaceAll("\\s+", " ").trim();
    return text.length() > 200 ? text.substring(0, 200) : text;
  }
}
