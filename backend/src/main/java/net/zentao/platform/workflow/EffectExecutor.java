package net.zentao.platform.workflow;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.activity.ActivityRepository;
import net.zentao.platform.notification.NotificationRecorder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * workflow 副作用执行器（platform 卡 §4.3）：activity|notify|event|fieldSet，
 * 按 YAML 声明顺序在同一事务内执行（调用方 {@code app} 层持有事务）。
 */
@Component
public class EffectExecutor {

  private static final int NOTIFICATION_TITLE_MAX = 255;

  private final ActivityRecorder activityRecorder;
  private final NotificationRecorder notificationRecorder;
  private final ApplicationEventPublisher publisher;

  public EffectExecutor(ActivityRecorder activityRecorder, NotificationRecorder notificationRecorder,
      ApplicationEventPublisher publisher) {
    this.activityRecorder = activityRecorder;
    this.notificationRecorder = notificationRecorder;
    this.publisher = publisher;
  }

  /** 执行迁移的全部副作用；返回本次落下的动态流 id（供通知关联跳转，无 activity 副作用时为 null）。 */
  public Long execute(StateMachine.Transition transition, WorkflowTarget target, String comment) {
    Long activityId = null;
    for (StateMachine.Effect effect : transition.effects()) {
      switch (effect.kind()) {
        case ACTIVITY -> activityId = activityRecorder.record(
            target.actor(), target.objectType(), target.objectId(), effect.value(),
            detailOf(effect, target), comment);
        case NOTIFY -> recordNotifications(effect.value(), transition, target, activityId);
        case EVENT -> publisher.publishEvent(new WorkflowEvent(
            effect.value(), transition.action(), target.objectType(), target.objectId(), target.actor(),
            target.status()));
        case FIELD_SET -> applyFields(effect.values(), target);
      }
    }
    return activityId;
  }

  private void recordNotifications(
      String expression, StateMachine.Transition transition, WorkflowTarget target, Long activityId) {
    List<String> recipients = recipients(expression, target);
    if (recipients.isEmpty()) {
      return;
    }
    notificationRecorder.record(recipients, target.objectType() + "-" + transition.action(),
        target.objectType(), target.objectId(), activityId, title(target), null, target.actor());
  }

  /** 接收人表达式 → 账号列表：字段值可为单个账号或账号数组，去空去重。 */
  private static List<String> recipients(String expression, WorkflowTarget target) {
    Object value = target.field(expression);
    if (value == null) {
      return List.of();
    }
    if (value instanceof Collection<?> collection) {
      return collection.stream().filter(Objects::nonNull).map(String::valueOf)
          .filter(account -> !account.isBlank()).distinct().toList();
    }
    String account = String.valueOf(value);
    return account.isBlank() ? List.of() : List.of(account);
  }

  /** activity 附带 detail（platform §4.3）：键 = detail 字段名，值 = 目标字段名（运行期取值）。 */
  private static List<ActivityRepository.DetailField> detailOf(StateMachine.Effect effect, WorkflowTarget target) {
    if (effect.values().isEmpty()) {
      return null;
    }
    return effect.values().entrySet().stream().map(entry -> {
      Object value = target.field(String.valueOf(entry.getValue()));
      return new ActivityRepository.DetailField(String.valueOf(entry.getKey()), null,
          value == null ? null : String.valueOf(value));
    }).toList();
  }

  /** 通知标题 = 对象标题（前端据 type 渲染动作文案）；无 title 字段的对象退化为「资源 #id」。 */
  private static String title(WorkflowTarget target) {
    Object value = target.field("title");
    String text = value == null ? "" : String.valueOf(value).trim();
    if (text.isEmpty()) {
      text = target.objectType() + " #" + target.objectId();
    }
    return text.length() > NOTIFICATION_TITLE_MAX ? text.substring(0, NOTIFICATION_TITLE_MAX) : text;
  }

  private static void applyFields(Map<String, Object> values, WorkflowTarget target) {
    values.forEach((field, raw) -> target.setField(field, resolve(raw, target)));
  }

  /** fieldSet 取值：@now/@actor/@null 为动态值，其余按字面量落。 */
  private static Object resolve(Object raw, WorkflowTarget target) {
    if (raw instanceof String text) {
      return switch (text) {
        case "@now" -> Instant.now();
        case "@actor" -> target.actor();
        case "@null" -> null;
        default -> text;
      };
    }
    return raw;
  }
}
