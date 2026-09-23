package net.zentao.project.infra;

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
import net.zentao.project.domain.BoardRepository;
import net.zentao.project.domain.BoardSpaceRepository;
import net.zentao.project.domain.CardRepository;
import net.zentao.project.domain.ProjectRepository;
import net.zentao.project.domain.StageRepository;
import org.springframework.context.annotation.Configuration;

/**
 * project → platform 反向注册（A3 不破坏：域主动调用 platform 注册口）。
 * 权限码：project 卡 §5 全域全集（stakeholder/board/team 族由 T-4/T-6 端点消费，同源登记于此）。
 */
@Configuration
public class ProjectRegistrar {

  /** 三型 PATCH 可比字段（描述是长文本、parentId 不随 PATCH 变，均不入 diff）。 */
  private static final List<String> PROJECT_DIFF_FIELDS = List.of("name", "code", "model", "status", "priority",
      "pm", "po", "qd", "rd", "beginDate", "endDate", "days", "budget", "acl", "whitelist", "isMilestone");

  public ProjectRegistrar(MetaRegistry metaRegistry, PrivilegeCatalog privilegeCatalog,
      WorkflowRegistry workflowRegistry, ProjectRepository projectRepository, StageRepository stageRepository,
      BoardSpaceRepository boardSpaceRepository, BoardRepository boardRepository, CardRepository cardRepository,
      AuditCatalog auditCatalog, AuditSnapshotRegistry auditSnapshots) {
    privilegeCatalog.register("program", List.of(
        "program-view", "program-create", "program-edit", "program-delete", "program-start", "program-suspend",
        "program-resume", "program-delay", "program-close", "program-activate"));
    privilegeCatalog.register("project", List.of(
        "project-view", "project-create", "project-edit", "project-delete", "project-start", "project-suspend",
        "project-resume", "project-delay", "project-close", "project-activate", "project-manage-members",
        "project-link-story", "project-whitelist"));
    privilegeCatalog.register("execution", List.of(
        "execution-view", "execution-create", "execution-edit", "execution-delete", "execution-start",
        "execution-suspend", "execution-resume", "execution-delay", "execution-close", "execution-activate",
        "execution-manage-members"));
    privilegeCatalog.register("stage", List.of("stage-view", "stage-manage"));
    privilegeCatalog.register("stakeholder", List.of("stakeholder-view", "stakeholder-manage"));
    privilegeCatalog.register("board", List.of(
        "board-view", "board-space-create", "board-space-edit", "board-space-close", "board-create", "board-edit",
        "board-close", "board-card-create", "board-card-edit"));

    metaRegistry.register("program", entityMeta("program", "program", workflowRegistry, List.of(
        Map.of("value", "program", "i18n", "project.type.program"))));
    metaRegistry.register("project", entityMeta("project", "project", workflowRegistry, List.of(
        Map.of("value", "project", "i18n", "project.type.project"))));
    metaRegistry.register("execution", entityMeta("execution", "execution", workflowRegistry, List.of(
        Map.of("value", "sprint", "i18n", "project.type.sprint"),
        Map.of("value", "stage", "i18n", "project.type.stage"),
        Map.of("value", "kanban", "i18n", "project.type.kanban"))));

    metaRegistry.register("stage", stageMeta());
    metaRegistry.register("stakeholder", stakeholderMeta());
    metaRegistry.register("board_space", boardSpaceMeta(workflowRegistry));
    metaRegistry.register("board", boardMeta(workflowRegistry));
    metaRegistry.register("lane", laneMeta());
    metaRegistry.register("card", cardMeta());

    // ── 审计分级（T10 / VISION 事项 4 第 4 行「业务增删改 → 对象级 + 关键字段 diff」）──
    registerAudit(auditCatalog, auditSnapshots, projectRepository, stageRepository, boardSpaceRepository,
        boardRepository, cardRepository);
  }

