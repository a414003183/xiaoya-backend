package net.zentao.platform.notification;

import java.time.Instant;

/** NotificationView（contract：id/recipient/type/objectType/objectId/activityId/title/content/readAt/createdBy/createdAt）。 */
public record NotificationView(
    long id,
    String recipient,
    String type,
    String objectType,
    long objectId,
    Long activityId,
    String title,
    String content,
    Instant readAt,
    String createdBy,
    Instant createdAt) {}
