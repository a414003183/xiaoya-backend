package net.zentao.product.infra;

import java.util.List;
import java.util.Map;
import net.zentao.platform.meta.MetaRegistry;
import net.zentao.platform.meta.MetaView;
import net.zentao.platform.rbac.PrivilegeCatalog;
import net.zentao.platform.workflow.WorkflowRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * product → platform 反向注册（A3 不破坏：域主动调用 platform 注册口）。
 * 权限码：product 卡 §5 六族全集；meta：六实体（本类随 T-2/T-6/T-8/T-9 逐个补齐）。
 */
@Configuration
public class ProductRegistrar {

  public ProductRegistrar(MetaRegistry metaRegistry, PrivilegeCatalog privilegeCatalog,
      WorkflowRegistry workflowRegistry) {
    privilegeCatalog.register("product", List.of(
        "product-view", "product-create", "product-edit", "product-close", "product-activate", "product-delete"));
    privilegeCatalog.register("branch", List.of("branch-manage", "branch-delete"));
    privilegeCatalog.register("category", List.of("category-manage"));
    privilegeCatalog.register("plan", List.of(
        "plan-view", "plan-create", "plan-edit", "plan-start", "plan-finish", "plan-close", "plan-activate",
        "plan-link", "plan-delete"));
    privilegeCatalog.register("release", List.of(
        "release-view", "release-create", "release-edit", "release-terminate", "release-link", "release-delete"));
    privilegeCatalog.register("build", List.of(
        "build-view", "build-create", "build-edit", "build-delete", "build-link"));

    metaRegistry.register("product", new MetaView(
        "product",
        List.of(
            new MetaView.MetaField("name", "text", true, 90, "product.field.name", null, null, null),
            new MetaView.MetaField("code", "text", null, 45, "product.field.code", null, null, null),
            new MetaView.MetaField("type", "select", null, null, "product.field.type", null, null, List.of(
                Map.of("value", "normal", "i18n", "product.type.normal"),
                Map.of("value", "branch", "i18n", "product.type.branch"),
                Map.of("value", "platform", "i18n", "product.type.platform"))),
            new MetaView.MetaField("programId", "number", null, null, "product.field.program", null, null, null),
            new MetaView.MetaField("po", "account", null, null, "product.field.po", "accounts", null, null),
            new MetaView.MetaField("qd", "account", null, null, "product.field.qd", "accounts", null, null),
            new MetaView.MetaField("rd", "account", null, null, "product.field.rd", "accounts", null, null),
            new MetaView.MetaField("acl", "select", true, null, "product.field.acl", null, null, List.of(
                Map.of("value", "public", "i18n", "product.acl.public"),
                Map.of("value", "private", "i18n", "product.acl.private"),
                Map.of("value", "custom", "i18n", "product.acl.custom"))),
            // 列表筛选值域（product 卡 §3.1 filterable：status）——前端状态下拉只认这里的选项。
            new MetaView.MetaField("status", "select", null, null, "product.field.status", null, null, List.of(
                Map.of("value", "normal", "i18n", "product.status.normal"),
                Map.of("value", "closed", "i18n", "product.status.closed"))),
            new MetaView.MetaField("whitelist", "accounts", null, null, "product.field.whitelist", "accounts", true, null),
            new MetaView.MetaField("description", "richtext", null, null, "product.field.description", null, null, null),
            new MetaView.MetaField("sort", "number", null, null, "product.field.sort", null, null, null)),
        new MetaView.MetaList(List.of("id", "name", "type", "status", "acl", "po", "createdBy"), "-id"),
        workflowRegistry.actionsOf("product"),
        Map.of(
            "normal", new MetaView.MetaStatusVisual("active", "product.status.normal"),
            "closed", new MetaView.MetaStatusVisual("closed", "product.status.closed"))));

    metaRegistry.register("branch", new MetaView(
        "branch",
        List.of(
            new MetaView.MetaField("name", "text", true, 255, "branch.field.name", null, null, null),
            new MetaView.MetaField("description", "text", null, 255, "branch.field.description", null, null, null),
            new MetaView.MetaField("sort", "number", null, null, "branch.field.sort", null, null, null),
            new MetaView.MetaField("isDefault", "checkbox", null, null, "branch.field.isDefault", null, null, null),
            // 列表筛选值域（product 卡 §3.2 filterable：status）——前端状态下拉只认这里的选项。
            new MetaView.MetaField("status", "select", null, null, "branch.field.status", null, null, List.of(
                Map.of("value", "active", "i18n", "branch.status.active"),
                Map.of("value", "closed", "i18n", "branch.status.closed")))),
        new MetaView.MetaList(List.of("id", "name", "isDefault", "status", "sort"), "sort"),
        workflowRegistry.actionsOf("branch"),
        Map.of(
            "active", new MetaView.MetaStatusVisual("active", "branch.status.active"),
            "closed", new MetaView.MetaStatusVisual("closed", "branch.status.closed"))));

    metaRegistry.register("category", new MetaView(        "category",
        List.of(
            new MetaView.MetaField("name", "text", true, 60, "category.field.name", null, null, null),
            new MetaView.MetaField("type", "select", true, null, "category.field.type", null, null, List.of(
                Map.of("value", "story", "i18n", "category.type.story"),
                Map.of("value", "bug", "i18n", "category.type.bug"),
                Map.of("value", "case", "i18n", "category.type.case"))),
            new MetaView.MetaField("parentId", "select", null, null, "category.field.parent", "categories", null, null),
            new MetaView.MetaField("owner", "account", null, null, "category.field.owner", "accounts", null, null),
            new MetaView.MetaField("sort", "number", null, null, "category.field.sort", null, null, null)),
        new MetaView.MetaList(List.of("id", "name", "type", "owner", "sort"), "sort"),
        List.of(),
        Map.of()));

    metaRegistry.register("plan", new MetaView(
        "plan",
        List.of(
            new MetaView.MetaField("title", "text", true, 90, "plan.field.title", null, null, null),
            new MetaView.MetaField("branchId", "select", null, null, "plan.field.branch", "branches", null, null),
            new MetaView.MetaField("parentId", "select", null, null, "plan.field.parent", "plans", null, null),
            new MetaView.MetaField("beginDate", "date", null, null, "plan.field.beginDate", null, null, null),
            new MetaView.MetaField("endDate", "date", null, null, "plan.field.endDate", null, null, null),
            // 列表筛选值域（product 卡 §3.4 filterable：status）——前端状态下拉只认这里的选项。
            new MetaView.MetaField("status", "select", null, null, "plan.field.status", null, null, List.of(
                Map.of("value", "wait", "i18n", "plan.status.wait"),
                Map.of("value", "doing", "i18n", "plan.status.doing"),
                Map.of("value", "done", "i18n", "plan.status.done"),
                Map.of("value", "closed", "i18n", "plan.status.closed"))),
            // 关闭原因值域（plan §4.3 close 动作请求体 closedReason）——关闭弹窗选项只认这里。
            new MetaView.MetaField("closedReason", "select", null, null, "plan.field.closedReason", null, null,
                List.of(
                    Map.of("value", "done", "i18n", "plan.closeReason.done"),
                    Map.of("value", "cancel", "i18n", "plan.closeReason.cancel"))),
            new MetaView.MetaField("description", "richtext", null, null, "plan.field.description", null, null, null)),
        new MetaView.MetaList(List.of("id", "title", "status", "beginDate", "endDate", "createdBy"), "-id"),
        workflowRegistry.actionsOf("plan"),
        Map.of(
            "wait", new MetaView.MetaStatusVisual("wait", "plan.status.wait"),
            "doing", new MetaView.MetaStatusVisual("doing", "plan.status.doing"),
            "done", new MetaView.MetaStatusVisual("active", "plan.status.done"),
            "closed", new MetaView.MetaStatusVisual("closed", "plan.status.closed"))));

    metaRegistry.register("release", new MetaView(
        "release",
        List.of(
            new MetaView.MetaField("name", "text", true, 90, "release.field.name", null, null, null),
            new MetaView.MetaField("branchId", "select", null, null, "release.field.branch", "branches", null, null),
            new MetaView.MetaField("buildId", "select", null, null, "release.field.build", "builds", null, null),
            new MetaView.MetaField("releaseDate", "date", true, null, "release.field.releaseDate", null, null, null),
            new MetaView.MetaField("isMilestone", "checkbox", null, null, "release.field.isMilestone", null, null, null),
            // 列表筛选值域（product 卡 §3.5 filterable：status）——前端状态下拉只认这里的选项。
            new MetaView.MetaField("status", "select", null, null, "release.field.status", null, null, List.of(
                Map.of("value", "normal", "i18n", "release.status.normal"),
                Map.of("value", "terminated", "i18n", "release.status.terminated"))),
            new MetaView.MetaField("notifyAccounts", "accounts", null, null, "release.field.notify", "accounts", true, null),
            new MetaView.MetaField("description", "richtext", null, null, "release.field.description", null, null, null)),
        new MetaView.MetaList(List.of("id", "name", "status", "releaseDate", "isMilestone", "createdBy"), "-id"),
        workflowRegistry.actionsOf("release"),
        Map.of(
            "normal", new MetaView.MetaStatusVisual("active", "release.status.normal"),
            "terminated", new MetaView.MetaStatusVisual("closed", "release.status.terminated"))));

    metaRegistry.register("build", new MetaView(
        "build",
        List.of(
            new MetaView.MetaField("name", "text", true, 150, "build.field.name", null, null, null),
            new MetaView.MetaField("branchId", "select", null, null, "build.field.branch", "branches", null, null),
            new MetaView.MetaField("executionId", "select", null, null, "build.field.execution", null, null, null),
            new MetaView.MetaField("scmPath", "text", null, 255, "build.field.scmPath", null, null, null),
            new MetaView.MetaField("filePath", "text", null, 255, "build.field.filePath", null, null, null),
            new MetaView.MetaField("buildDate", "date", true, null, "build.field.buildDate", null, null, null),
            new MetaView.MetaField("builder", "account", true, null, "build.field.builder", "accounts", null, null),
            new MetaView.MetaField("description", "richtext", null, null, "build.field.description", null, null, null)),
        new MetaView.MetaList(List.of("id", "name", "branchId", "buildDate", "builder"), "-id"),
        List.of(),
        Map.of()));
  }
}
