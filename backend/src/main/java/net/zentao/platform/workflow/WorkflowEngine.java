package net.zentao.platform.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 状态机执行器（platform 卡 §4.3）：{@code fire(target, action, comment)} 完成
 * 「分派迁移 → 守卫判定 → 落状态 → 执行副作用」。
 *
 * <p>错误语义：当前状态不在 from / 无匹配分派分支 → 42202；守卫不满足 → 42203 且 message 带守卫名。
 * 副作用在调用方事务内顺序执行——本类不加 {@code @Transactional}（01 §2.3 A5，事务归 app 层）。
 * 表达式与迁移在构造期完成编译：YAML 语法错误在启动时即暴露（{@link YamlStateMachineLoader}）。
 */
@Component
public class WorkflowEngine {

  private final Map<String, Map<String, List<CompiledTransition>>> machines;
  private final EffectExecutor effectExecutor;

  public WorkflowEngine(WorkflowRegistry registry, EffectExecutor effectExecutor) {
    this.effectExecutor = effectExecutor;
    Map<String, Map<String, List<CompiledTransition>>> compiled = new LinkedHashMap<>();
    for (StateMachine machine : registry.all()) {
      Map<String, List<CompiledTransition>> byAction = new LinkedHashMap<>();
      for (StateMachine.Transition transition : machine.transitions()) {
        byAction.computeIfAbsent(transition.action(), action -> new ArrayList<>()).add(compile(transition));
      }
      compiled.put(machine.domain(), byAction);
    }
    this.machines = compiled;
  }

  public StateMachine.Transition fire(WorkflowTarget target, String action) {
    return fire(target, action, null);
  }

  public StateMachine.Transition fire(WorkflowTarget target, String action, String comment) {
    List<CompiledTransition> candidates = candidates(target.objectType(), action);
    GuardEvaluator.FieldSource source = field -> "comment".equals(field) ? comment : target.field(field);

    CompiledTransition chosen = null;
    for (CompiledTransition candidate : candidates) {
      if (!candidate.transition().from().contains(target.status())) {
        continue;
      }
      if (candidate.when() != null && !candidate.when().test(source)) {
        continue;
      }
      chosen = candidate;
      break;
    }
    if (chosen == null) {
      throw ApiException.keyed(ErrorCode.STATE_ACTION_NOT_ALLOWED, "workflow.state.actionDenied", action);
    }

    for (CompiledGuard guard : chosen.guards()) {
      if (guard.when() != null && !guard.when().test(source)) {
        continue;
      }
      if (!guard.expr().test(source)) {
        throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "workflow.guard.notSatisfied", guard.definition().name());
      }
    }

    if (!chosen.transition().keepsStatus()) {
      target.applyStatus(chosen.transition().to());
    }
    effectExecutor.execute(chosen.transition(), target, comment);
    return chosen.transition();
  }

  /** 该域该动作的可用迁移（YAML 声明顺序）；动作未在状态机声明 → 42202。 */
  private List<CompiledTransition> candidates(String domain, String action) {
    Map<String, List<CompiledTransition>> byAction = machines.get(domain);
    if (byAction == null) {
      throw ApiException.keyed(ErrorCode.INTERNAL_ERROR, "workflow.machine.unregistered", domain);
    }
    List<CompiledTransition> candidates = byAction.get(action);
    if (candidates == null) {
      throw ApiException.keyed(ErrorCode.STATE_ACTION_NOT_ALLOWED, "workflow.action.unknown", action);
    }
    return candidates;
  }

  private static CompiledTransition compile(StateMachine.Transition transition) {
    List<CompiledGuard> guards = transition.guards().stream()
        .map(guard -> new CompiledGuard(guard, compile(guard.when()), compile(guard.expr())))
        .toList();
    return new CompiledTransition(transition, compile(transition.when()), guards);
  }

  private static GuardEvaluator.Condition compile(String expression) {
    return expression == null ? null : GuardEvaluator.compile(expression);
  }

  private record CompiledTransition(
      StateMachine.Transition transition, GuardEvaluator.Condition when, List<CompiledGuard> guards) {}

  private record CompiledGuard(
      StateMachine.Guard definition, GuardEvaluator.Condition when, GuardEvaluator.Condition expr) {}
}
