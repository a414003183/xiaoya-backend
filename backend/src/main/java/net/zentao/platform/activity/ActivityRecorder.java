package net.zentao.platform.activity;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 动态流记录器（01 §2.4）：app 层显式调用，非 AOP。各域在命令处理器内记录业务动作。
 * 与 {@link net.zentao.platform.notification.NotificationRecorder} 解耦：通知由域动作的 notify 副作用另行触发。
 */
@Component
public class ActivityRecorder {

  private final ActivityRepository repository;

  public ActivityRecorder(ActivityRepository repository) {
    this.repository = repository;
  }

  /** 追加一条动态流，返回新行 id（workflow notify 副作用据此关联跳转上下文）。 */
  public Long record(String actor, String objectType, long objectId, String action,
      List<ActivityRepository.DetailField> detail, String remark) {
    ActivityPO po = new ActivityPO();
    po.setObjectType(objectType);
    po.setObjectId(objectId);
    po.setActor(actor);
    po.setAction(action);
    po.setDetail(repository.writeDetail(detail));
    po.setRemark(remark);
    po.setOccurredAt(Instant.now());
    repository.insert(po);
    return po.getId();
  }

  /** ActivityView（contract：id/objectType/objectId/actor/action/detail/remark/occurredAt）。 */
  public record ActivityView(
      long id, String objectType, long objectId, String actor, String action,
      List<ActivityRepository.DetailField> detail, String remark, Instant occurredAt) {}

  public ActivityView toView(ActivityPO po) {
    return new ActivityView(po.getId(), po.getObjectType(), po.getObjectId(), po.getActor(),
        po.getAction(), repository.parseDetail(po.getDetail()), po.getRemark(), po.getOccurredAt());
  }

  /** 游标列表（contract ActivityList：items + hasMore，倒序，limit 默认 50 上限 200）。 */
  public record ActivityList(List<ActivityView> items, boolean hasMore) {}

  public ActivityList list(String objectType, long objectId, String actor, Integer limit, Long beforeId) {
    int effectiveLimit = limit == null ? 50 : Math.min(Math.max(limit, 1), 200);
    List<ActivityPO> rows = repository.cursor(objectType, objectId, actor, effectiveLimit, beforeId);
    boolean hasMore = rows.size() > effectiveLimit;
    List<ActivityView> items = rows.stream()
        .limit(effectiveLimit)
        .map(this::toView)
        .toList();
    return new ActivityList(items, hasMore);
  }

  /** 按操作人的跨对象游标列表（/my/activities：actor=@me，不限对象）。 */
  public ActivityList listByActor(String actor, Integer limit, Long beforeId) {
    int effectiveLimit = limit == null ? 50 : Math.min(Math.max(limit, 1), 200);
    List<ActivityPO> rows = repository.cursorByActor(actor, effectiveLimit, beforeId);
    boolean hasMore = rows.size() > effectiveLimit;
    List<ActivityView> items = rows.stream()
        .limit(effectiveLimit)
        .map(this::toView)
        .toList();
    return new ActivityList(items, hasMore);
  }
}
