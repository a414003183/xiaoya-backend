package net.zentao.platform.meta;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Configuration;

/**
 * 通知域 meta 注册（platform 卡 §3.1/§6）：通知是平台自身资源、无独立业务域包，
 * 故注册口就在 platform.meta（与各域 Registrar 同构，域侧不得反向依赖）。
 * 列表筛选值域与个人开关目录（notify.&lt;type&gt;）都只在这里声明——前端不另立清单。
 */
@Configuration
public class NotificationMetaRegistrar {

  public NotificationMetaRegistrar(MetaRegistry metaRegistry) {
    metaRegistry.register("notification", new MetaView(
        "notification",
        List.of(
            new MetaView.MetaField("readAt", "select", null, null, "common.field.status", null, null, List.of(
                Map.of("value", "@null", "i18n", "platform.notification.tab.unread"),
                Map.of("value", "@notNull", "i18n", "platform.notification.status.read"))),
            // 个人开关目录（§3.7：GET/PUT /settings 的 notify.<type> 键；设置页开关逐项由本值域生成）。
            new MetaView.MetaField("type", "select", null, null, "platform.notification.field.type", null, null,
                List.of(
                    Map.of("value", "story-created", "i18n", "platform.notification.type.story-created"),
                    Map.of("value", "story-changed", "i18n", "platform.notification.type.story-changed"),
                    Map.of("value", "task-assigned", "i18n", "platform.notification.type.task-assigned"),
                    Map.of("value", "task-finished", "i18n", "platform.notification.type.task-finished"),
                    Map.of("value", "bug-created", "i18n", "platform.notification.type.bug-created"),
                    Map.of("value", "bug-resolved", "i18n", "platform.notification.type.bug-resolved"),
                    Map.of("value", "account-reset-password",
                        "i18n", "platform.notification.type.account-reset-password")))),
        new MetaView.MetaList(List.of("id", "title", "type", "createdAt"), "-id"),
        List.of(),
        Map.of()));
  }
}
