package net.zentao.task.infra;

import java.util.List;
import java.util.Map;
import net.zentao.platform.meta.MetaRegistry;
import net.zentao.platform.meta.MetaView;
import net.zentao.platform.rbac.PrivilegeCatalog;
import net.zentao.platform.search.SearchRegistry;
import net.zentao.platform.search.SearchScope;
import net.zentao.platform.workflow.WorkflowRegistry;
import net.zentao.task.app.TaskQueryService;
import org.springframework.context.annotation.Configuration;

/**
 * task → platform 反向注册（A3 不破坏：域主动调用 platform 注册口）。
 * 权限码：task 卡 §5 全集；meta：task（字段 + 动作 + 六态可视化 + 列表默认列排序）+ effort；
 * 搜索 scope：task（§5.2 固定词表之一，DataScope 在查询内先行注入）。
 */
@Configuration
public class TaskRegistrar {

  public TaskRegistrar(MetaRegistry metaRegistry, PrivilegeCatalog privilegeCatalog,
      WorkflowRegistry workflowRegistry, SearchRegistry searchRegistry, TaskQueryService queryService) {
    privilegeCatalog.register("task", List.of(
        "task-view", "task-create", "task-edit", "task-start", "task-finish", "task-pause", "task-resume",
        "task-cancel", "task-close", "task-activate", "task-assign", "task-effort", "task-effort-edit",
        "task-effort-delete", "task-delete"));

    metaRegistry.register("task", new MetaView(
        "task",
        List.of(
            new MetaView.MetaField("title", "text", true, 255, "task.field.title", null, null, null),
            new MetaView.MetaField("type", "select", true, null, "task.field.type", null, null, List.of(
                Map.of("value", "design", "i18n", "task.type.design"),
                Map.of("value", "devel", "i18n", "task.type.devel"),
                Map.of("value", "request", "i18n", "task.type.request"),
                Map.of("value", "test", "i18n", "task.type.test"),
                Map.of("value", "study", "i18n", "task.type.study"),
                Map.of("value", "discuss", "i18n", "task.type.discuss"),
                Map.of("value", "ui", "i18n", "task.type.ui"),
                Map.of("value", "affair", "i18n", "task.type.affair"),
                Map.of("value", "misc", "i18n", "task.type.misc"))),
            new MetaView.MetaField("priority", "select", true, null, "common.priority", null, null, List.of(
                Map.of("value", 1, "i18n", "common.priority.1"),
                Map.of("value", 2, "i18n", "common.priority.2"),
                Map.of("value", 3, "i18n", "common.priority.3"),
                Map.of("value", 4, "i18n", "common.priority.4"))),
            // 列表筛选值域（task §3 filterable：status/closedReason）——前端下拉只认这里的选项。
            new MetaView.MetaField("status", "select", null, null, "task.field.status", null, null, List.of(
                Map.of("value", "wait", "i18n", "task.status.wait"),
                Map.of("value", "doing", "i18n", "task.status.doing"),
                Map.of("value", "done", "i18n", "task.status.done"),
                Map.of("value", "pause", "i18n", "task.status.pause"),
                Map.of("value", "cancel", "i18n", "task.status.cancel"),
                Map.of("value", "closed", "i18n", "task.status.closed"))),
            new MetaView.MetaField("closedReason", "select", null, null, "task.field.closedReason", null, null,
                List.of(
                    Map.of("value", "done", "i18n", "task.closeReason.done"),
                    Map.of("value", "cancel", "i18n", "task.closeReason.cancel"))),
            new MetaView.MetaField("storyId", "select", null, null, "task.field.story", "stories", null, null),
            new MetaView.MetaField("parentId", "select", null, null, "task.field.parent", "tasks", null, null),
            new MetaView.MetaField("categoryId", "select", null, null, "task.field.category", "categories", null, null),
            new MetaView.MetaField("estimateHours", "decimal", null, null, "task.field.estimate", null, null, null),
            new MetaView.MetaField("estStartedDate", "date", null, null, "task.field.estStarted", null, null, null),
            new MetaView.MetaField("deadline", "date", null, null, "task.field.deadline", null, null, null),
            new MetaView.MetaField("assignee", "account", null, null, "task.field.assignee", "accounts", null, null),
            new MetaView.MetaField("keywords", "text", null, 255, "task.field.keywords", null, null, null),
            new MetaView.MetaField("description", "richtext", null, null, "task.field.description", null, null, null),
            new MetaView.MetaField("notifyAccounts", "accounts", null, null, "task.field.notify", "accounts", true,
                null)),
        new MetaView.MetaList(
            List.of("id", "title", "priority", "status", "assignee", "estimateHours", "consumedHours", "leftHours",
                "deadline"),
            "-id"),
        workflowRegistry.actionsOf("task"),
        Map.of(
            "wait", new MetaView.MetaStatusVisual("wait", "task.status.wait"),
            "doing", new MetaView.MetaStatusVisual("doing", "task.status.doing"),
            "done", new MetaView.MetaStatusVisual("active", "task.status.done"),
            "pause", new MetaView.MetaStatusVisual("pause", "task.status.pause"),
            "cancel", new MetaView.MetaStatusVisual("closed", "task.status.cancel"),
            "closed", new MetaView.MetaStatusVisual("closed", "task.status.closed"))));

    metaRegistry.register("effort", new MetaView(
        "effort",
        List.of(
            new MetaView.MetaField("workDate", "date", true, null, "effort.field.workDate", null, null, null),
            new MetaView.MetaField("consumedHours", "decimal", true, null, "effort.field.consumed", null, null, null),
            new MetaView.MetaField("leftHours", "decimal", null, null, "effort.field.left", null, null, null),
            new MetaView.MetaField("work", "text", null, 255, "effort.field.work", null, null, null)),
        new MetaView.MetaList(List.of("id", "workDate", "account", "consumedHours", "leftHours", "work"),
            "-workDate,-id"),
        List.of(),
        Map.of()));

    searchRegistry.register(new SearchScope("task", List.of("title", "keywords"),
        (q, limit, principal) -> queryService.search(q, limit, principal)));
  }
}