  /**
   * 审计动作目录登记 + 各资源快照 provider：动作名/分类/字段清单在此一处声明（控制器只挂注解）。
   */
  private static void registerAudit(AuditCatalog auditCatalog, AuditSnapshotRegistry auditSnapshots,
      ProjectRepository projectRepository, StageRepository stageRepository, BoardSpaceRepository boardSpaceRepository,
      BoardRepository boardRepository, CardRepository cardRepository) {
    // 三型同构（program/project/execution 一表三义）：动词取路由，字段口径同一份——批量登记保证三型不漂移。
    // execution 无独立创建端点（建执行走 projects 族），故 create 单独登记。
    for (String resource : List.of("program", "project", "execution")) {
      auditCatalog.register(resource + "-update", AuditCategory.BUSINESS, AuditLevel.FULL, PROJECT_DIFF_FIELDS, false);
      auditCatalog.register(resource + "-delete", AuditCategory.BUSINESS, AuditLevel.FULL,
          List.of("name", "status", "parentId"), false);
      auditCatalog.register(resource + "-start", AuditCategory.BUSINESS, AuditLevel.FULL,
          List.of("status", "realBeganDate"), false);
      auditCatalog.register(resource + "-suspend", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
      auditCatalog.register(resource + "-resume", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
      auditCatalog.register(resource + "-delay", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
      auditCatalog.register(resource + "-close", AuditCategory.BUSINESS, AuditLevel.FULL,
          List.of("status", "realEndDate"), false);
      auditCatalog.register(resource + "-activate", AuditCategory.BUSINESS, AuditLevel.FULL,
          List.of("status", "beginDate", "endDate", "realEndDate"), false);
    }
    auditCatalog.register("program-create", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("project-create", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);

    // 关系/归属类（干系人、成员、关联产品/需求、白名单、建执行）：只记「发生了」——被改的是关系表，
    // 对象自身字段不变，没有可比的前后值（VISION 事项 4 第 4 行只要求对象级留痕）。
    for (String action : List.of("program-stakeholder-add", "program-stakeholder-remove", "project-stakeholder-add",
        "project-stakeholder-remove", "project-product-replace", "project-story-link", "project-story-unlink",
        "project-member-submit", "project-whitelist-replace", "project-execution-create", "execution-member-submit",
        "execution-story-unlink", "execution-card-move")) {
      auditCatalog.register(action, AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    }

    auditCatalog.register("stage-create", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("stage-update", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "percent", "type", "sort"), false);
    auditCatalog.register("stage-delete", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "percent", "type"), false);

    auditCatalog.register("board-space-create", AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    auditCatalog.register("board-space-update", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "type", "owner", "team", "acl", "whitelist", "sort"), false);
    auditCatalog.register("board-space-close", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
    auditCatalog.register("board-space-activate", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
    auditCatalog.register("board-space-delete", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "status"), false);
    for (String action : List.of("board-space-board-create", "board-lane-create", "board-lane-update",
        "board-lane-delete", "board-card-create", "card-move")) {
      auditCatalog.register(action, AuditCategory.BUSINESS, AuditLevel.SUMMARY, List.of(), false);
    }
    auditCatalog.register("board-update", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "owner", "team", "acl", "whitelist", "sort"), false);
    auditCatalog.register("board-close", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
    auditCatalog.register("board-activate", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("status"), false);
    auditCatalog.register("board-delete", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("name", "status"), false);
    auditCatalog.register("card-update", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "status", "priority", "assignee", "beginDate", "endDate", "estimateHours", "progress"),
        false);
    auditCatalog.register("card-archive", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("archived"), false);
    auditCatalog.register("card-unarchive", AuditCategory.BUSINESS, AuditLevel.FULL, List.of("archived"), false);
    auditCatalog.register("card-delete", AuditCategory.BUSINESS, AuditLevel.FULL,
        List.of("name", "laneId", "status"), false);

    // 快照 provider：**每次返回新 Map**（框架留着 before 再取 after 比对，同一个可变 Map 会让 diff 恒为空）；
    // 字段用 LinkedHashMap 装（Map.of 不收 null，而 code/pm/realBeganDate 这类字段可以为空）。
    // 只取审计关心的标量与访问配置：描述正文一律不入快照（审计表只追加且留 180 天）。
    for (String resource : List.of("program", "project", "execution")) {
      auditSnapshots.register(resource, id -> projectSnapshot(projectRepository, id));
    }
    auditSnapshots.register("stage", stageId -> stageRepository.findActiveById(stageId)
        .map(stage -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("name", stage.name());
          snapshot.put("percent", stage.percent());
          snapshot.put("type", stage.type());
          snapshot.put("projectModel", stage.projectModel());
          snapshot.put("sort", stage.sort());
          return snapshot;
        })
        .orElse(null));
    auditSnapshots.register("boardSpace", boardSpaceId -> boardSpaceRepository.findActiveById(boardSpaceId)
        .map(space -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("name", space.name());
          snapshot.put("type", space.type());
          snapshot.put("owner", space.owner());
          snapshot.put("team", space.team());
          snapshot.put("acl", space.acl());
          snapshot.put("whitelist", space.whitelist());
          snapshot.put("status", space.status());
          snapshot.put("sort", space.sort());
          return snapshot;
        })
        .orElse(null));
    auditSnapshots.register("board", boardId -> boardRepository.findActiveById(boardId)
        .map(board -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("spaceId", board.spaceId());
          snapshot.put("name", board.name());
          snapshot.put("owner", board.owner());
          snapshot.put("team", board.team());
          snapshot.put("acl", board.acl());
          snapshot.put("whitelist", board.whitelist());
          snapshot.put("status", board.status());
          snapshot.put("sort", board.sort());
          return snapshot;
        })
        .orElse(null));
    auditSnapshots.register("card", cardId -> cardRepository.findActiveById(cardId)
        .map(card -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("laneId", card.laneId());
          snapshot.put("name", card.name());
          snapshot.put("status", card.status());
          snapshot.put("priority", card.priority());
          snapshot.put("assignee", card.assignee());
          snapshot.put("beginDate", card.beginDate());
          snapshot.put("endDate", card.endDate());
          snapshot.put("estimateHours", card.estimateHours());
          snapshot.put("progress", card.progress());
          snapshot.put("archived", card.archived());
          return snapshot;
        })
        .orElse(null));
  }

