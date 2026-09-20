package net.zentao.platform.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.zentao.platform.rbac.PrivilegeCatalog;
import net.zentao.platform.workflow.StateMachine;
import net.zentao.platform.workflow.WorkflowRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * meta 动作 code 与权限码前缀同源核验（03 §6）：前端以 `actions[].code` 过滤 /me privileges 决定按钮显隐，
 * 故 code 的域前缀必须与各域 Registrar 登记的权限码前缀一致；否则该按钮对所有人（含超管）隐藏。
 *
 * <p>回归背景：code 曾按 yml 的 domain 原样拼接，camelCase/下划线域（testRun/testCase/board_space）
 * 拼出的码与权限码（testrun 族、testcase 族、board-space 族）不符，测试单详情页动作区整体为空。
 * 断言只覆盖"域前缀"而非"整码"——权限码粒度按域卡定义，部分动作共用组级码（如 story 的 pass/reject
 * 同属 story-review），故不作整码登记要求。
 */
class MetaActionPrivilegeTest extends net.zentao.H2TestSupport {

  @Autowired
  private MetaRegistry metaRegistry;

  @Autowired
  private PrivilegeCatalog privilegeCatalog;

  @Autowired
  private WorkflowRegistry workflowRegistry;

  @Test
  @DisplayName("每个 workflow 动作 code 的域前缀都是已登记权限码前缀（camelCase/下划线域归一后）")
  void actionCodePrefixesAreRegistered() {
    List<String> unknown = new ArrayList<>();
    for (StateMachine machine : workflowRegistry.all()) {
      MetaView view = metaRegistry.get(machine.domain()).orElse(null);
      if (view == null) {
        continue; // 未注册 meta 的域（account/branch 等）不参与按钮显隐
      }
      String prefix = WorkflowRegistry.privilegeCode(machine.domain(), "").replaceAll("-$", "");
      boolean prefixRegistered = privilegeCatalog.allCodes().stream()
          .anyMatch(code -> code.startsWith(prefix + "-"));
      if (!prefixRegistered) {
        unknown.add(machine.domain() + " → 前缀 " + prefix);
        continue;
      }
      for (MetaView.MetaAction action : view.actions()) {
        if (action.code() == null || !action.code().startsWith(prefix + "-")) {
          unknown.add(machine.domain() + ":" + action.action() + " → " + action.code());
        }
      }
    }
    assertTrue(unknown.isEmpty(), "meta 动作 code 必须带已登记的权限码前缀，异常项：" + unknown);
  }

  @Test
  @DisplayName("camelCase / 下划线域的权限码前缀归一：testRun→testrun、testCase→testcase、board_space→board-space")
  void privilegeCodeNormalization() {
    assertEquals("testrun-start", WorkflowRegistry.privilegeCode("testRun", "start"));
    assertEquals("testcase-review", WorkflowRegistry.privilegeCode("testCase", "review"));
    assertEquals("board-space-close", WorkflowRegistry.privilegeCode("board_space", "close"));
    assertEquals("task-start", WorkflowRegistry.privilegeCode("task", "start"), "已是小写的域不受影响");

    assertTrue(privilegeCatalog.isRegistered("testrun-start"), "归一后的码必须是已登记权限码");
    assertFalse(privilegeCatalog.isRegistered("testRun-start"), "旧拼法不得再出现");
  }
}
