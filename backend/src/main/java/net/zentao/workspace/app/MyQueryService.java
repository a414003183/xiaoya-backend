package net.zentao.workspace.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.Map;
import net.zentao.platform.activity.ActivityQueryService;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.quality.api.BugApi;
import net.zentao.requirement.api.StoryApi;
import net.zentao.task.api.TaskApi;
import net.zentao.workspace.api.MySummaryView;
import net.zentao.workspace.domain.TodoRepository;
import org.springframework.stereotype.Component;

/**
 * 我的地盘跨域只读聚合（workspace 卡 §3.4/§5）：role → 目标域过滤字段映射的真源在本域，
 * 取数经各域 api（A2），结果自动与目标域 DataScope 取交集。
 */
@Component
public class MyQueryService {

  /** §3.4 映射真源：/my/tasks 与 /my/bugs /my/stories 的 role 取值。 */
  private static final Map<String, String> TASK_ROLES =
      Map.of("assignee", "assignee", "creator", "createdBy", "finisher", "finishedBy", "closer", "closedBy");
  private static final Map<String, String> BUG_ROLES =
      Map.of("assignee", "assignee", "creator", "createdBy", "resolver", "resolvedBy", "closer", "closedBy");
  private static final Map<String, String> STORY_ROLES =
      Map.of("assignee", "assignee", "creator", "createdBy", "reviewer", "reviewedBy", "closer", "closedBy");

  /** 计数口径（§3.4）：非终态集合，DSL 无 NE 算子故按正向枚举（状态机全集的可枚举补集）。 */
  private static final String TASK_OPEN_STATUSES = "wait,doing,pause";
  private static final String STORY_OPEN_STATUSES = "reviewing,active,changing,changed";

  private final TodoRepository todoRepository;
  private final TaskApi taskApi;
  private final BugApi bugApi;
  private final StoryApi storyApi;
  private final ActivityQueryService activityQueryService;

  public MyQueryService(TodoRepository todoRepository, TaskApi taskApi, BugApi bugApi, StoryApi storyApi,
      ActivityQueryService activityQueryService) {
    this.todoRepository = todoRepository;
    this.taskApi = taskApi;
    this.bugApi = bugApi;
    this.storyApi = storyApi;
    this.activityQueryService = activityQueryService;
  }

  /** 地盘首页四计数（固定 role=assignee 口径）。 */
  public MySummaryView summary(SessionPrincipal principal) {
    return new MySummaryView(
        todoCount(principal),
        taskApi.pageByRole(principal, "assignee", counts(TASK_OPEN_STATUSES)).total(),
        bugApi.pageByRole(principal, "assignee", counts("active")).total(),
        storyApi.pageByRole(principal, "assignee", counts(STORY_OPEN_STATUSES)).total());
  }

  public net.zentao.task.api.TaskList tasks(SessionPrincipal principal, String role, Map<String, String[]> params) {
    return taskApi.pageByRole(principal, roleField(TASK_ROLES, role), params);
  }

  public net.zentao.quality.api.BugList bugs(SessionPrincipal principal, String role, Map<String, String[]> params) {
    return bugApi.pageByRole(principal, roleField(BUG_ROLES, role), params);
  }

  public net.zentao.requirement.api.StoryList stories(SessionPrincipal principal, String role,
      Map<String, String[]> params) {
    return storyApi.pageByRole(principal, roleField(STORY_ROLES, role), params);
  }

  /** 我的动态（actor=@me，跨对象游标倒序）。 */
  public ActivityQueryService.ActivityList activities(SessionPrincipal principal, Integer limit, Long beforeId) {
    return activityQueryService.listByActor(principal.account(), limit, beforeId);
  }

  private long todoCount(SessionPrincipal principal) {
    QueryWrapper query = QueryWrapper.create().where(new QueryColumn("deleted_at").isNull()
        .and(new QueryColumn("assignee").eq(principal.account()))
        .and(new QueryColumn("status").in("wait", "doing")));
    return todoRepository.countByQuery(query);
  }

  private static Map<String, String[]> counts(String statuses) {
    return Map.of("limit", new String[] {"1"}, "filters[status]", new String[] {statuses});
  }

  /** role 缺省 assignee；非法值 → 40001（§3.4）。 */
  private static String roleField(Map<String, String> roles, String role) {
    String key = role == null || role.isBlank() ? "assignee" : role;
    String field = roles.get(key);
    if (field == null) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "my.filter.roleInvalid", role);
    }
    return field;
  }
}