  /** 三型共用的项目快照口径（program/project/execution 同表，只按 id 找，不要求 principal）。 */
  private static Map<String, Object> projectSnapshot(ProjectRepository repository, long id) {
    return repository.findActiveById(id)
        .map(project -> {
          Map<String, Object> snapshot = new LinkedHashMap<>();
          snapshot.put("name", project.name());
          snapshot.put("code", project.code());
          snapshot.put("model", project.model());
          snapshot.put("status", project.status());
          snapshot.put("priority", project.priority());
          snapshot.put("parentId", project.parentId());
          snapshot.put("pm", project.pm());
          snapshot.put("po", project.po());
          snapshot.put("qd", project.qd());
          snapshot.put("rd", project.rd());
          snapshot.put("beginDate", project.beginDate());
          snapshot.put("endDate", project.endDate());
          snapshot.put("realBeganDate", project.realBeganDate());
          snapshot.put("realEndDate", project.realEndDate());
          snapshot.put("days", project.days());
          snapshot.put("budget", project.budget());
          snapshot.put("acl", project.acl());
          snapshot.put("whitelist", project.whitelist());
          snapshot.put("isMilestone", project.isMilestone());
          return snapshot;
        })
        .orElse(null);
  }

  /** 阶段类型字典 meta（§3.2）：无状态机（actions 空），percent 超限由端点守卫。 */
  private static MetaView stageMeta() {
    return new MetaView(
        "stage",
        List.of(
            new MetaView.MetaField("name", "text", true, 255, "stage.field.name", null, null, null),
            new MetaView.MetaField("percent", "number", true, null, "stage.field.percent", null, null, null),
            new MetaView.MetaField("type", "select", true, null, "stage.type", null, null, List.of(
                Map.of("value", "mix", "i18n", "stage.type.mix"),
                Map.of("value", "request", "i18n", "stage.type.request"),
                Map.of("value", "design", "i18n", "stage.type.design"),
                Map.of("value", "dev", "i18n", "stage.type.dev"),
                Map.of("value", "qa", "i18n", "stage.type.qa"),
                Map.of("value", "release", "i18n", "stage.type.release"),
                Map.of("value", "review", "i18n", "stage.type.review"),
                Map.of("value", "other", "i18n", "stage.type.other"))),
            new MetaView.MetaField("projectModel", "select", true, null, "stage.field.projectModel", null, null,
                List.of(Map.of("value", "waterfall", "i18n", "project.model.waterfall"))),
            new MetaView.MetaField("sort", "number", null, null, "stage.field.sort", null, null, null)),
        new MetaView.MetaList(List.of("id", "name", "percent", "type", "projectModel", "sort"), "sort"),
        List.of(),
        Map.of());
  }

