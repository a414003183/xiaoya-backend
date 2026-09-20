package net.zentao.quality.infra;

import java.util.List;
import java.util.Map;
import net.zentao.platform.meta.DictRegistry;
import net.zentao.platform.meta.MetaRegistry;
import net.zentao.platform.meta.MetaView;
import net.zentao.platform.rbac.PrivilegeCatalog;
import net.zentao.platform.search.SearchRegistry;
import net.zentao.platform.search.SearchScope;
import net.zentao.platform.workflow.WorkflowRegistry;
import net.zentao.quality.app.BugQueryService;
import net.zentao.quality.app.TestCaseQueryService;
import org.springframework.context.annotation.Configuration;

/**
 * quality → platform 反向注册（A3 不破坏：域主动调用 platform 注册口）。
 * 权限码：quality 卡 §5 全集；meta：bug（字段 + 动作 + 状态可视化）；搜索 scope：bug。
 */
@Configuration
public class QualityRegistrar {

  public QualityRegistrar(MetaRegistry metaRegistry, PrivilegeCatalog privilegeCatalog,
      WorkflowRegistry workflowRegistry, SearchRegistry searchRegistry, DictRegistry dictRegistry,
      BugQueryService bugQueryService, TestCaseQueryService testCaseQueryService) {
    privilegeCatalog.register("bug", List.of(
        "bug-view", "bug-create", "bug-edit", "bug-confirm", "bug-resolve", "bug-activate", "bug-close",
        "bug-assign", "bug-delete"));
    privilegeCatalog.register("testcase", List.of(
        "testcase-view", "testcase-create", "testcase-edit", "testcase-review", "testcase-delete"));
    privilegeCatalog.register("suite", List.of(
        "suite-view", "suite-create", "suite-edit", "suite-link-case", "suite-delete"));
    privilegeCatalog.register("library", List.of(
        "library-view", "library-create", "library-edit", "library-delete"));
    privilegeCatalog.register("testrun", List.of(
        "testrun-view", "testrun-create", "testrun-edit", "testrun-start", "testrun-block", "testrun-activate",
        "testrun-close", "testrun-link-case", "testrun-record-result", "testrun-assign-case", "testrun-delete"));
    privilegeCatalog.register("report", List.of(
        "report-view", "report-create", "report-edit", "report-delete"));

    // 计算字典（quality 卡 §3.1 os/browser 取值）
    dictRegistry.register(new net.zentao.platform.meta.DictProvider() {
      @Override
      public String name() {
        return "bug-os";
      }

      @Override
      public List<Map<String, Object>> items() {
        return List.of(
            Map.of("value", "windows", "i18n", "bug.os.windows"),
            Map.of("value", "osx", "i18n", "bug.os.osx"),
            Map.of("value", "android", "i18n", "bug.os.android"),
            Map.of("value", "ios", "i18n", "bug.os.ios"),
            Map.of("value", "linux", "i18n", "bug.os.linux"),
            Map.of("value", "others", "i18n", "bug.os.others"));
      }
    });
    dictRegistry.register(new net.zentao.platform.meta.DictProvider() {
      @Override
      public String name() {
        return "bug-browser";
      }

      @Override
      public List<Map<String, Object>> items() {
        return List.of(
            Map.of("value", "ie", "i18n", "bug.browser.ie"),
            Map.of("value", "chrome", "i18n", "bug.browser.chrome"),
            Map.of("value", "firefox", "i18n", "bug.browser.firefox"),
            Map.of("value", "safari", "i18n", "bug.browser.safari"),
            Map.of("value", "edge", "i18n", "bug.browser.edge"),
            Map.of("value", "others", "i18n", "bug.browser.others"));
      }
    });

    metaRegistry.register("bug", new MetaView(
        "bug",
        List.of(
            new MetaView.MetaField("title", "text", true, 255, "bug.field.title", null, null, null),
            new MetaView.MetaField("severity", "select", true, null, "bug.field.severity", null, null, List.of(
                Map.of("value", 1, "i18n", "bug.severity.1"),
                Map.of("value", 2, "i18n", "bug.severity.2"),
                Map.of("value", 3, "i18n", "bug.severity.3"),
                Map.of("value", 4, "i18n", "bug.severity.4"))),
            new MetaView.MetaField("priority", "select", true, null, "common.priority", null, null, List.of(
                Map.of("value", 1, "i18n", "common.priority.1"),
                Map.of("value", 2, "i18n", "common.priority.2"),
                Map.of("value", 3, "i18n", "common.priority.3"),
                Map.of("value", 4, "i18n", "common.priority.4"))),
            new MetaView.MetaField("type", "select", true, null, "bug.field.type", null, null, List.of(
                Map.of("value", "codeerror", "i18n", "bug.type.codeerror"),
                Map.of("value", "config", "i18n", "bug.type.config"),
                Map.of("value", "install", "i18n", "bug.type.install"),
                Map.of("value", "security", "i18n", "bug.type.security"),
                Map.of("value", "performance", "i18n", "bug.type.performance"),
                Map.of("value", "standard", "i18n", "bug.type.standard"),
                Map.of("value", "automation", "i18n", "bug.type.automation"),
                Map.of("value", "designdefect", "i18n", "bug.type.designdefect"),
                Map.of("value", "others", "i18n", "bug.type.others"))),
            // 列表筛选值域（quality §3.1 filterable：status/resolution/confirmed）——前端下拉只认这里的选项。
            new MetaView.MetaField("status", "select", null, null, "bug.field.status", null, null, List.of(
                Map.of("value", "active", "i18n", "bug.status.active"),
                Map.of("value", "resolved", "i18n", "bug.status.resolved"),
                Map.of("value", "closed", "i18n", "bug.status.closed"))),
            new MetaView.MetaField("resolution", "select", null, null, "bug.field.resolution", null, null, List.of(
                Map.of("value", "bydesign", "i18n", "bug.resolution.bydesign"),
                Map.of("value", "duplicate", "i18n", "bug.resolution.duplicate"),
                Map.of("value", "external", "i18n", "bug.resolution.external"),
                Map.of("value", "fixed", "i18n", "bug.resolution.fixed"),
                Map.of("value", "notrepro", "i18n", "bug.resolution.notrepro"),
                Map.of("value", "postponed", "i18n", "bug.resolution.postponed"),
                Map.of("value", "willnotfix", "i18n", "bug.resolution.willnotfix"),
                Map.of("value", "tostory", "i18n", "bug.resolution.tostory"))),
            // 契约 filters[confirmed] 明确「1/0」（bool 列），故选项值即 1/0，不是 yes/no。
            new MetaView.MetaField("confirmed", "select", null, null, "bug.field.confirmed", null, null, List.of(
                Map.of("value", "1", "i18n", "bug.confirmed.yes"),
                Map.of("value", "0", "i18n", "bug.confirmed.no"))),
            new MetaView.MetaField("os", "select", null, null, "bug.field.os", "bug-os", null, null),
            new MetaView.MetaField("browser", "select", null, null, "bug.field.browser", "bug-browser", null, null),
            new MetaView.MetaField("categoryId", "categoryTree", null, null, "bug.field.category", null, null, null),
            new MetaView.MetaField("executionId", "select", null, null, "bug.field.execution", "executions", null, null),
            new MetaView.MetaField("planId", "select", null, null, "bug.field.plan", "plans", null, null),
            new MetaView.MetaField("storyId", "select", null, null, "bug.field.story", "stories", null, null),
            new MetaView.MetaField("taskId", "select", null, null, "bug.field.task", "tasks", null, null),
            new MetaView.MetaField("openedBuilds", "text", null, 255, "bug.field.openedBuilds", null, null, null),
            new MetaView.MetaField("deadline", "date", null, null, "bug.field.deadline", null, null, null),
            new MetaView.MetaField("assignee", "account", null, null, "bug.field.assignee", "accounts", null, null),
            new MetaView.MetaField("steps", "richtext", null, null, "bug.field.steps", null, null, null),
            new MetaView.MetaField("keywords", "text", null, 255, "bug.field.keywords", null, null, null),
            new MetaView.MetaField("relatedBugIds", "multiselect", null, null, "bug.field.relatedBugs", "bugs", true, null),
            new MetaView.MetaField("notifyAccounts", "accounts", null, null, "bug.field.notify", "accounts", true, null)),
        new MetaView.MetaList(List.of("id", "title", "severity", "priority", "status", "assignee", "createdBy"), "-id"),
        workflowRegistry.actionsOf("bug"),
        Map.of(
            "active", new MetaView.MetaStatusVisual("doing", "bug.status.active"),
            "resolved", new MetaView.MetaStatusVisual("active", "bug.status.resolved"),
            "closed", new MetaView.MetaStatusVisual("closed", "bug.status.closed"))));

    // 搜索 scope（platform 卡 §5.2）：DataScope 在查询内先行注入
    searchRegistry.register(new SearchScope("bug", List.of("title", "keywords"),
        (q, limit, principal) -> bugQueryService.search(q, limit, principal)));
    searchRegistry.register(new SearchScope("testCase", List.of("title", "keywords"),
        (q, limit, principal) -> testCaseQueryService.search(q, limit, principal)));

    metaRegistry.register("testCase", new MetaView(
        "testCase",
        List.of(
            new MetaView.MetaField("title", "text", true, 255, "testCase.field.title", null, null, null),
            new MetaView.MetaField("priority", "select", true, null, "common.priority", null, null, List.of(
                Map.of("value", 1, "i18n", "common.priority.1"),
                Map.of("value", 2, "i18n", "common.priority.2"),
                Map.of("value", 3, "i18n", "common.priority.3"),
                Map.of("value", 4, "i18n", "common.priority.4"))),
            new MetaView.MetaField("type", "select", true, null, "testCase.field.type", null, null, List.of(
                Map.of("value", "unit", "i18n", "testCase.type.unit"),
                Map.of("value", "interface", "i18n", "testCase.type.interface"),
                Map.of("value", "feature", "i18n", "testCase.type.feature"),
                Map.of("value", "install", "i18n", "testCase.type.install"),
                Map.of("value", "config", "i18n", "testCase.type.config"),
                Map.of("value", "performance", "i18n", "testCase.type.performance"),
                Map.of("value", "security", "i18n", "testCase.type.security"),
                Map.of("value", "other", "i18n", "testCase.type.other"))),
            new MetaView.MetaField("stage", "multiselect", null, null, "testCase.field.stage", null, null, List.of(
                Map.of("value", "unittest", "i18n", "testCase.stage.unittest"),
                Map.of("value", "feature", "i18n", "testCase.stage.feature"),
                Map.of("value", "intergrate", "i18n", "testCase.stage.intergrate"),
                Map.of("value", "system", "i18n", "testCase.stage.system"),
                Map.of("value", "smoke", "i18n", "testCase.stage.smoke"),
                Map.of("value", "bvt", "i18n", "testCase.stage.bvt"))),
            // 列表筛选值域（quality §3.2 filterable：status/lastRunResult）——前端下拉只认这里的选项。
            new MetaView.MetaField("status", "select", null, null, "testCase.field.status", null, null, List.of(
                Map.of("value", "wait", "i18n", "testCase.status.wait"),
                Map.of("value", "normal", "i18n", "testCase.status.normal"),
                Map.of("value", "blocked", "i18n", "testCase.status.blocked"),
                Map.of("value", "investigate", "i18n", "testCase.status.investigate"))),
            new MetaView.MetaField("lastRunResult", "select", null, null, "testCase.field.lastRunResult", null, null,
                List.of(
                    Map.of("value", "pass", "i18n", "testCase.result.pass"),
                    Map.of("value", "fail", "i18n", "testCase.result.fail"),
                    Map.of("value", "blocked", "i18n", "testCase.result.blocked"),
                    Map.of("value", "n/a", "i18n", "testCase.result.n/a"))),
            new MetaView.MetaField("categoryId", "categoryTree", null, null, "testCase.field.category", null, null, null),
            new MetaView.MetaField("storyId", "select", null, null, "testCase.field.story", "stories", null, null),
            new MetaView.MetaField("precondition", "textarea", null, null, "testCase.field.precondition", null, null, null),
            new MetaView.MetaField("keywords", "text", null, 255, "testCase.field.keywords", null, null, null),
            new MetaView.MetaField("steps", "steps", null, null, "testCase.field.steps", null, null, null),
            new MetaView.MetaField("needReview", "checkbox", null, null, "testCase.field.needReview", null, null, null)),
        new MetaView.MetaList(List.of("id", "title", "priority", "status", "lastRunResult", "createdBy"), "-id"),
        workflowRegistry.actionsOf("testCase"),
        Map.of(
            "wait", new MetaView.MetaStatusVisual("wait", "testCase.status.wait"),
            "normal", new MetaView.MetaStatusVisual("active", "testCase.status.normal"),
            "blocked", new MetaView.MetaStatusVisual("doing", "testCase.status.blocked"),
            "investigate", new MetaView.MetaStatusVisual("doing", "testCase.status.investigate"))));

    metaRegistry.register("suite", new MetaView(
        "suite",
        List.of(
            new MetaView.MetaField("name", "text", true, 255, "suite.field.name", null, null, null),
            new MetaView.MetaField("description", "richtext", null, null, "suite.field.description", null, null, null),
            new MetaView.MetaField("type", "select", true, null, "suite.field.type", null, null, List.of(
                Map.of("value", "public", "i18n", "suite.type.public"),
                Map.of("value", "private", "i18n", "suite.type.private"))),
            new MetaView.MetaField("sort", "number", null, null, "suite.field.sort", null, null, null)),
        new MetaView.MetaList(List.of("id", "name", "type", "caseCount", "createdBy"), "-id"),
        List.of(),
        Map.of()));

    metaRegistry.register("library", new MetaView(
        "library",
        List.of(
            new MetaView.MetaField("name", "text", true, 255, "suite.field.name", null, null, null),
            new MetaView.MetaField("description", "richtext", null, null, "suite.field.description", null, null, null)),
        new MetaView.MetaList(List.of("id", "name", "caseCount", "createdBy"), "-id"),
        List.of(),
        Map.of()));

    metaRegistry.register("testRun", new MetaView(
        "testRun",
        List.of(
            new MetaView.MetaField("name", "text", true, 90, "testRun.field.name", null, null, null),
            new MetaView.MetaField("executionId", "select", true, null, "testRun.field.execution", "executions", null, null),
            new MetaView.MetaField("buildId", "select", null, null, "testRun.field.build", "builds", null, null),
            new MetaView.MetaField("owner", "account", null, null, "testRun.field.owner", "accounts", null, null),
            new MetaView.MetaField("priority", "select", true, null, "common.priority", null, null, List.of(
                Map.of("value", 1, "i18n", "common.priority.1"),
                Map.of("value", 2, "i18n", "common.priority.2"),
                Map.of("value", 3, "i18n", "common.priority.3"),
                Map.of("value", 4, "i18n", "common.priority.4"))),
            new MetaView.MetaField("type", "select", null, null, "testRun.field.type", null, null, List.of(
                Map.of("value", "integrate", "i18n", "testRun.type.integrate"),
                Map.of("value", "system", "i18n", "testRun.type.system"),
                Map.of("value", "acceptance", "i18n", "testRun.type.acceptance"),
                Map.of("value", "performance", "i18n", "testRun.type.performance"),
                Map.of("value", "safety", "i18n", "testRun.type.safety"))),
            // 列表筛选值域（quality §3.4/§3.5 filterable：status；执行结果 result 与用例 lastRunResult 同词表）。
            new MetaView.MetaField("status", "select", null, null, "testRun.field.status", null, null, List.of(
                Map.of("value", "wait", "i18n", "testRun.status.wait"),
                Map.of("value", "doing", "i18n", "testRun.status.doing"),
                Map.of("value", "done", "i18n", "testRun.status.done"),
                Map.of("value", "blocked", "i18n", "testRun.status.blocked"))),
            new MetaView.MetaField("result", "select", null, null, "testRun.field.result", null, null, List.of(
                Map.of("value", "pass", "i18n", "testCase.result.pass"),
                Map.of("value", "fail", "i18n", "testCase.result.fail"),
                Map.of("value", "blocked", "i18n", "testCase.result.blocked"),
                Map.of("value", "n/a", "i18n", "testCase.result.n/a"),
                // @null = 尚未登记结果的关联用例（03 §3 特殊量），执行清单筛选的第五档。
                Map.of("value", "@null", "i18n", "testRun.summary.none"))),
            new MetaView.MetaField("beginDate", "date", true, null, "testRun.field.beginDate", null, null, null),
            new MetaView.MetaField("endDate", "date", true, null, "testRun.field.endDate", null, null, null),
            new MetaView.MetaField("description", "richtext", null, null, "testRun.field.description", null, null, null),
            new MetaView.MetaField("members", "accounts", null, null, "testRun.field.members", "accounts", true, null),
            new MetaView.MetaField("notifyAccounts", "accounts", null, null, "testRun.field.notify", "accounts", true, null)),
        new MetaView.MetaList(List.of("id", "name", "priority", "status", "owner", "beginDate", "endDate"), "-id"),
        workflowRegistry.actionsOf("testRun"),
        Map.of(
            "wait", new MetaView.MetaStatusVisual("wait", "testRun.status.wait"),
            "doing", new MetaView.MetaStatusVisual("doing", "testRun.status.doing"),
            "blocked", new MetaView.MetaStatusVisual("doing", "testRun.status.blocked"),
            "done", new MetaView.MetaStatusVisual("closed", "testRun.status.done"))));

    metaRegistry.register("report", new MetaView(
        "report",
        List.of(
            new MetaView.MetaField("title", "text", true, 255, "report.field.title", null, null, null),
            new MetaView.MetaField("testRunIds", "multiselect", null, null, "report.field.testRuns", null, true, null),
            new MetaView.MetaField("beginDate", "date", true, null, "report.field.beginDate", null, null, null),
            new MetaView.MetaField("endDate", "date", true, null, "report.field.endDate", null, null, null),
            new MetaView.MetaField("owner", "account", null, null, "report.field.owner", "accounts", null, null),
            new MetaView.MetaField("content", "richtext", null, null, "report.field.content", null, null, null)),
        new MetaView.MetaList(List.of("id", "title", "owner", "beginDate", "endDate", "createdBy"), "-id"),
        List.of(),
        Map.of()));
  }
}
