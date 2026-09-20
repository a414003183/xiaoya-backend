package net.zentao.platform.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.zentao.platform.meta.MetaView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** workflow YAML 加载与 allowedStatus 导出（platform 卡 §4.3，03 §6 同源）。 */
@SpringBootTest
class WorkflowRegistryTest {

  @Autowired
  WorkflowRegistry registry;

  @Test
  @DisplayName("加载 workflow/test.yml：states/transitions 完整")
  void loadsYamlDefinition() {
    StateMachine definition =
        registry.get("test").orElseThrow(() -> new AssertionError("test.yml 应已加载"));
    assertEquals("draft", definition.initial());
    assertEquals(List.of("draft", "reviewing", "active", "changed", "closed"), definition.states());
    assertEquals(2, definition.transitions().size());
    var submit = definition.transitions().getFirst();
    assertEquals("submit-review", submit.action());
    assertEquals(List.of("draft", "changed"), submit.from());
    assertEquals("reviewing", submit.to());
    assertEquals(1, submit.guards().size());
    assertEquals("reviewers-required", submit.guards().getFirst().name());
    assertEquals("reviewers.size() > 0", submit.guards().getFirst().expr());
  }

  @Test
  @DisplayName("allowedStatus 导出与 from 同源：submit-review 在 draft/changed 可见")
  void exportsAllowedStatus() {
    List<MetaView.MetaAction> actions = registry.actionsOf("test");
    assertEquals(2, actions.size());
    var submit = actions.stream()
        .filter(action -> action.action().equals("submit-review"))
        .findFirst()
        .orElseThrow();
    assertEquals("test-submit-review", submit.code());
    assertEquals(List.of("draft", "changed"), submit.allowedStatus());
    assertTrue(actions.stream()
        .filter(action -> action.action().equals("close"))
        .findFirst()
        .orElseThrow()
        .allowedStatus()
        .equals(List.of("active")));
  }

  @Test
  @DisplayName("未注册域导出空动作表")
  void unknownDomainYieldsEmpty() {
    assertTrue(registry.actionsOf("no-such-domain").isEmpty());
  }
}
