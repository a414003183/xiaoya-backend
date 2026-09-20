package net.zentao.platform.activity;

import java.util.List;
import org.springframework.stereotype.Component;

/** 动态流查询（platform 卡 §5 末段协议；各域动态端点复用）。 */
@Component
public class ActivityQueryService {

  private final ActivityRecorder recorder;

  public ActivityQueryService(ActivityRecorder recorder) {
    this.recorder = recorder;
  }

  /** ActivityList（contract：items + hasMore；倒序游标）。 */
  public record ActivityList(List<ActivityRecorder.ActivityView> items, boolean hasMore) {}

  public ActivityList list(String objectType, long objectId, String actor, Integer limit, Long beforeId) {
    ActivityRecorder.ActivityList list = recorder.list(objectType, objectId, actor, limit, beforeId);
    return new ActivityList(list.items(), list.hasMore());
  }

  /** 按操作人的跨对象动态流（workspace 卡 §5 /my/activities，actor=@me）。 */
  public ActivityList listByActor(String actor, Integer limit, Long beforeId) {
    ActivityRecorder.ActivityList list = recorder.listByActor(actor, limit, beforeId);
    return new ActivityList(list.items(), list.hasMore());
  }
}
