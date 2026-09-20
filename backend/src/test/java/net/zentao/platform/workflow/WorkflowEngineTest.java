package net.zentao.platform.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.meta.MetaView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 状态机引擎（platform 卡 §4.3 / §8）：分派迁移、守卫错误码、副作用落库、meta 同源、非法定义启动失败。
 * 直接使用真 story.yml / product.yml，不另造夹具机器——YAML 即被测对象。
 */
@SpringBootTest
class WorkflowEngineTest {

  @Autowired
  WorkflowEngine engine;

  @Autowired
  WorkflowRegistry registry;

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("from 不匹配 → 42202，且不落状态")
  void illegalFromYields42202() {
    FixtureTarget story = story(9101, "draft");

    ApiException error = assertThrows(ApiException.class, () -> engine.fire(story, "pass"));

    assertEquals(ErrorCode.STATE_ACTION_NOT_ALLOWED, error.errorCode());
    assertEquals("draft", story.status(), "失败不得迁移");
  }

  @Test
  @DisplayName("submit-review 按 needNotReview 双分支分派：false 走评审并通知评审人，true 直达激活")
  void submitReviewDispatchesByNeedNotReview() {
    FixtureTarget reviewed = story(9102, "draft").with("needNotReview", false).with("reviewers", List.of("dev1"));
    engine.fire(reviewed, "submit-review");
    assertEquals("reviewing", reviewed.status());
    assertEquals("submitted", lastActivityAction("story", 9102));
    assertEquals(List.of("dev1"), notificationRecipients("story-submit-review", 9102));

    FixtureTarget direct = story(9103, "draft").with("needNotReview", true);
    engine.fire(direct, "submit-review");
    assertEquals("active", direct.status());
    assertEquals("activated", lastActivityAction("story", 9103), "直达分支不写 submitted");
    assertEquals(List.of(), notificationRecipients("story-submit-review", 9103));
  }

  @Test
  @DisplayName("守卫失败 → 42203 且 message 带守卫名")
  void guardFailureCarriesName() {
    FixtureTarget story = story(9104, "draft").with("needNotReview", false).with("reviewers", List.of());

    ApiException error = assertThrows(ApiException.class, () -> engine.fire(story, "submit-review"));

    assertEquals(ErrorCode.GUARD_NOT_SATISFIED, error.errorCode());
    assertTrue(error.getMessage().contains("reviewers-required"), error.getMessage());
    assertEquals("draft", story.status());
  }

  @Test
  @DisplayName("reject 缺 comment 不通过；带 comment 走通并落 remark")
  void rejectRequiresComment() {
    FixtureTarget story = story(9105, "reviewing").with("createdBy", "admin");

    ApiException error = assertThrows(ApiException.class, () -> engine.fire(story, "reject"));
    assertTrue(error.getMessage().contains("comment-required"), error.getMessage());

    engine.fire(story, "reject", "请补充验收标准");
    assertEquals("draft", story.status());
    assertEquals("rejected", lastActivityAction("story", 9105));
    assertEquals("请补充验收标准", remarkOfLastActivity("story", 9105));
  }

  @Test
  @DisplayName("close：closedReason=duplicate 缺 duplicateOfId → 守卫拦截；补齐后 fieldSet 落 closedAt/closedBy")
  void closeDuplicateRequiresTarget() {
    FixtureTarget story = story(9106, "active").with("closedReason", "duplicate");

    ApiException error = assertThrows(ApiException.class, () -> engine.fire(story, "close"));
    assertTrue(error.getMessage().contains("duplicate-of-required"), error.getMessage());

    story.with("duplicateOfId", 42L);
    engine.fire(story, "close");
    assertEquals("closed", story.status());
    assertEquals("admin", story.field("closedBy"));
    assertNotNull(story.field("closedAt"));
  }

  @Test
  @DisplayName("assign：状态不变（to=self），落 assignedAt 并通知被指派人")
  void assignKeepsStatus() {
    FixtureTarget story = story(9107, "active").with("assignee", "dev1");

    engine.fire(story, "assign");

    assertEquals("active", story.status());
    assertNotNull(story.field("assignedAt"));
    assertEquals("assigned", lastActivityAction("story", 9107));
    assertEquals(List.of("dev1"), notificationRecipients("story-assign", 9107));
  }

  @Test
  @DisplayName("plan close 双分支：done 同时落 finishedAt，cancel 不落")
  void planCloseBranchesByReason() {
    FixtureTarget done = new FixtureTarget("plan", 9201, "admin", "doing")
        .with("title", "计划-done").with("closedReason", "done");
    engine.fire(done, "close");
    assertEquals("closed", done.status());
    assertNotNull(done.field("finishedAt"), "closedReason=done 应同时落 finishedAt");

    FixtureTarget cancel = new FixtureTarget("plan", 9202, "admin", "doing")
        .with("title", "计划-cancel").with("closedReason", "cancel");
    engine.fire(cancel, "close");
    assertEquals("closed", cancel.status());
    assertNull(cancel.field("finishedAt"), "closedReason=cancel 不落 finishedAt");
  }

