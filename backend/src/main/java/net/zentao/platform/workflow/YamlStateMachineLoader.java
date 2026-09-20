package net.zentao.platform.workflow;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * workflow YAML 加载器（platform 卡 §4.3）：读取 {@code workflow/*.yml}（一个文件可含多文档，
 * 每文档一域——product.yml 即 product/branch/plan/release 四机），逐条规定校验，
 * **任何非法定义 → 启动失败**（IllegalStateException），不留运行时静默坑。
 */
@Component
public class YamlStateMachineLoader {

  /** notify 接收人表达式词表（platform §4.3 + self/pm/owner）。 */
  private static final Set<String> RECIPIENTS =
      Set.of("assignee", "createdBy", "reviewers", "notifyAccounts", "team", "self", "pm", "owner");

  private final String location;

  public YamlStateMachineLoader(
      @Value("${zentao.workflow.location:classpath*:workflow/*.yml}") String location) {
    this.location = location;
  }

  public Map<String, StateMachine> load() {
    Resource[] resources;
    try {
      resources = new PathMatchingResourcePatternResolver().getResources(location);
    } catch (IOException e) {
      throw new IllegalStateException("workflow 定义目录不可读：" + location, e);
    }
    if (resources.length == 0) {
      throw new IllegalStateException("未找到任何 workflow 定义：" + location);
    }
    Map<String, StateMachine> machines = new LinkedHashMap<>();
    Yaml yaml = new Yaml();
    for (Resource resource : resources) {
      String source = resource.getFilename();
      try (InputStream input = resource.getInputStream()) {
        for (Object document : yaml.loadAll(input)) {
          if (document == null) {
            continue;
          }
          StateMachine machine = parse(document, source);
          if (machines.putIfAbsent(machine.domain(), machine) != null) {
            throw invalid(source, "状态机 domain 重复：" + machine.domain());
          }
        }
      } catch (IOException e) {
        throw new IllegalStateException("workflow 定义不可读：" + source, e);
      }
    }
    return machines;
  }

  private static StateMachine parse(Object document, String source) {
    if (!(document instanceof Map<?, ?> map)) {
      throw invalid(source, "顶层不是键值映射");
    }
    String domain = text(map.get("domain"));
    if (domain == null) {
      throw invalid(source, "缺少 domain");
    }
    String initial = text(map.get("initial"));
    List<String> states = strings(map.get("states"));
    if (states.isEmpty()) {
      throw invalid(source, domain + " 的 states 为空");
    }
    if (initial == null || !states.contains(initial)) {
      throw invalid(source, domain + " 的 initial 必须 ∈ states：" + initial);
    }
    List<StateMachine.Transition> transitions = new ArrayList<>();
    for (Object item : list(map.get("transitions"))) {
      if (!(item instanceof Map<?, ?> raw)) {
        throw invalid(source, domain + " 的 transitions 项不是键值映射");
      }
      transitions.add(parseTransition(source, domain, raw, states));
    }
    if (transitions.isEmpty()) {
      throw invalid(source, domain + " 无任何 transitions");
    }
    return new StateMachine(domain, initial, states, transitions);
  }

  private static StateMachine.Transition parseTransition(
      String source, String domain, Map<?, ?> map, List<String> states) {
    String action = text(map.get("action"));
    if (action == null) {
      throw invalid(source, domain + " 的 transition 缺少 action");
    }
    String where = domain + "/" + action;
    String to = text(map.get("to"));
    if (to == null) {
      throw invalid(source, where + " 缺少 to（状态不变写 self）");
    }
    if (!StateMachine.Transition.SELF_STATUS.equals(to) && !states.contains(to)) {
      throw invalid(source, where + " 的 to 必须 ∈ states 或 self：" + to);
    }
    List<String> from = strings(map.get("from"));
    if (from.isEmpty()) {
      throw invalid(source, where + " 的 from 为空");
    }
    for (String state : from) {
      if (!states.contains(state)) {
        throw invalid(source, where + " 的 from 含未声明状态：" + state);
      }
    }
    String when = text(map.get("when"));
    compile(source, where + " 的 when", when);

    List<StateMachine.Guard> guards = new ArrayList<>();
    for (Object item : list(map.get("guards"))) {
      if (!(item instanceof Map<?, ?> rawGuard)) {
        throw invalid(source, where + " 的 guards 项不是键值映射");
      }
      String name = text(rawGuard.get("name"));
      String expr = text(rawGuard.get("expr"));
      if (name == null || expr == null) {
        throw invalid(source, where + " 的守卫缺少 name/expr");
      }
      compile(source, where + "/" + name, text(rawGuard.get("when")));
      compile(source, where + "/" + name, expr);
      guards.add(new StateMachine.Guard(name, text(rawGuard.get("when")), expr));
    }

    List<StateMachine.Effect> effects = new ArrayList<>();
    for (Object item : list(map.get("effects"))) {
      if (!(item instanceof Map<?, ?> rawEffect)) {
        throw invalid(source, where + " 的 effects 项不是键值映射");
      }
      effects.add(parseEffect(source, where, rawEffect));
    }
    return new StateMachine.Transition(action, from, to, when, guards, effects);
  }

  private static StateMachine.Effect parseEffect(String source, String where, Map<?, ?> map) {
    boolean activityWithDetail = map.size() == 2 && map.containsKey("activity") && map.containsKey("detail");
    if (map.size() != 1 && !activityWithDetail) {
      throw invalid(source, where + " 的 effect 必须只含一个键（activity|notify|event|fieldSet；activity 可附 detail）");
    }
    Map.Entry<?, ?> entry = map.entrySet().iterator().next();
    String kind = String.valueOf(entry.getKey());
    Object payload = entry.getValue();
    return switch (kind) {
      case "activity" -> {
        StateMachine.Effect effect = named(source, where, StateMachine.Effect.Kind.ACTIVITY, payload);
        Object detail = map.get("detail");
        if (detail == null) {
          yield effect;
        }
        if (!(detail instanceof Map<?, ?> detailMap) || detailMap.isEmpty()) {
          throw invalid(source, where + " 的 activity.detail 必须是非空键值映射（值 = 目标字段名）");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        detailMap.forEach((key, value) -> fields.put(String.valueOf(key), value));
        yield new StateMachine.Effect(StateMachine.Effect.Kind.ACTIVITY, effect.value(), fields);
      }
      case "event" -> named(source, where, StateMachine.Effect.Kind.EVENT, payload);
      case "notify" -> {
        StateMachine.Effect effect = named(source, where, StateMachine.Effect.Kind.NOTIFY, payload);
        if (!RECIPIENTS.contains(effect.value())) {
          throw invalid(source, where + " 的 notify 接收人不在词表内：" + effect.value() + "（" + RECIPIENTS + "）");
        }
        yield effect;
      }
      case "fieldSet" -> {
        if (!(payload instanceof Map<?, ?> values) || values.isEmpty()) {
          throw invalid(source, where + " 的 fieldSet 必须是非空键值映射");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        values.forEach((key, value) -> fields.put(String.valueOf(key), value));
        yield new StateMachine.Effect(StateMachine.Effect.Kind.FIELD_SET, null, fields);
      }
      default -> throw invalid(source, where + " 的 effect 类型未知：" + kind);
    };
  }

  private static StateMachine.Effect named(
      String source, String where, StateMachine.Effect.Kind kind, Object payload) {
    String value = text(payload);
    if (value == null) {
      throw invalid(source, where + " 的 effect 缺少取值：" + kind);
    }
    return new StateMachine.Effect(kind, value, null);
  }

  private static void compile(String source, String where, String expression) {
    if (expression == null) {
      return;
    }
    try {
      GuardEvaluator.compile(expression);
    } catch (IllegalArgumentException e) {
      throw invalid(source, where + " 表达式非法：" + e.getMessage());
    }
  }

  private static IllegalStateException invalid(String source, String message) {
    return new IllegalStateException("workflow 定义非法 [" + source + "] " + message);
  }

  private static List<?> list(Object value) {
    return value instanceof List<?> items ? items : List.of();
  }

  private static List<String> strings(Object value) {
    return list(value).stream().filter(item -> item != null).map(String::valueOf).toList();
  }

  private static String text(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }
}