  /** 干系人 meta（§3.8）：无状态机（无 actions），type 值域 inside|outside。 */
  private static MetaView stakeholderMeta() {
    return new MetaView(
        "stakeholder",
        List.of(
            new MetaView.MetaField("account", "account", true, null, "stakeholder.field.account", "accounts", null,
                null),
            new MetaView.MetaField("type", "select", true, null, "stakeholder.field.type", null, null, List.of(
                Map.of("value", "inside", "i18n", "stakeholder.type.inside"),
                Map.of("value", "outside", "i18n", "stakeholder.type.outside"))),
            new MetaView.MetaField("isKey", "checkbox", null, null, "stakeholder.field.isKey", null, null, null),
            new MetaView.MetaField("source", "text", null, 30, "stakeholder.field.source", null, null, null)),
        new MetaView.MetaList(List.of("id", "account", "type", "isKey", "source"), "id"),
        List.of(),
        Map.of());
  }

  /** 看板空间 meta（§3.3）：二态机 board_space，动作来自 workflow/board.yml。 */
  private static MetaView boardSpaceMeta(WorkflowRegistry workflowRegistry) {
    return new MetaView(
        "board_space",
        List.of(
            new MetaView.MetaField("name", "text", true, 90, "board.field.name", null, null, null),
            new MetaView.MetaField("type", "select", true, null, "board.spaceType", null, null, List.of(
                Map.of("value", "cooperation", "i18n", "board.spaceType.cooperation"),
                Map.of("value", "public", "i18n", "board.spaceType.public"),
                Map.of("value", "private", "i18n", "board.spaceType.private"))),
            new MetaView.MetaField("owner", "account", null, null, "board.field.owner", "accounts", null, null),
            new MetaView.MetaField("team", "accounts", null, null, "board.field.team", "accounts", true, null),
            new MetaView.MetaField("acl", "select", true, null, "board.acl", null, null, List.of(
                Map.of("value", "open", "i18n", "board.acl.open"),
                Map.of("value", "private", "i18n", "board.acl.private"))),
            // 列表筛选值域（§3.3 filterable：status）——前端状态下拉只认这里的选项。
            new MetaView.MetaField("status", "select", null, null, "common.field.status", null, null, List.of(
                Map.of("value", "active", "i18n", "board.status.active"),
                Map.of("value", "closed", "i18n", "board.status.closed"))),
            new MetaView.MetaField("whitelist", "accounts", null, null, "board.field.whitelist", "accounts", true,
                null),
            new MetaView.MetaField("description", "richtext", null, null, "board.field.description", null, null, null),
            new MetaView.MetaField("sort", "number", null, null, "board.field.sort", null, null, null)),
        new MetaView.MetaList(List.of("id", "name", "type", "owner", "status", "createdAt"), "-id"),
        workflowRegistry.actionsOf("board_space"),
        Map.of(
            "active", new MetaView.MetaStatusVisual("active", "board.status.active"),
            "closed", new MetaView.MetaStatusVisual("closed", "board.status.closed")));
  }

  /** 看板 meta（§3.4）：二态机 board，动作来自 workflow/board.yml。 */
  private static MetaView boardMeta(WorkflowRegistry workflowRegistry) {
    return new MetaView(
        "board",
        List.of(
            new MetaView.MetaField("spaceId", "select", true, null, "board.field.space", "board_spaces", null, null),
            new MetaView.MetaField("name", "text", true, 90, "board.field.name", null, null, null),
            new MetaView.MetaField("owner", "account", null, null, "board.field.owner", "accounts", null, null),
            new MetaView.MetaField("team", "accounts", null, null, "board.field.team", "accounts", true, null),
            new MetaView.MetaField("acl", "select", true, null, "board.acl", null, null, List.of(
                Map.of("value", "open", "i18n", "board.acl.open"),
                Map.of("value", "private", "i18n", "board.acl.private"),
                Map.of("value", "extend", "i18n", "board.acl.extend"))),
            new MetaView.MetaField("whitelist", "accounts", null, null, "board.field.whitelist", "accounts", true,
                null),
            new MetaView.MetaField("description", "richtext", null, null, "board.field.description", null, null, null),
            new MetaView.MetaField("sort", "number", null, null, "board.field.sort", null, null, null)),
        new MetaView.MetaList(List.of("id", "spaceId", "name", "owner", "status", "createdAt"), "-id"),
        workflowRegistry.actionsOf("board"),
        Map.of(
            "active", new MetaView.MetaStatusVisual("active", "board.status.active"),
            "closed", new MetaView.MetaStatusVisual("closed", "board.status.closed")));
  }