  @Test
  @DisplayName("plan link：in 运算符守卫（objectType ∈ [story,bug]、ids 非空）")
  void planLinkGuards() {
    FixtureTarget plan = new FixtureTarget("plan", 9203, "admin", "doing")
        .with("title", "计划-link").with("objectType", "story").with("ids", List.of(1L, 2L));
    engine.fire(plan, "link");
    assertEquals("doing", plan.status());
    assertEquals("linked", lastActivityAction("plan", 9203));

    FixtureTarget wrongType = new FixtureTarget("plan", 9204, "admin", "doing")
        .with("title", "计划-bad").with("objectType", "doc").with("ids", List.of(1L));
    ApiException error = assertThrows(ApiException.class, () -> engine.fire(wrongType, "link"));
    assertTrue(error.getMessage().contains("object-type"), error.getMessage());

    FixtureTarget emptyIds = new FixtureTarget("plan", 9205, "admin", "doing")
        .with("title", "计划-empty").with("objectType", "bug").with("ids", List.of());
    assertTrue(assertThrows(ApiException.class, () -> engine.fire(emptyIds, "link"))
        .getMessage().contains("ids-required"));
  }

  @Test
  @DisplayName("release terminate：通知 notifyAccounts，标题取对象 title")
  void releaseTerminateNotifies() {
    FixtureTarget release = new FixtureTarget("release", 9301, "admin", "normal")
        .with("title", "1.0 发布").with("notifyAccounts", List.of("dev1", "guest"));

    engine.fire(release, "terminate");

    assertEquals("terminated", release.status());
    assertEquals(List.of("dev1", "guest"), notificationRecipients("release-terminate", 9301));
    assertEquals("1.0 发布", jdbcTemplate.queryForObject(
        "select title from notification where type = ? and object_id = ? limit 1",
        String.class, "release-terminate", 9301L));
  }

  @Test
  @DisplayName("meta allowedStatus 与 YAML 同源：多分支 action 合并 from，product.yml 四机齐载")
  void metaActionsMatchYaml() {
    List<MetaView.MetaAction> storyActions = registry.actionsOf("story");
    // requirement §4 的九行 = submit-review 双分支 + 另外七个动作 → 去重后八个动作码
    assertEquals(8, storyActions.size(), "story.yml 八个动作码");
    MetaView.MetaAction submitReview = storyActions.stream()
        .filter(action -> action.action().equals("submit-review"))
        .findFirst()
        .orElseThrow();
    assertEquals(List.of("draft", "changed"), submitReview.allowedStatus(), "双分支 from 合并去重");
    assertEquals("story-submit-review", submitReview.code());
    assertEquals("story.action.submit-review", submitReview.i18n());

    assertEquals(2, registry.actionsOf("product").size());
    assertEquals(3, registry.actionsOf("branch").size());
    assertEquals(6, registry.actionsOf("plan").size(), "close 双分支去重后六个动作码");
    assertEquals(3, registry.actionsOf("release").size());
    assertTrue(registry.actionsOf("no-such-domain").isEmpty());
  }

  @Test
  @DisplayName("非法定义（to 未声明）→ 加载即失败")
  void invalidDefinitionFailsLoad() {
    YamlStateMachineLoader loader = new YamlStateMachineLoader("classpath*:workflow-invalid/*.yml");

    IllegalStateException error = assertThrows(IllegalStateException.class, loader::load);

    assertTrue(error.getMessage().contains("broken.yml"), error.getMessage());
    assertTrue(error.getMessage().contains("to"), error.getMessage());
  }

  @Test
  @DisplayName("未知动作/未注册域：42202 与 50001 区分")
  void unknownActionAndDomain() {
    FixtureTarget story = story(9108, "draft");
    assertEquals(ErrorCode.STATE_ACTION_NOT_ALLOWED,
        assertThrows(ApiException.class, () -> engine.fire(story, "no-such-action")).errorCode());

    FixtureTarget orphan = new FixtureTarget("no-such-domain", 9109, "admin", "draft");
    assertEquals(ErrorCode.INTERNAL_ERROR,
        assertThrows(ApiException.class, () -> engine.fire(orphan, "close")).errorCode());
  }

  private static FixtureTarget story(long objectId, String status) {
    return new FixtureTarget("story", objectId, "admin", status).with("title", "需求 " + objectId);
  }

  private String lastActivityAction(String objectType, long objectId) {
    return jdbcTemplate.queryForObject(
        "select action from activity where object_type = ? and object_id = ? order by id desc limit 1",
        String.class, objectType, objectId);
  }

  private String remarkOfLastActivity(String objectType, long objectId) {
    return jdbcTemplate.queryForObject(
        "select remark from activity where object_type = ? and object_id = ? order by id desc limit 1",
        String.class, objectType, objectId);
  }

  private List<String> notificationRecipients(String type, long objectId) {
    return jdbcTemplate.queryForList(
        "select recipient from notification where type = ? and object_id = ? order by id",
        String.class, type, objectId);
  }

  /** 内存版 WorkflowTarget：字段即 guard 表达式可见的全部输入。 */
  private static final class FixtureTarget implements WorkflowTarget {

    private final String objectType;
    private final long objectId;
    private final String actor;
    private final Map<String, Object> fields = new HashMap<>();
    private String status;

    private FixtureTarget(String objectType, long objectId, String actor, String status) {
      this.objectType = objectType;
      this.objectId = objectId;
      this.actor = actor;
      this.status = status;
    }

    private FixtureTarget with(String field, Object value) {
      fields.put(field, value);
      return this;
    }

    @Override
    public String objectType() {
      return objectType;
    }

    @Override
    public long objectId() {
      return objectId;
    }

    @Override
    public String actor() {
      return actor;
    }

    @Override
    public String status() {
      return status;
    }

    @Override
    public void applyStatus(String status) {
      this.status = status;
    }

    @Override
    public Object field(String name) {
      return fields.get(name);
    }

    @Override
    public void setField(String name, Object value) {
      fields.put(name, value);
    }
  }
}
