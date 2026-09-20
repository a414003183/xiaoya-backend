package net.zentao.platform.notification;

import java.time.Instant;
import java.util.List;
import net.zentao.platform.meta.SettingRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 站内通知记录器（platform 卡 §4.2）：域动作 notify 副作用逐接收人落行，type=&lt;资源单数&gt;-&lt;动作&gt;；
 * 事务提交后经 Spring Events 推送 SSE（01 §2.4，推送仅是提示，DB 为真源）。
 * B-PLT-02：落行前查收件人个人级设置 notify.&lt;type&gt;，值为 false（Boolean 或字符串 "false"）→ 跳过该收件人。
 */
@Component
public class NotificationRecorder {

  private final NotificationRepository repository;
  private final SettingRepository settingRepository;
  private final JsonMapper jsonMapper;
  private final ApplicationEventPublisher publisher;

  public NotificationRecorder(NotificationRepository repository, SettingRepository settingRepository,
      JsonMapper jsonMapper, ApplicationEventPublisher publisher) {
    this.repository = repository;
    this.settingRepository = settingRepository;
    this.jsonMapper = jsonMapper;
    this.publisher = publisher;
  }

  @Transactional
  public void record(List<String> recipients, String type, String objectType, long objectId,
      Long activityId, String title, String content, String createdBy) {
    if (recipients == null || recipients.isEmpty()) {
      return;
    }
    Instant now = Instant.now();
    List<NotificationView> views = recipients.stream().distinct()
        // ponytail: 逐收件人一次设置查询（个人级单键），通知单次接收人数量小，可接受；量大时升级为按键批量取
        .filter(recipient -> !muted(recipient, type))
        .map(recipient -> {
          NotificationPO po = new NotificationPO();
          po.setRecipient(recipient);
          po.setType(type);
          po.setObjectType(objectType);
          po.setObjectId(objectId);
          po.setActivityId(activityId);
          po.setTitle(title);
          po.setContent(content);
          po.setCreatedBy(createdBy);
          po.setCreatedAt(now);
          repository.insert(po);
          return NotificationViews.toView(po);
        })
        .toList();
    if (views.isEmpty()) {
      return;
    }
    publisher.publishEvent(new NotificationCreatedEvent(views));
  }

  /** 个人级 notify.<type> 设置为 false（Boolean 或字符串 "false"）→ 屏蔽；未设置/非 false 照常。 */
  private boolean muted(String recipient, String type) {
    return settingRepository.find(recipient, "notify", type)
        .map(po -> isFalse(po.getItemValue()))
        .orElse(false);
  }

  private boolean isFalse(String itemValue) {
    if (itemValue == null || itemValue.isBlank()) {
      return false;
    }
    try {
      JsonNode node = jsonMapper.readTree(itemValue);
      return node.isBoolean() && !node.asBoolean() || node.isString() && "false".equals(node.asString());
    } catch (Exception e) {
      return false;
    }
  }

  /** 事务提交后事件（监听方推 SSE）。 */
  public record NotificationCreatedEvent(List<NotificationView> views) {}
}