  /** 看板列 meta（§3.5）：无状态机（archived 为标记，非状态）。 */
  private static MetaView laneMeta() {
    return new MetaView(
        "lane",
        List.of(
            new MetaView.MetaField("name", "text", true, 90, "board.field.laneName", null, null, null),
            new MetaView.MetaField("color", "text", null, null, "board.field.laneColor", null, null, null),
            new MetaView.MetaField("wipLimit", "number", null, null, "board.field.wipLimit", null, null, null),
            new MetaView.MetaField("archived", "checkbox", null, null, "board.field.archived", null, null, null),
            new MetaView.MetaField("sort", "number", null, null, "board.field.sort", null, null, null)),
        new MetaView.MetaList(List.of("id", "boardId", "name", "wipLimit", "archived", "sort"), "sort"),
        List.of(),
        Map.of());
  }

  /** 看板卡片 meta（§3.6）：状态只有 doing/done 且由 PATCH 直改（§5），故 actions 为空。 */
  private static MetaView cardMeta() {
    return new MetaView(
        "card",
        List.of(
            new MetaView.MetaField("laneId", "select", true, null, "board.field.lane", "lanes", null, null),
            new MetaView.MetaField("name", "text", true, 255, "board.field.cardName", null, null, null),
            new MetaView.MetaField("description", "richtext", null, null, "board.field.description", null, null, null),
            // 卡片状态值域（§3.6/§5：PATCH 直改 doing|done）——卡片弹窗状态下拉只认这里。
            new MetaView.MetaField("status", "select", null, null, "common.field.status", null, null, List.of(
                Map.of("value", "doing", "i18n", "board.cardStatus.doing"),
                Map.of("value", "done", "i18n", "board.cardStatus.done"))),
            new MetaView.MetaField("priority", "select", null, null, "common.priority", null, null, List.of(
                Map.of("value", 1, "i18n", "common.priority.1"),
                Map.of("value", 2, "i18n", "common.priority.2"),
                Map.of("value", 3, "i18n", "common.priority.3"),
                Map.of("value", 4, "i18n", "common.priority.4"))),
            new MetaView.MetaField("assignee", "account", null, null, "board.field.assignee", "accounts", null, null),
            new MetaView.MetaField("beginDate", "date", null, null, "board.field.beginDate", null, null, null),
            new MetaView.MetaField("endDate", "date", null, null, "board.field.endDate", null, null, null),
            new MetaView.MetaField("estimateHours", "decimal", null, null, "board.field.estimate", null, null, null),
            new MetaView.MetaField("progress", "number", null, null, "board.field.progress", null, null, null),
            new MetaView.MetaField("color", "text", null, null, "board.field.cardColor", null, null, null),
            new MetaView.MetaField("archived", "checkbox", null, null, "board.field.archived", null, null, null),
            new MetaView.MetaField("sort", "number", null, null, "board.field.sort", null, null, null)),
        new MetaView.MetaList(List.of("id", "laneId", "name", "status", "priority", "assignee", "archived"), "sort"),
        List.of(),
        Map.of(
            "doing", new MetaView.MetaStatusVisual("doing", "board.cardStatus.doing"),
            "done", new MetaView.MetaStatusVisual("active", "board.cardStatus.done")));
  }

