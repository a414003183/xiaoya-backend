package net.zentao.product.infra;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.platform.audit.AuditCatalog;
import net.zentao.platform.audit.AuditCategory;
import net.zentao.platform.audit.AuditLevel;
import net.zentao.platform.audit.AuditSnapshotRegistry;
import net.zentao.platform.meta.MetaRegistry;
import net.zentao.platform.meta.MetaView;
import net.zentao.platform.rbac.PrivilegeCatalog;
import net.zentao.platform.workflow.WorkflowRegistry;
import net.zentao.product.domain.BranchRepository;
import net.zentao.product.domain.BuildRepository;
import net.zentao.product.domain.CategoryRepository;
import net.zentao.product.domain.PlanRepository;
import net.zentao.product.domain.ProductRepository;
import net.zentao.product.domain.ReleaseRepository;
import org.springframework.context.annotation.Configuration;

/**
 * product → platform 反向注册（A3 不破坏：域主动调用 platform 注册口）。
 * 权限码：product 卡 §5 六族全集；meta：六实体（本类随 T-2/T-6/T-8/T-9 逐个补齐）。
 */
@Configuration
public class ProductRegistrar {

  public ProductRegistrar(MetaRegistry metaRegistry, PrivilegeCatalog privilegeCatalog,
      WorkflowRegistry workflowRegistry, ProductRepository productRepository,
      BranchRepository branchRepository, BuildRepository buildRepository, CategoryRepository categoryRepository,
      PlanRepository planRepository, ReleaseRepository releaseRepository, AuditCatalog auditCatalog,
      AuditSnapshotRegistry auditSnapshots) {
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

    // ── 审计分级（T10 / VISION 事项 4 第 4 行「业务增删改 → 对象级 + 关键字段 diff」）──
    // 分类 + 关键字段（@AuditDiff 留空时回落这里，字段名必须与下面 provider 的 Map 键同名）；
    // create 无旧值可比、关系类动作对象自身关键字段不变，两者只记「发生了」
    auditCatalog.register("product-create", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("product-update", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "code", "type", "acl", "whitelist", "programId", "po", "qd", "rd"), false);
    auditCatalog.register("product-close", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
    auditCatalog.register("product-activate", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
    auditCatalog.register("product-delete", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("name", "status"), false);

    auditCatalog.register("branch-create", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("branch-update", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("name", "sort"), false);
    auditCatalog.register("branch-close", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
    auditCatalog.register("branch-activate", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
    auditCatalog.register("branch-set-default", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("branch-delete", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "isDefault", "status"), false);

    auditCatalog.register("build-create", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("build-update", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "branchId", "buildDate", "builder"), false);
    auditCatalog.register("build-delete", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "branchId", "buildDate", "builder"), false);
    auditCatalog.register("build-link", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("build-unlink", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);

    auditCatalog.register("category-create", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("category-update", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "parentId", "owner", "sort"), false);
    auditCatalog.register("category-delete", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "type", "owner"), false);

    auditCatalog.register("plan-create", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("plan-update", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("title", "branchId", "parentId", "beginDate", "endDate"), false);
    auditCatalog.register("plan-start", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
    auditCatalog.register("plan-finish", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
    auditCatalog.register("plan-close", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("status", "closedReason"), false);
    auditCatalog.register("plan-activate", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
    auditCatalog.register("plan-delete", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("title", "status"), false);
    auditCatalog.register("plan-link", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("plan-unlink", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);

    auditCatalog.register("release-create", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("release-update", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "branchId", "buildId", "releaseDate", "isMilestone"), false);
    auditCatalog.register("release-terminate", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
    auditCatalog.register("release-delete", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("name", "status"), false);
    auditCatalog.register("release-link", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("release-unlink", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);

    // 快照 provider：**每次返回新 Map**（框架留着 before 再取 after 比对，同一个可变 Map 会让 diff 恒为空）；
    // 字段用 LinkedHashMap 装（Map.of 不收 null，而 po/rd/owner/buildId 这类字段可以为空）
    auditSnapshots.register("product", productId -> productRepository.findActiveById(productId)
        .map(product -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("name", product.name());
          snapshot.put("code", product.code());
          snapshot.put("type", product.type());
          snapshot.put("status", product.status());
          snapshot.put("acl", product.acl());
          snapshot.put("whitelist", product.whitelist());
          snapshot.put("programId", product.programId());
          snapshot.put("po", product.po());
          snapshot.put("qd", product.qd());
          snapshot.put("rd", product.rd());
          return snapshot;
        })
        .orElse(null));

    auditSnapshots.register("branch", branchId -> branchRepository.findActiveById(branchId)
        .map(branch -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("name", branch.name());
          snapshot.put("productId", branch.productId());
          snapshot.put("isDefault", branch.isDefault());
          snapshot.put("status", branch.status());
          snapshot.put("sort", branch.sort());
          return snapshot;
        })
        .orElse(null));

    auditSnapshots.register("build", buildId -> buildRepository.findActiveById(buildId)
        .map(build -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("name", build.name());
          snapshot.put("branchId", build.branchId());
          snapshot.put("projectId", build.projectId());
          snapshot.put("buildDate", build.buildDate());
          snapshot.put("builder", build.builder());
          return snapshot;
        })
        .orElse(null));

    auditSnapshots.register("category", categoryId -> categoryRepository.findActiveById(categoryId)
        .map(category -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("name", category.name());
          snapshot.put("type", category.type());
          snapshot.put("productId", category.productId());
          snapshot.put("parentId", category.parentId());
          snapshot.put("owner", category.owner());
          snapshot.put("sort", category.sort());
          return snapshot;
        })
        .orElse(null));

    auditSnapshots.register("plan", planId -> planRepository.findActiveById(planId)
        .map(plan -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("title", plan.title());
          snapshot.put("productId", plan.productId());
          snapshot.put("branchId", plan.branchId());
          snapshot.put("parentId", plan.parentId());
          snapshot.put("status", plan.status());
          snapshot.put("beginDate", plan.beginDate());
          snapshot.put("endDate", plan.endDate());
          snapshot.put("closedReason", plan.closedReason());
          return snapshot;
        })
        .orElse(null));

    auditSnapshots.register("release", releaseId -> releaseRepository.findActiveById(releaseId)
        .map(release -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("name", release.name());
          snapshot.put("productId", release.productId());
          snapshot.put("branchId", release.branchId());
          snapshot.put("buildId", release.buildId());
          snapshot.put("status", release.status());
          snapshot.put("releaseDate", release.releaseDate());
          snapshot.put("isMilestone", release.isMilestone());
          return snapshot;
        })
        .orElse(null));
  }
}
