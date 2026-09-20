package net.zentao.platform.workflow;

/**
 * workflow {@code event} 副作用的进程内事件（platform 卡 §4.3）：Spring Events 广播，无 MQ（00 §2）。
 *
 * <p>ponytail: 载荷为通用记录，消费方按 {@link #eventType()}（YAML 里的事件名，如 StorySubmitted）过滤；
 * 某域需要强类型事件 record（02 §4 &lt;Entity&gt;&lt;VerbPast&gt;）时，再在该域注册工厂替换本记录。
 */
public record WorkflowEvent(
    String eventType, String action, String objectType, long objectId, String actor, String status) {}
