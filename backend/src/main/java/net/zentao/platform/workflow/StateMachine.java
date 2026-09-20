package net.zentao.platform.workflow;

import java.util.List;
import java.util.Map;

/**
 * workflow 状态机定义（platform 卡 §4.3）：resources/workflow/&lt;domain&gt;.yml 的加载结果，
 * 由 {@link YamlStateMachineLoader} 校验后不可变。本类为纯声明模型，不含执行逻辑（执行见
 * {@link WorkflowEngine}）。
 */
public record StateMachine(String domain, String initial, List<String> states, List<Transition> transitions) {

  public StateMachine {
    states = states == null ? List.of() : List.copyOf(states);
    transitions = transitions == null ? List.of() : List.copyOf(transitions);
  }

  /**
   * 一次状态迁移声明。
   *
   * @param when 分派条件（可省略）：同 action 多迁移时按 YAML 声明顺序取首个 from 匹配且 when 成立的
   *             ——story 的 submit-review 双分支（needNotReview 真假）、plan 的 close 双分支即此机制。
   * @param to   目标状态；{@link #SELF_STATUS}（self）= 状态不变（assign/link 类动作）。
   */
  public record Transition(
      String action, List<String> from, String to, String when, List<Guard> guards, List<Effect> effects) {

    /** to 取值：状态保持不变。 */
    public static final String SELF_STATUS = "self";

    public Transition {
      from = from == null ? List.of() : List.copyOf(from);
      guards = guards == null ? List.of() : List.copyOf(guards);
      effects = effects == null ? List.of() : List.copyOf(effects);
    }

    public boolean keepsStatus() {
      return SELF_STATUS.equals(to);
    }
  }

  /** 守卫（platform §4.3）：when 可省略（恒生效）；任一 expr 不成立 → 42203 且 message 带 name。 */
  public record Guard(String name, String when, String expr) {}

  /**
   * 副作用（platform §4.3）：同事务按声明顺序执行。
   * ACTIVITY/NOTIFY/EVENT 用 {@code value}（动作名/接收人表达式/事件名），FIELD_SET 用 {@code values}。
   */
  public record Effect(Kind kind, String value, Map<String, Object> values) {

    public enum Kind { ACTIVITY, NOTIFY, EVENT, FIELD_SET }

    public Effect {
      // 不用 Map.copyOf：fieldSet 允许显式置空（null 值）
      values = values == null
          ? Map.of()
          : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(values));
    }
  }
}
