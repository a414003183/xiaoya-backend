package net.zentao.platform.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 枚举值域同源核验（03 §5）：前端列表页筛选下拉与表单弹窗的枚举下拉一律读 `meta/{domain}` 字段的
 * `options`，不在前端写死清单——所以每个业务枚举字段都必须在 Registrar 里声明取值与 i18n 键。
 *
 * <p>EXPECTED 表 = 列表页筛选字段（frontend/web/src/features/*&#47;pages&#47;*list*.page.tsx）；
 * FORM_EXPECTED 表 = 表单/开关目录字段（创建/编辑弹窗、通知设置页）。
 * 少声明 / 值域漂移 / 选项缺 i18n 键都在这里红灯；前端只负责 `t(option.i18n)` 展示。
 */
class FilterFieldOptionsTest extends net.zentao.H2TestSupport {

  /** 期望：域 / 字段 / 取值顺序（顺序即下拉顺序，前端不重排）。 */
  private record Expected(String domain, String field, List<String> values) {}

  private static final List<Expected> EXPECTED = List.of(
      // quality（QualityRegistrar）
      new Expected("bug", "status", List.of("active", "resolved", "closed")),
      new Expected("bug", "severity", List.of("1", "2", "3", "4")),
      new Expected("bug", "priority", List.of("1", "2", "3", "4")),
      new Expected("bug", "type", List.of("codeerror", "config", "install", "security", "performance", "standard",
          "automation", "designdefect", "others")),
      new Expected("bug", "resolution", List.of("bydesign", "duplicate", "external", "fixed", "notrepro", "postponed",
          "willnotfix", "tostory")),
      new Expected("bug", "confirmed", List.of("1", "0")),
      new Expected("testCase", "status", List.of("wait", "normal", "blocked", "investigate")),
      new Expected("testCase", "priority", List.of("1", "2", "3", "4")),
      new Expected("testCase", "type", List.of("unit", "interface", "feature", "install", "config", "performance",
          "security", "other")),
      new Expected("testCase", "stage", List.of("unittest", "feature", "intergrate", "system", "smoke", "bvt")),
      new Expected("testCase", "lastRunResult", List.of("pass", "fail", "blocked", "n/a")),
      new Expected("testRun", "status", List.of("wait", "doing", "done", "blocked")),
      new Expected("testRun", "type", List.of("integrate", "system", "acceptance", "performance", "safety")),
      new Expected("testRun", "priority", List.of("1", "2", "3", "4")),
      // 执行清单第五档 @null = 未登记结果（03 §3 特殊量）
      new Expected("testRun", "result", List.of("pass", "fail", "blocked", "n/a", "@null")),
      new Expected("suite", "type", List.of("public", "private")),
      // requirement（StoryRegistrar）
      new Expected("story", "type", List.of("story", "epic", "requirement")),
      new Expected("story", "status", List.of("draft", "reviewing", "active", "changing", "changed", "closed")),
      new Expected("story", "priority", List.of("1", "2", "3", "4")),
      new Expected("story", "stage", List.of("wait", "developing", "testing", "released")),
      new Expected("story", "source", List.of("manual", "customer", "market", "bug", "other")),
      // task（TaskRegistrar）
      new Expected("task", "status", List.of("wait", "doing", "done", "pause", "cancel", "closed")),
      new Expected("task", "type", List.of("design", "devel", "request", "test", "study", "discuss", "ui", "affair",
          "misc")),
      new Expected("task", "priority", List.of("1", "2", "3", "4")),
      new Expected("task", "closedReason", List.of("done", "cancel")),
      // workspace（WorkspaceRegistrar）：待办 + /my/* 的 role 值域
      new Expected("todo", "status", List.of("wait", "doing", "done", "closed")),
      new Expected("todo", "priority", List.of("1", "2", "3", "4")),
      new Expected("todo", "type", List.of("custom", "bug", "task", "story", "epic", "requirement", "testRun")),
      new Expected("workspace", "taskRole", List.of("assignee", "creator", "finisher", "closer")),
      new Expected("workspace", "bugRole", List.of("assignee", "creator", "resolver", "closer")),
      new Expected("workspace", "storyRole", List.of("assignee", "creator", "reviewer", "closer")),
      // product（ProductRegistrar）
      new Expected("product", "status", List.of("normal", "closed")),
      new Expected("product", "type", List.of("normal", "branch", "platform")),
      new Expected("product", "acl", List.of("public", "private", "custom")),
      new Expected("branch", "status", List.of("active", "closed")),
      new Expected("category", "type", List.of("story", "bug", "case")),
      new Expected("plan", "status", List.of("wait", "doing", "done", "closed")),
      new Expected("release", "status", List.of("normal", "terminated")),
      // project（ProjectRegistrar）
      new Expected("program", "type", List.of("program")),
      new Expected("project", "type", List.of("project")),
      new Expected("execution", "type", List.of("sprint", "stage", "kanban")),
      new Expected("program", "status", List.of("wait", "doing", "suspended", "delay", "closed")),
      new Expected("program", "model", List.of("scrum", "waterfall", "kanban")),
      new Expected("program", "acl", List.of("open", "private", "program")),
      new Expected("program", "priority", List.of("1", "2", "3", "4")),
      new Expected("execution", "status", List.of("wait", "doing", "suspended", "delay", "closed")),
      new Expected("board_space", "status", List.of("active", "closed")),
      new Expected("board_space", "type", List.of("cooperation", "public", "private")),
      new Expected("board_space", "acl", List.of("open", "private")),
      // doc（DocRegistrar）
      new Expected("docSpace", "type", List.of("product", "project", "execution", "custom", "mine")),
      new Expected("docSpace", "acl", List.of("open", "default", "private")),
      new Expected("doc", "status", List.of("draft", "published")),
      new Expected("doc", "type", List.of("markdown", "html")),
      new Expected("doc", "acl", List.of("open", "private")),
      // org（OrgRegistrar）：role 的选项来自角色字典（source=roles），不在此表
      new Expected("account", "status", List.of("active", "disabled")),
      new Expected("account", "gender", List.of("m", "f")),
      // platform（NotificationMetaRegistrar）
      new Expected("notification", "readAt", List.of("@null", "@notNull")));

