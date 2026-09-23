package net.zentao.doc.infra;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.doc.app.DocAccess;
import net.zentao.doc.domain.DocCategoryRepository;
import net.zentao.doc.domain.DocRepository;
import net.zentao.doc.domain.DocSpaceRepository;
import net.zentao.platform.activity.ObjectVisibilityRegistry;
import net.zentao.platform.audit.AuditCatalog;
import net.zentao.platform.audit.AuditCategory;
import net.zentao.platform.audit.AuditLevel;
import net.zentao.platform.audit.AuditSnapshotRegistry;
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
 * docSpace（无状态机，动作空）；搜索 scope：doc（searchable = title/keywords/当前正文）；对象可见性：doc（platform 卡 §7.2）。
 */
@Configuration
public class DocRegistrar {

  public DocRegistrar(MetaRegistry metaRegistry, PrivilegeCatalog privilegeCatalog,
      WorkflowRegistry workflowRegistry, SearchRegistry searchRegistry, DocSearcher docSearcher,
      ObjectVisibilityRegistry visibilityRegistry, DocAccess docAccess, DocRepository docRepository,
      DocSpaceRepository docSpaceRepository, DocCategoryRepository docCategoryRepository,
      AuditCatalog auditCatalog, AuditSnapshotRegistry auditSnapshots) {
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

    // 对象可见性（T49 / platform 卡 §7.2）：文档可读（库可见 + 文档 ACL）即可见，语义同 requireReadableDoc
    visibilityRegistry.register("doc", docAccess::canRead);

    // ── 审计分级（T10 / VISION 事项 4 第 4 行「业务增删改 → 对象级 + 关键字段 diff」）──
    // 分类 + 关键字段（@AuditDiff 留空时回落这里）；create 不登记 diff 字段（没有旧值可比）
    auditCatalog.register("doc-create", AuditCategory.BUSINESS, AuditLevel.FULL, List.of(), false);
    auditCatalog.register("doc-update", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("title", "categoryId", "parentId"), false);
    // 存草稿只动 v0 正文与 updatedBy，主表关键字段不动；仍挂 diff，采到了就说明真有字段被改
    auditCatalog.register("doc-save-draft", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("title", "categoryId", "parentId"), false);
    auditCatalog.register("doc-move", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("docSpaceId", "categoryId", "parentId"), false);
    auditCatalog.register("doc-delete", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("title", "status", "version"), false);
    // 发布是审批类：除常规 diff 外还要前后整快照（框架写进 snapshot 列，VISION 事项 4 第 9 行）
    auditCatalog.register("doc-publish", AuditCategory.APPROVE, AuditLevel.FULL,
        List.of("status", "version", "title"), true);
    auditCatalog.register("doc-space-create", AuditCategory.BUSINESS, AuditLevel.FULL, List.of(), false);
    auditCatalog.register("doc-space-update", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "acl", "docSort", "isDefault", "sort"), false);
    auditCatalog.register("doc-space-delete", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "type"), false);
    auditCatalog.register("doc-category-create", AuditCategory.BUSINESS, AuditLevel.FULL, List.of(), false);
    auditCatalog.register("doc-category-update", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "parentId", "sort"), false);
    auditCatalog.register("doc-category-delete", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "parentId"), false);

    // 快照 provider：**每次返回新 Map**（框架留着 before 再取 after 比对，同一个可变 Map 会让 diff 恒为空）；
    // 字段用 LinkedHashMap 装（Map.of 不收 null，而 name/acl 这类字段可以为空）
    // 正文与描述类长文本一律不进快照（content/description）：审计表只追加且留 180 天，「干脆不取」比「取了再掩码」更稳
    auditSnapshots.register("doc", docId -> docRepository.findActiveById(docId)
        .map(doc -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("title", doc.title());
          snapshot.put("status", doc.status());
          snapshot.put("docSpaceId", doc.docSpaceId());
          snapshot.put("categoryId", doc.categoryId());
          snapshot.put("parentId", doc.parentId());
          snapshot.put("version", doc.version());
          return snapshot;
        })
        .orElse(null));
    auditSnapshots.register("docSpace", spaceId -> docSpaceRepository.findActiveById(spaceId)
        .map(space -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("name", space.name());
          snapshot.put("type", space.type());
          snapshot.put("acl", space.acl());
          snapshot.put("docSort", space.docSort());
          snapshot.put("isDefault", space.isDefault());
          snapshot.put("sort", space.sort());
          return snapshot;
        })
        .orElse(null));
    auditSnapshots.register("docCategory", categoryId -> docCategoryRepository.findById(categoryId)
        .map(category -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("name", category.name());
          snapshot.put("parentId", category.parentId());
          snapshot.put("sort", category.sort());
          return snapshot;
        })
        .orElse(null));
  }
}
