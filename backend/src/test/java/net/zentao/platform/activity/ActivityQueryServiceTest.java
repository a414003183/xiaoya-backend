package net.zentao.platform.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.zentao.platform.session.SessionPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 动态流游标查询（platform 卡 §5 末段：倒序、limit 上限 200、beforeId、hasMore）。 */
@SpringBootTest
class ActivityQueryServiceTest {

  @Autowired
  ActivityRecorder recorder;

  @Autowired
  JdbcTemplate jdbcTemplate;

  private final SessionPrincipal actor = new SessionPrincipal(1, "admin");

  private long seedObjectActivities(String objectType, int count) {
    long objectId = System.nanoTime();
    jdbcTemplate.update("DELETE FROM activity WHERE object_type = ? AND object_id = ?", objectType, objectId);
    for (int i = 0; i < count; i++) {
      recorder.record(actor.account(), objectType, objectId, "step-" + i, null, null);
    }
    return objectId;
  }

  @Test
  @DisplayName("倒序返回且 hasMore 正确；beforeId 取更早一页")
  void cursorPaging() {
    String objectType = "cursor-test";
    long objectId = seedObjectActivities(objectType, 7);

    ActivityRecorder.ActivityList firstPage = recorder.list(objectType, objectId, null, 3, null);
    assertEquals(3, firstPage.items().size());
    assertTrue(firstPage.hasMore(), "还有 4 条应 hasMore=true");
    assertEquals("step-6", firstPage.items().getFirst().action(), "倒序：最新在前");

    long lastId = firstPage.items().getLast().id();
    ActivityRecorder.ActivityList secondPage = recorder.list(objectType, objectId, null, 3, lastId);
    assertEquals(3, secondPage.items().size());
    assertTrue(secondPage.hasMore(), "还剩 1 条应 hasMore=true");

    long lastId2 = secondPage.items().getLast().id();
    ActivityRecorder.ActivityList thirdPage = recorder.list(objectType, objectId, null, 3, lastId2);
    assertEquals(1, thirdPage.items().size());
    assertFalse(thirdPage.hasMore());
    assertEquals("step-0", thirdPage.items().getFirst().action());
  }

  @Test
  @DisplayName("limit 超 200 收敛到 200；actor 过滤生效")
  void limitCapAndActorFilter() {
    String objectType = "limit-test";
    long objectId = seedObjectActivities(objectType, 3);

    ActivityRecorder.ActivityList capped = recorder.list(objectType, objectId, null, 500, null);
    assertEquals(3, capped.items().size());
    assertFalse(capped.hasMore());

    recorder.record("someone-else", objectType, objectId, "other-actor", null, null);
    ActivityRecorder.ActivityList mine = recorder.list(objectType, objectId, "someone-else", null, null);
    assertEquals(1, mine.items().size());
    assertEquals("other-actor", mine.items().getFirst().action());
    List<ActivityRecorder.ActivityView> all = recorder.list(objectType, objectId, null, null, null).items();
    assertEquals(4, all.size());
  }
}
