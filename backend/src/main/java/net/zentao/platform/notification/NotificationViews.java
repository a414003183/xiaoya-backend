package net.zentao.platform.notification;

/** PO → View 装配。 */
final class NotificationViews {

  private NotificationViews() {}

  static NotificationView toView(NotificationPO po) {
    return new NotificationView(po.getId(), po.getRecipient(), po.getType(), po.getObjectType(),
        po.getObjectId() == null ? 0 : po.getObjectId(), po.getActivityId(), po.getTitle(),
        po.getContent(), po.getReadAt(), po.getCreatedBy(), po.getCreatedAt());
  }
}
