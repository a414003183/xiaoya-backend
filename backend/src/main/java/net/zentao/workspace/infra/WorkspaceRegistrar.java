package net.zentao.workspace.infra;

import java.util.List;
import java.util.Map;
import net.zentao.platform.meta.DictProvider;
import net.zentao.platform.meta.DictRegistry;
import net.zentao.platform.meta.MetaRegistry;
import net.zentao.platform.meta.MetaView;
import net.zentao.platform.rbac.PrivilegeCatalog;
import net.zentao.platform.search.SearchRegistry;
import net.zentao.platform.search.SearchScope;
import net.zentao.platform.workflow.WorkflowRegistry;
import net.zentao.workspace.app.TodoQueryService;
import org.springframework.context.annotation.Configuration;

/**
 * workspace → platform 反向注册（A3 不破坏：域主动调用 platform 注册口）。
 * 权限码：workspace 卡 §5 全集（`report-view` 复用 quality 域「测试报告」已登记码，不重复编目）；
 * 字典：todoType；meta：todo（字段 + 动作 + 四态可视化）；搜索 scope：todo。
 */
@Configuration
public class WorkspaceRegistrar {

  public WorkspaceRegistrar(MetaRegistry metaRegistry, PrivilegeCatalog privilegeCatalog,
      WorkflowRegistry workflowRegistry, SearchRegistry searchRegistry, DictRegistry dictRegistry,
      TodoQueryService todoQueryService) {
    privilegeCatalog.register("workspace", List.of(
        "todo-view", "todo-create", "todo-edit", "todo-delete", "todo-start", "todo-finish", "todo-activate",
        "todo-close", "todo-assign", "my-view", "weekly-report-view"));

    // 待办类型选项（workspace 卡 §5：GET /dicts/todoType）——同一份清单同时是 meta/todo.type 的 options，
    // 前端下拉不另立一份（列表筛选与创建表单同源）。
    List<Map<String, Object>> todoTypeItems = List.of(
        Map.of("value", "custom", "i18n", "todo.type.custom"),
        Map.of("value", "bug", "i18n", "todo.type.bug"),
        Map.of("value", "task", "i18n", "todo.type.task"),
        Map.of("value", "story", "i18n", "todo.type.story"),
        Map.of("value", "epic", "i18n", "todo.type.epic"),
        Map.of("value", "requirement", "i18n", "todo.type.requirement"),
        Map.of("value", "testRun", "i18n", "todo.type.testRun"));
    dictRegistry.register(new DictProvider() {
      @Override
      public String name() {
        return "todoType";
      }

      @Override
      public List<Map<String, Object>> items() {
        return todoTypeItems;
      }
    });

    metaRegistry.register("todo", new MetaView(
        "todo",
        List.of(
            new MetaView.MetaField("title", "text", true, 150, "todo.field.title", null, null, null),
            new MetaView.MetaField("type", "select", true, null, "todo.field.type", "todoType", null,
                todoTypeItems),
            new MetaView.MetaField("objectId", "select", null, null, "todo.field.object", null, null, null),
            new MetaView.MetaField("date", "date", null, null, "todo.field.date", null, null, null),
            new MetaView.MetaField("beginTime", "text", null, 5, "todo.field.begin", null, null, null),
            new MetaView.MetaField("endTime", "text", null, 5, "todo.field.end", null, null, null),
            new MetaView.MetaField("priority", "select", true, null, "common.priority", null, null, List.of(
                Map.of("value", 1, "i18n", "common.priority.1"),
                Map.of("value", 2, "i18n", "common.priority.2"),
                Map.of("value", 3, "i18n", "common.priority.3"),
                Map.of("value", 4, "i18n", "common.priority.4"))),
            // 列表筛选值域（§3.1 filterable：status）——前端状态下拉只认这里的选项。
            new MetaView.MetaField("status", "select", null, null, "common.field.status", null, null, List.of(
                Map.of("value", "wait", "i18n", "todo.status.wait"),
                Map.of("value", "doing", "i18n", "todo.status.doing"),
                Map.of("value", "done", "i18n", "todo.status.done"),
                Map.of("value", "closed", "i18n", "todo.status.closed"))),
            new MetaView.MetaField("description", "richtext", null, null, "todo.field.description", null, null,
                null),
            new MetaView.MetaField("isPrivate", "checkbox", null, null, "todo.field.isPrivate", null, null, null),
            new MetaView.MetaField("assignee", "account", true, null, "todo.field.assignee", "accounts", null,
                null)),
        new MetaView.MetaList(List.of("id", "title", "type", "priority", "status", "date", "assignee"), "-id"),
        workflowRegistry.actionsOf("todo"),
        Map.of(
            "wait", new MetaView.MetaStatusVisual("wait", "todo.status.wait"),
            "doing", new MetaView.MetaStatusVisual("doing", "todo.status.doing"),
            "done", new MetaView.MetaStatusVisual("active", "todo.status.done"),
            "closed", new MetaView.MetaStatusVisual("closed", "todo.status.closed"))));

    // meta：workspace（§3.4 我的地盘聚合视图无表；此处只承载 /my/* 的 role 参数值域——
    // 前端页签/筛选下拉不另立清单，字段名 taskRole/bugRole/storyRole 对应 /my/tasks|bugs|stories）。
    metaRegistry.register("workspace", new MetaView(
        "workspace",
        List.of(
            new MetaView.MetaField("taskRole", "select", null, null, "workspace.title.myTasks", null, null, List.of(
                Map.of("value", "assignee", "i18n", "my.role.tasks.assignee"),
                Map.of("value", "creator", "i18n", "my.role.tasks.creator"),
                Map.of("value", "finisher", "i18n", "my.role.tasks.finisher"),
                Map.of("value", "closer", "i18n", "my.role.tasks.closer"))),
            new MetaView.MetaField("bugRole", "select", null, null, "workspace.title.myBugs", null, null, List.of(
                Map.of("value", "assignee", "i18n", "my.role.bugs.assignee"),
                Map.of("value", "creator", "i18n", "my.role.bugs.creator"),
                Map.of("value", "resolver", "i18n", "my.role.bugs.resolver"),
                Map.of("value", "closer", "i18n", "my.role.bugs.closer"))),
            new MetaView.MetaField("storyRole", "select", null, null, "workspace.title.myStories", null, null,
                List.of(
                    Map.of("value", "assignee", "i18n", "my.role.stories.assignee"),
                    Map.of("value", "creator", "i18n", "my.role.stories.creator"),
                    Map.of("value", "reviewer", "i18n", "my.role.stories.reviewer"),
                    Map.of("value", "closer", "i18n", "my.role.stories.closer")))),
        new MetaView.MetaList(List.of(), "-id"),
        List.of(),
        Map.of()));

    searchRegistry.register(new SearchScope("todo", List.of("title"),
        (q, limit, principal) -> todoQueryService.search(q, limit, principal)));
  }
}