  /** 三型同构 meta（§3.1）：字段/列表列/状态可视化同一份，动作来自各自 workflow 机。
   *  typeOptions 是该域 filters[type] 的合法值域（program/project 各只有自身一型，execution 为三型）。 */
  private static MetaView entityMeta(String domain, String i18nPrefix, WorkflowRegistry workflowRegistry,
      List<Map<String, Object>> typeOptions) {
    return new MetaView(
        domain,
        List.of(
            new MetaView.MetaField("name", "text", true, 90, i18nPrefix + ".field.name", null, null, null),
            new MetaView.MetaField("code", "text", null, 45, i18nPrefix + ".field.code", null, null, null),
            // 列表筛选值域（§3.1 filterable：type/status）——前端下拉只认这里的选项。
            new MetaView.MetaField("type", "select", null, null, i18nPrefix + ".field.type", null, null,
                typeOptions),
            new MetaView.MetaField("status", "select", null, null, i18nPrefix + ".field.status", null, null, List.of(
                Map.of("value", "wait", "i18n", "project.status.wait"),
                Map.of("value", "doing", "i18n", "project.status.doing"),
                Map.of("value", "suspended", "i18n", "project.status.suspended"),
                Map.of("value", "delay", "i18n", "project.status.delay"),
                Map.of("value", "closed", "i18n", "project.status.closed"))),
            new MetaView.MetaField("model", "select", null, null, i18nPrefix + ".field.model", null, null, List.of(
                Map.of("value", "scrum", "i18n", "project.model.scrum"),
                Map.of("value", "waterfall", "i18n", "project.model.waterfall"),
                Map.of("value", "kanban", "i18n", "project.model.kanban"))),
            new MetaView.MetaField("priority", "select", null, null, "common.priority", null, null, List.of(
                Map.of("value", 1, "i18n", "common.priority.1"),
                Map.of("value", 2, "i18n", "common.priority.2"),
                Map.of("value", 3, "i18n", "common.priority.3"),
                Map.of("value", 4, "i18n", "common.priority.4"))),
            new MetaView.MetaField("beginDate", "date", null, null, i18nPrefix + ".field.beginDate", null, null, null),
            new MetaView.MetaField("endDate", "date", null, null, i18nPrefix + ".field.endDate", null, null, null),
            new MetaView.MetaField("days", "number", null, null, i18nPrefix + ".field.days", null, null, null),
            new MetaView.MetaField("budget", "decimal", null, null, i18nPrefix + ".field.budget", null, null, null),
            new MetaView.MetaField("budgetUnit", "select", null, null, i18nPrefix + ".budgetUnit", null, null, List.of(
                Map.of("value", "CNY", "i18n", "project.budgetUnit.CNY"),
                Map.of("value", "USD", "i18n", "project.budgetUnit.USD"))),
            new MetaView.MetaField("pm", "account", null, null, i18nPrefix + ".field.pm", "accounts", null, null),
            new MetaView.MetaField("po", "account", null, null, i18nPrefix + ".field.po", "accounts", null, null),
            new MetaView.MetaField("qd", "account", null, null, i18nPrefix + ".field.qd", "accounts", null, null),
            new MetaView.MetaField("rd", "account", null, null, i18nPrefix + ".field.rd", "accounts", null, null),
            new MetaView.MetaField("acl", "select", true, null, i18nPrefix + ".acl", null, null, List.of(
                Map.of("value", "open", "i18n", "project.acl.open"),
                Map.of("value", "private", "i18n", "project.acl.private"),
                Map.of("value", "program", "i18n", "project.acl.program"))),
            new MetaView.MetaField("whitelist", "accounts", null, null, i18nPrefix + ".field.whitelist", "accounts",
                true, null),
            new MetaView.MetaField("description", "richtext", null, null, i18nPrefix + ".field.description", null, null,
                null),
            new MetaView.MetaField("sort", "number", null, null, i18nPrefix + ".field.sort", null, null, null),
            new MetaView.MetaField("isMilestone", "checkbox", null, null, i18nPrefix + ".field.milestone", null, null,
                null),
            new MetaView.MetaField("productIds", "multiselect", null, null, i18nPrefix + ".field.products", "products",
                true, null)),
        new MetaView.MetaList(List.of("id", "name", "status", "model", "pm", "beginDate", "endDate"), "-id"),
        workflowRegistry.actionsOf(domain),
        Map.of(
            "wait", new MetaView.MetaStatusVisual("wait", "project.status.wait"),
            "doing", new MetaView.MetaStatusVisual("doing", "project.status.doing"),
            "suspended", new MetaView.MetaStatusVisual("suspended", "project.status.suspended"),
            "delay", new MetaView.MetaStatusVisual("delay", "project.status.delay"),
            "closed", new MetaView.MetaStatusVisual("closed", "project.status.closed")));
  }
}
