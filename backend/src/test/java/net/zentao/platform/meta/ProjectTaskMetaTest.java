package net.zentao.platform.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.yaml.snakeyaml.Yaml;

/**
 * T-12 meta 同源核验（03 §5/§6）：program/project/execution/task 的 `actions[].allowedStatus` 必须与
 * workflow yml 的 from 列逐字一致（前端按钮显隐与后端守卫同源）；字段规约（必填/上限/枚举）与领域卡一致。
 * 本测试直接解析 YAML 源文件比对——registry 侧同名比对不算证据。
 */
class ProjectTaskMetaTest extends net.zentao.H2TestSupport {

  @Autowired
  private MetaRegistry metaRegistry;

  /** 从 workflow yml 解析 domain → action → from 状态集（合并同 action 的多条迁移，声明顺序去重）。 */
  @SuppressWarnings("unchecked")
  private static Map<String, Map<String, List<String>>> yamlTransitions(String file) {
    Map<String, Map<String, List<String>>> byDomain = new LinkedHashMap<>();
    try (InputStream stream = ProjectTaskMetaTest.class.getResourceAsStream("/workflow/" + file)) {
      assertTrue(stream != null, "缺少 workflow/" + file);
      for (Object document : new Yaml().loadAll(stream)) {
        Map<String, Object> root = (Map<String, Object>) document;
        String domain = String.valueOf(root.get("domain"));
        Map<String, List<String>> byAction = new LinkedHashMap<>();
        for (Object raw : (List<Object>) root.get("transitions")) {
          Map<String, Object> transition = (Map<String, Object>) raw;
          String action = String.valueOf(transition.get("action"));
          List<String> from = byAction.computeIfAbsent(action, key -> new ArrayList<>());
          for (Object state : (List<Object>) transition.get("from")) {
            String value = String.valueOf(state);
            if (!from.contains(value)) {
              from.add(value);
            }
          }
        }
        byDomain.put(domain, byAction);
      }
    } catch (Exception e) {
      throw new UncheckedIOException(new java.io.IOException("解析 workflow/" + file + " 失败", e));
    }
    return byDomain;
  }

  @Test
  @DisplayName("状态机同源：三型 project.yml、board.yml 二态、task.yml 的 actions[].allowedStatus 逐字等于 yml from 列")
  void actionsMatchWorkflowYaml() {
    assertActionsMatch("project.yml", List.of("program", "project", "execution"));
    assertActionsMatch("board.yml", List.of("board_space", "board"));
    assertActionsMatch("task.yml", List.of("task"));
  }

  private void assertActionsMatch(String file, List<String> domains) {
    Map<String, Map<String, List<String>>> yaml = yamlTransitions(file);
    for (String domain : domains) {
      MetaView view = metaRegistry.get(domain).orElseThrow(() -> new AssertionError("未注册 meta：" + domain));
      Map<String, List<String>> expected = yaml.get(domain);
      assertTrue(expected != null, file + " 缺 domain=" + domain);
      assertEquals(expected.size(), view.actions().size(), domain + " 动作数不一致：" + view.actions());
      for (MetaView.MetaAction action : view.actions()) {
        List<String> from = expected.get(action.action());
        assertTrue(from != null, domain + " 多出动作：" + action.action());
        assertEquals(from, action.allowedStatus(), domain + "/" + action.action() + " allowedStatus 与 yml 不一致");
      }
    }
  }

  @Test
  @DisplayName("字段规约：必填/上限/枚举与 project 卡 §3.1、task 卡 §3 一致；默认列与排序按卡面")
  void fieldRulesMatchDomainCards() {
    MetaView task = metaRegistry.get("task").orElseThrow();
    assertField(task, "title", true, 255);
    assertField(task, "keywords", null, 255);
    MetaView.MetaField priority = field(task, "priority");
    assertEquals(4, priority.options().size(), "优先级四档");
    assertEquals(Boolean.TRUE, priority.required(), "priority 创建必填（契约有默认值）");
    assertEquals(List.of("id", "title", "priority", "status", "assignee", "estimateHours", "consumedHours",
        "leftHours", "deadline"), task.list().defaultColumns(), "task 列表默认列（§3 meta 要点）");
    assertEquals("-id", task.list().defaultSort(), "task 列表默认排序（§3 meta 要点）");

    MetaView project = metaRegistry.get("project").orElseThrow();
    assertField(project, "name", true, 90);
    assertField(project, "code", null, 45);
    assertEquals(List.of("open", "private", "program"),
        field(project, "acl").options().stream().map(option -> String.valueOf(option.get("value"))).toList(),
        "acl 三档与 §3.1 一致");

    MetaView effort = metaRegistry.get("effort").orElseThrow();
    assertField(effort, "consumedHours", true, null);
    assertEquals("-workDate,-id", effort.list().defaultSort(), "工时明细默认排序（§3b）");
  }

  private static MetaView.MetaField field(MetaView view, String key) {
    return view.fields().stream().filter(item -> key.equals(item.key())).findFirst()
        .orElseThrow(() -> new AssertionError(view.domain() + " 缺字段：" + key));
  }

  private static void assertField(MetaView view, String key, Boolean required, Integer maxLength) {
    MetaView.MetaField field = field(view, key);
    assertEquals(required, field.required(), view.domain() + "." + key + " required");
    assertEquals(maxLength, field.maxLength(), view.domain() + "." + key + " maxLength");
  }
}
