package net.zentao.platform.workflow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.platform.meta.MetaView;
import org.springframework.stereotype.Component;

/**
 * workflow 状态机注册表（platform 卡 §4.3）：启动期由 {@link YamlStateMachineLoader} 加载并校验
 * resources/workflow/*.yml（非法定义启动即失败），运行期只读。
 * {@link #actionsOf} 导出 meta 的 actions[].allowedStatus——前端按钮显隐与后端守卫同源（03 §6）。
 */
@Component
public class WorkflowRegistry {

  private final Map<String, StateMachine> machines;

  public WorkflowRegistry(YamlStateMachineLoader loader) {
    this.machines = Map.copyOf(loader.load());
  }

  public Optional<StateMachine> get(String domain) {
    return Optional.ofNullable(machines.get(domain));
  }

  public Collection<StateMachine> all() {
    return machines.values();
  }

  /** 动作在某状态可用 ⇔ 该状态 ∈ from；同 action 的多条迁移（分派分支）合并 from 并保持声明顺序。 */
  public List<MetaView.MetaAction> actionsOf(String domain) {
    return get(domain)
        .map(machine -> {
          Map<String, List<String>> fromByAction = new LinkedHashMap<>();
          for (StateMachine.Transition transition : machine.transitions()) {
            fromByAction.computeIfAbsent(transition.action(), action -> new ArrayList<>())
                .addAll(transition.from());
          }
          return fromByAction.entrySet().stream()
              .map(entry -> new MetaView.MetaAction(
                  privilegeCode(machine.domain(), entry.getKey()),
                  entry.getKey(),
                  machine.domain() + ".action." + entry.getKey(),
                  entry.getValue().stream().distinct().toList()))
              .toList();
        })
        .orElse(List.of());
  }

  /**
   * meta 动作 code = 该动作的权限码（前端按 /me privileges 过滤按钮，如 {@code testrun-start}）。
   * 域名的权限码前缀由各域 Registrar 注册（camelCase 域 → 全小写、下划线 → 连字符，如 testRun→testrun、
   * board_space→board-space）；直接用 yml 的 domain 原样拼接会与权限码不符，导致按钮对所有人隐藏。
   */
  public static String privilegeCode(String domain, String action) {
    return domain.replace('_', '-').toLowerCase(java.util.Locale.ROOT) + "-" + action;
  }
}
