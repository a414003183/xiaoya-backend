package net.zentao.requirement.infra;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.zentao.product.api.ProductApi;
import net.zentao.requirement.app.StoryQueryService;
import net.zentao.requirement.domain.StoryRepository;
import org.springframework.context.annotation.Configuration;

/**
 * requirement → platform 反向注册（A3 不破坏：域主动调用 platform 注册口）。
 * 权限码：requirement 卡 §5 全集；meta：story（字段 + 动作 + 状态可视化）；搜索 scope：story/epic/requirement；
 * 对象可见性：story（附件/评论的行级数据权限，platform 卡 §7.2）。
 */
@Configuration
public class StoryRegistrar {

  public StoryRegistrar(MetaRegistry metaRegistry, PrivilegeCatalog privilegeCatalog,
      WorkflowRegistry workflowRegistry, SearchRegistry searchRegistry, StoryQueryService queryService,
      ObjectVisibilityRegistry visibilityRegistry, StoryRepository storyRepository, ProductApi productApi,
      AuditCatalog auditCatalog, AuditSnapshotRegistry auditSnapshots) {
    privilegeCatalog.register("story", List.of(
        "story-view", "story-create", "story-edit", "story-submit-review", "story-pass", "story-change",
        "story-close", "story-activate", "story-assign", "story-delete"));

    metaRegistry.register("story", new MetaView(
        "story",
        List.of(
            new MetaView.MetaField("title", "text", true, 255, "story.field.title", null, null, null),
            new MetaView.MetaField("type", "select", true, null, "story.field.type", null, null, List.of(
                Map.of("value", "story", "i18n", "story.type.story"),
                Map.of("value", "epic", "i18n", "story.type.epic"),
                Map.of("value", "requirement", "i18n", "story.type.requirement"))),
            new MetaView.MetaField("priority", "select", true, null, "common.priority", null, null, List.of(
                Map.of("value", 1, "i18n", "common.priority.1"),
                Map.of("value", 2, "i18n", "common.priority.2"),
                Map.of("value", 3, "i18n", "common.priority.3"),
                Map.of("value", 4, "i18n", "common.priority.4"))),
            new MetaView.MetaField("categoryId", "categoryTree", null, null, "story.field.category", null, null, null),
            new MetaView.MetaField("planId", "select", null, null, "story.field.plan", "plans", null, null),
            new MetaView.MetaField("estimateHours", "decimal", null, null, "story.field.estimate", null, null, null),
            new MetaView.MetaField("source", "select", null, null, "story.field.source", null, null, List.of(
                Map.of("value", "manual", "i18n", "story.source.manual"),
                Map.of("value", "customer", "i18n", "story.source.customer"),
                Map.of("value", "market", "i18n", "story.source.market"),
                Map.of("value", "bug", "i18n", "story.source.bug"),
                Map.of("value", "other", "i18n", "story.source.other"))),
            // 列表筛选值域（requirement §3 filterable：status/stage）——前端下拉只认这里的选项。
            new MetaView.MetaField("status", "select", null, null, "story.field.status", null, null, List.of(
                Map.of("value", "draft", "i18n", "story.status.draft"),
                Map.of("value", "reviewing", "i18n", "story.status.reviewing"),
                Map.of("value", "active", "i18n", "story.status.active"),
                Map.of("value", "changing", "i18n", "story.status.changing"),
                Map.of("value", "changed", "i18n", "story.status.changed"),
                Map.of("value", "closed", "i18n", "story.status.closed"))),
            new MetaView.MetaField("stage", "select", null, null, "story.field.stage", null, null, List.of(
                Map.of("value", "wait", "i18n", "story.stage.wait"),
                Map.of("value", "developing", "i18n", "story.stage.developing"),
                Map.of("value", "testing", "i18n", "story.stage.testing"),
                Map.of("value", "released", "i18n", "story.stage.released"))),
            // 关闭原因值域（§4 close 动作请求体 closedReason）——关闭弹窗与批量页选项只认这里。
            new MetaView.MetaField("closedReason", "select", null, null, "story.field.closedReason", null, null,
                List.of(
                    Map.of("value", "done", "i18n", "story.closeReason.done"),
                    Map.of("value", "duplicate", "i18n", "story.closeReason.duplicate"),
                    Map.of("value", "rejected", "i18n", "story.closeReason.rejected"),
                    Map.of("value", "willnotfix", "i18n", "story.closeReason.willnotfix"),
                    Map.of("value", "postponed", "i18n", "story.closeReason.postponed"))),
            new MetaView.MetaField("description", "richtext", null, null, "story.field.description", null, null, null),
            new MetaView.MetaField("assignee", "account", null, null, "story.field.assignee", "accounts", null, null),
            new MetaView.MetaField("reviewers", "accounts", null, null, "story.field.reviewers", "accounts", true, null),
            new MetaView.MetaField("needNotReview", "checkbox", null, null, "story.field.needNotReview", null, null, null),
            new MetaView.MetaField("notifyAccounts", "accounts", null, null, "story.field.notify", "accounts", true, null),
            new MetaView.MetaField("linkedStoryIds", "multiselect", null, null, "story.field.linked", "stories", true, null)),
        new MetaView.MetaList(List.of("id", "title", "priority", "status", "assignee", "createdBy"), "-id"),
        workflowRegistry.actionsOf("story"),
        Map.of(
            "draft", new MetaView.MetaStatusVisual("wait", "story.status.draft"),
            "reviewing", new MetaView.MetaStatusVisual("doing", "story.status.reviewing"),
            "active", new MetaView.MetaStatusVisual("active", "story.status.active"),
            "changing", new MetaView.MetaStatusVisual("doing", "story.status.changing"),
            "changed", new MetaView.MetaStatusVisual("active", "story.status.changed"),
            "closed", new MetaView.MetaStatusVisual("closed", "story.status.closed"))));

    // 搜索 scope（platform 卡 §5.2）：story/epic/requirement 三型分别注册，DataScope 在查询内先行注入
    searchRegistry.register(new SearchScope("story", List.of("title", "keywords"),
        (q, limit, principal) -> queryService.search(q, limit, principal, "story")));
    searchRegistry.register(new SearchScope("epic", List.of("title", "keywords"),
        (q, limit, principal) -> queryService.search(q, limit, principal, "epic")));
    searchRegistry.register(new SearchScope("requirement", List.of("title", "keywords"),
        (q, limit, principal) -> queryService.search(q, limit, principal, "requirement")));

    // 对象可见性（T49 / platform 卡 §7.2）：需求可见 ⇔ 其产品可见（与列表 DataScope 同口径）；查无此行 = 不可见
    visibilityRegistry.register("story", (principal, storyId) -> storyRepository.findActiveById(storyId)
        .map(story -> productApi.canAccess(principal, story.productId()))
        .orElse(false));

    // ── 审计分级（T10 / VISION 事项 4 第 4 行「业务增删改 → 对象级 + 关键字段 diff」）──
    // keyFields 只列审计关心的字段（标题/状态/负责人/优先级/归属 id），与下面 provider 的 Map 键同名
    auditCatalog.register("story-create", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("story-update", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("title", "priority", "estimateHours", "categoryId", "planId", "parentId"), false);
    auditCatalog.register("story-delete", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("title", "status", "assignee"), false);
    // 评审类（submit-review/pass/reject）走审批口径：除 diff 外整快照留档，回答「审的是哪一版」
    auditCatalog.register("story-submit-review", AuditCategory.APPROVE, AuditLevel.FULL,
        List.of("status", "reviewers"), true);
    auditCatalog.register("story-pass", AuditCategory.APPROVE, AuditLevel.FULL, List.of("status"), true);
    auditCatalog.register("story-reject", AuditCategory.APPROVE, AuditLevel.FULL, List.of("status"), true);
    auditCatalog.register("story-change", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
    auditCatalog.register("story-change-done", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("status", "title", "priority", "estimateHours", "categoryId", "planId", "parentId"), false);
    auditCatalog.register("story-close", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("status", "closedReason", "duplicateOfId"), false);
    auditCatalog.register("story-activate", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("status", "closedReason"), false);
    auditCatalog.register("story-assign", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("assignee"), false);

    // 快照 provider：**每次返回新 Map**（框架留着 before 再取 after 比对，同一个可变 Map 会让 diff 恒为空）；
    // 用 LinkedHashMap 装（Map.of 不收 null，而 assignee/planId 这类字段可以为空）
    auditSnapshots.register("story", storyId -> storyRepository.findActiveById(storyId)
        .map(story -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("title", story.title());
          snapshot.put("type", story.type());
          snapshot.put("status", story.status());
          snapshot.put("stage", story.stage());
          snapshot.put("priority", story.priority());
          snapshot.put("estimateHours", story.estimateHours());
          snapshot.put("source", story.source());
          snapshot.put("productId", story.productId());
          snapshot.put("categoryId", story.categoryId());
          snapshot.put("planId", story.planId());
          snapshot.put("parentId", story.parentId());
          snapshot.put("assignee", story.assignee());
          snapshot.put("reviewers", story.reviewers());
          snapshot.put("closedReason", story.closedReason());
          snapshot.put("duplicateOfId", story.duplicateOfId());
          // 描述/关键词是长正文，不进快照：审计要能定位「改了什么」，不是留全文
          return snapshot;
        })
        .orElse(null));
  }
}
