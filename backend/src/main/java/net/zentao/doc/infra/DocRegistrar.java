package net.zentao.doc.infra;

import java.util.List;
import java.util.Map;
import net.zentao.platform.meta.MetaRegistry;
import net.zentao.platform.meta.MetaView;
import net.zentao.platform.rbac.PrivilegeCatalog;
import net.zentao.platform.search.SearchRegistry;
import net.zentao.platform.search.SearchScope;
import net.zentao.platform.workflow.WorkflowRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * doc → platform 反向注册（A3 不破坏：域主动调用 platform 注册口）。
 * 权限码：doc 卡 §5 全集（doc-space-* 与 doc-*）；meta：doc（字段 + 动作 + 状态可视化，动作由 workflow/doc.yml 导出）、
 * docSpace（无状态机，动作空）；搜索 scope：doc（searchable = title/keywords/当前正文）。
 */
@Configuration
public class DocRegistrar {

  public DocRegistrar(MetaRegistry metaRegistry, PrivilegeCatalog privilegeCatalog,
      WorkflowRegistry workflowRegistry, SearchRegistry searchRegistry, DocSearcher docSearcher) {
    privilegeCatalog.register("docSpace", List.of(
        "doc-space-view", "doc-space-create", "doc-space-edit", "doc-space-delete"));
    privilegeCatalog.register("doc", List.of(
        "doc-view", "doc-create", "doc-edit", "doc-delete"));

    searchRegistry.register(new SearchScope("doc", List.of("title", "keywords", "content"), docSearcher));

    metaRegistry.register("docSpace", new MetaView(
        "docSpace",
        List.of(
            new MetaView.MetaField("name", "text", true, 60, "docSpace.field.name", null, null, null),
            new MetaView.MetaField("type", "select", true, null, "docSpace.type", null, null, List.of(
                Map.of("value", "product", "i18n", "docSpace.type.product"),
                Map.of("value", "project", "i18n", "docSpace.type.project"),
                Map.of("value", "execution", "i18n", "docSpace.type.execution"),
                Map.of("value", "custom", "i18n", "docSpace.type.custom"),
                Map.of("value", "mine", "i18n", "docSpace.type.mine"))),
            new MetaView.MetaField("productId", "select", null, null, "docSpace.field.product", "products", null, null),
            new MetaView.MetaField("projectId", "select", null, null, "docSpace.field.project", "projects", null, null),
            new MetaView.MetaField("executionId", "select", null, null, "docSpace.field.execution", "executions", null, null),
            new MetaView.MetaField("acl", "select", true, null, "docSpace.acl", null, null, List.of(
                Map.of("value", "open", "i18n", "docSpace.acl.open"),
                Map.of("value", "default", "i18n", "docSpace.acl.default"),
                Map.of("value", "private", "i18n", "docSpace.acl.private"))),
            new MetaView.MetaField("whitelist", "multiselect", null, null, "docSpace.field.whitelist", "accounts", true, null),
            new MetaView.MetaField("description", "textarea", null, null, "docSpace.field.description", null, null, null),
            new MetaView.MetaField("docSort", "select", null, null, "docSpace.field.docSort", null, null, List.of(
                Map.of("value", "id_asc", "i18n", "docSpace.docSort.id_asc"),
                Map.of("value", "id_desc", "i18n", "docSpace.docSort.id_desc"))),
            new MetaView.MetaField("isDefault", "checkbox", null, null, "docSpace.field.isDefault", null, null, null),
            new MetaView.MetaField("sort", "number", null, null, "docSpace.field.sort", null, null, null)),
        new MetaView.MetaList(List.of("id", "name", "type", "acl", "docCount", "createdBy"), "-id"),
        List.of(),
        Map.of()));

    metaRegistry.register("doc", new MetaView(
        "doc",
        List.of(
            new MetaView.MetaField("title", "text", true, 255, "doc.field.title", null, null, null),
            new MetaView.MetaField("keywords", "text", null, 255, "doc.field.keywords", null, null, null),
            new MetaView.MetaField("docSpaceId", "select", true, null, "doc.field.docSpace", null, null, null),
            new MetaView.MetaField("categoryId", "categoryTree", null, null, "doc.field.category", null, null, null),
            new MetaView.MetaField("parentId", "select", null, null, "doc.field.parent", null, null, null),
            new MetaView.MetaField("type", "select", true, null, "doc.field.type", null, null, List.of(
                Map.of("value", "markdown", "i18n", "doc.type.markdown"),
                Map.of("value", "html", "i18n", "doc.type.html"))),
            new MetaView.MetaField("acl", "select", true, null, "doc.acl", null, null, List.of(
                Map.of("value", "open", "i18n", "doc.acl.open"),
                Map.of("value", "private", "i18n", "doc.acl.private"))),
            // 列表筛选值域（doc 卡 §3.2 filterable：status）——前端状态下拉只认这里的选项。
            new MetaView.MetaField("status", "select", null, null, "doc.field.status", null, null, List.of(
                Map.of("value", "draft", "i18n", "doc.status.draft"),
                Map.of("value", "published", "i18n", "doc.status.published"))),
            new MetaView.MetaField("editors", "multiselect", null, null, "doc.field.editors", "accounts", true, null),
            new MetaView.MetaField("readers", "multiselect", null, null, "doc.field.readers", "accounts", true, null),
            new MetaView.MetaField("notifyAccounts", "accounts", null, null, "doc.field.notify", "accounts", true, null),
            new MetaView.MetaField("content", "richtext", null, null, "doc.field.content", null, null, null),
            new MetaView.MetaField("files", "file", null, null, "doc.field.files", null, true, null),
            new MetaView.MetaField("version", "number", null, null, "doc.field.version", null, null, null),
            new MetaView.MetaField("views", "number", null, null, "doc.field.views", null, null, null),
            new MetaView.MetaField("sort", "number", null, null, "doc.field.sort", null, null, null)),
        new MetaView.MetaList(List.of("id", "title", "status", "version", "views", "createdBy", "updatedAt"), "-id"),
        workflowRegistry.actionsOf("doc"),
        Map.of(
            "draft", new MetaView.MetaStatusVisual("wait", "doc.status.draft"),
            "published", new MetaView.MetaStatusVisual("active", "doc.status.published"))));
  }
}