  /**
   * 表单/开关目录字段的值域（2026-09-20 表单 meta 选项化）：创建/编辑弹窗与通知设置页的下拉
   * 不再用前端常量，值域只能在 Registrar 声明——新增取值只改注册表，前端自动出现。
   */
  private static final List<Expected> FORM_EXPECTED = List.of(
      new Expected("card", "status", List.of("doing", "done")),
      new Expected("card", "priority", List.of("1", "2", "3", "4")),
      new Expected("story", "closedReason", List.of("done", "duplicate", "rejected", "willnotfix", "postponed")),
      new Expected("plan", "closedReason", List.of("done", "cancel")),
      new Expected("stakeholder", "type", List.of("inside", "outside")),
      new Expected("notification", "type", List.of("story-created", "story-changed", "task-assigned",
          "task-finished", "bug-created", "bug-resolved", "account-reset-password")));

  @Autowired
  private MetaRegistry metaRegistry;

  @Test
  @DisplayName("筛选字段都声明 options：取值逐字等于期望清单（顺序即下拉顺序），且每个取值都有 i18n 键")
  void filterFieldsDeclareOptions() {
    assertOptions(EXPECTED);
  }

  @Test
  @DisplayName("表单字段都声明 options：卡片状态/关闭原因/干系人类型/通知开关目录取值与 i18n 键齐全")
  void formFieldsDeclareOptions() {
    assertOptions(FORM_EXPECTED);
  }

  private void assertOptions(List<Expected> table) {
    assertEquals(table.size(), table.stream().map(item -> item.domain() + "." + item.field()).distinct().count(),
        "期望表自身有重复行");
    for (Expected expected : table) {
      MetaView view = metaRegistry.get(expected.domain())
          .orElseThrow(() -> new AssertionError("未注册 meta：" + expected.domain()));
      MetaView.MetaField field = view.fields().stream()
          .filter(item -> expected.field().equals(item.key()))
          .findFirst()
          .orElseThrow(() -> new AssertionError(expected.domain() + " 缺字段：" + expected.field()));
      List<Map<String, Object>> options = field.options();
      assertNotNull(options, expected.domain() + "." + expected.field() + " 未声明 options（前端会没有下拉项）");
      assertTrue(!options.isEmpty(), expected.domain() + "." + expected.field() + " options 为空");
      assertEquals(expected.values(),
          options.stream().map(option -> String.valueOf(option.get("value"))).toList(),
          expected.domain() + "." + expected.field() + " 取值与期望清单不一致");
      for (Map<String, Object> option : options) {
        Object i18n = option.get("i18n");
        assertTrue(i18n instanceof String key && !key.isBlank(),
            expected.domain() + "." + expected.field() + " 的取值 " + option.get("value") + " 缺 i18n 键");
        assertTrue(((String) i18n).matches("[a-zA-Z][\\w./-]*"),
            expected.domain() + "." + expected.field() + " i18n 键形如域.段.取值：" + i18n);
      }
    }
  }
}
