package net.zentao.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 我的地盘聚合（workspace 卡 §3.4/§8）：role 映射等价过滤、非法 role 40001、
 * 目标域 DataScope 交集剔除、计数口径、/my/activities 恒 actor=@me。
 */
class MyAggregationTest extends ApiTestSupport {

  private static final String CODES =
      "\"my-view\",\"todo-view\",\"todo-create\",\"task-view\",\"task-finish\",\"bug-view\",\"story-view\"";

  private static final AtomicInteger SEQ = new AtomicInteger();

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  @Test
  @DisplayName("role 映射：finisher 等价 filters[finishedBy]=@me；非法 role → 40001")
  void roleMapping() throws Exception {
    Actor user = newActor();
    long product = createProduct(admin, "聚合产品" + SEQ.incrementAndGet());
    long project = createProject(admin, "聚合项目" + SEQ.incrementAndGet(), product, null);
    long execution = createExecution(admin, project, "聚合执行");
    long task = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks", "{\"title\":\"聚合任务\"}", admin));
    addMember(project, user.account());
    assertEquals(200, send("POST", "/api/v1/tasks/" + task + "/assign",
        "{\"assignee\":\"" + user.account() + "\"}", admin).statusCode());

    JsonNode assignee = data(send("GET", "/api/v1/my/tasks?role=assignee", null, user.cookie()));
    assertEquals(1, assignee.at("/total").asLong(), assignee.toString());
    assertTrue(assignee.at("/items").toString().contains("聚合任务"), assignee.toString());

    JsonNode finisher = data(send("GET", "/api/v1/my/tasks?role=finisher", null, user.cookie()));
    assertEquals(0, finisher.at("/total").asLong(), "未完成前 finisher 页签为空：" + finisher);

    assertEquals(200, send("POST", "/api/v1/tasks/" + task + "/finish",
        "{\"consumedHours\":1}", user.cookie()).statusCode());
    JsonNode afterFinish = data(send("GET", "/api/v1/my/tasks?role=finisher", null, user.cookie()));
    assertEquals(1, afterFinish.at("/total").asLong(), "完成后归入 finisher：" + afterFinish);

    assertEquals(400, send("GET", "/api/v1/my/tasks?role=bogus", null, user.cookie()).statusCode(),
        "非法 role → 40001");
    assertEquals(400, send("GET", "/api/v1/my/bugs?role=finisher", null, user.cookie()).statusCode(),
        "bugs 的 role 词表独立（finisher 非法）");
  }

  @Test
  @DisplayName("/my/summary 四计数与 role=assignee 口径一致（待办 wait/doing、任务非终态、Bug active、需求非 closed/draft）")
  void summaryCounts() throws Exception {
    Actor user = newActor();
    String name = user.account();
    long product = createProduct(admin, "计数产品" + SEQ.incrementAndGet());
    long project = createProject(admin, "计数项目" + SEQ.incrementAndGet(), product, null);
    long execution = createExecution(admin, project, "计数执行");
    addMember(project, name);

    long task = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks", "{\"title\":\"计数任务\"}", admin));
    assertEquals(200, send("POST", "/api/v1/tasks/" + task + "/assign",
        "{\"assignee\":\"" + name + "\"}", admin).statusCode());
    long bug = dataId(send("POST", "/api/v1/products/" + product + "/bugs", "{\"title\":\"计数缺陷\"}", admin));
    assertEquals(200, send("POST", "/api/v1/bugs/" + bug + "/assign",
        "{\"assignee\":\"" + name + "\"}", admin).statusCode());
    long story = createStory(admin, product, "计数需求", "\"assignee\":\"" + name + "\",\"needNotReview\":true");
    assertEquals(200, send("POST", "/api/v1/stories/" + story + "/submit-review", "{}", admin).statusCode(),
        "提交评审后需求进入非 draft 态");
    assertEquals(200, send("POST", "/api/v1/todos",
        "{\"title\":\"计数待办\",\"assignee\":\"" + name + "\"}", admin).statusCode());
    long done = dataId(send("POST", "/api/v1/todos",
        "{\"title\":\"已完成待办\",\"assignee\":\"" + name + "\"}", admin));
    assertEquals(200, send("POST", "/api/v1/todos/" + done + "/finish", null, admin).statusCode());

    JsonNode summary = data(send("GET", "/api/v1/my/summary", null, user.cookie()));
    assertEquals(1, summary.at("/todoCount").asLong(), "待办只计 wait/doing：" + summary);
    assertEquals(1, summary.at("/taskCount").asLong(), summary.toString());
    assertEquals(1, summary.at("/bugCount").asLong(), summary.toString());
    assertEquals(1, summary.at("/storyCount").asLong(), summary.toString());

    // changing 态计入开放需求数（gap B-WKS-10：六态全集 reviewing,active,changing,changed 的回归锁）
    long changing = createStory(admin, product, "变更中需求", "\"assignee\":\"" + name + "\",\"needNotReview\":true");
    assertEquals(200, send("POST", "/api/v1/stories/" + changing + "/submit-review", "{}", admin).statusCode());
    assertEquals(200, send("POST", "/api/v1/stories/" + changing + "/change", null, admin).statusCode(),
        "needNotReview 需求 submit-review 直达 active，发起变更进入 changing");
    JsonNode summary2 = data(send("GET", "/api/v1/my/summary", null, user.cookie()));
    assertEquals(2, summary2.at("/storyCount").asLong(), "changing 也是开放态：" + summary2);
  }

  @Test
  @DisplayName("DataScope 交集：私有产品/项目下的对象不出现在我的聚合结果")
  void dataScopeIntersection() throws Exception {
    Actor user = newActor();
    long product = createProduct(admin, "私有产品" + SEQ.incrementAndGet());
    assertEquals(200, send("PATCH", "/api/v1/products/" + product,
        "{\"acl\":\"private\",\"lockVersion\":0}", admin).statusCode());
    long project = createProject(admin, "私有项目" + SEQ.incrementAndGet(), product, "\"acl\":\"private\"");
    long execution = createExecution(admin, project, "私有执行");
    long task = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"私有任务\"}", admin));
    assertEquals(200, send("POST", "/api/v1/tasks/" + task + "/assign",
        "{\"assignee\":\"" + user.account() + "\"}", admin).statusCode());

    JsonNode mine = data(send("GET", "/api/v1/my/tasks?role=assignee", null, user.cookie()));
    assertEquals(0, mine.at("/total").asLong(), "不可见执行下的任务不出现：" + mine);
    assertFalse(mine.at("/items").toString().contains("私有任务"), mine.toString());

    JsonNode executionTasks = data(send("GET", "/api/v1/executions/" + execution + "/tasks", null, admin));
    assertEquals(1, executionTasks.at("/total").asLong(), "超管的任务列表不受可见集限制：" + executionTasks);
  }

  @Test
  @DisplayName("/my/activities 恒只含 actor=@me 的动态流条目")
  void activitiesByActor() throws Exception {
    Actor user = newActor();
    assertEquals(200, send("POST", "/api/v1/todos", "{\"title\":\"动态待办\"}", user.cookie()).statusCode());

    JsonNode list = data(send("GET", "/api/v1/my/activities", null, user.cookie()));
    assertTrue(list.at("/items").size() >= 1, list.toString());
    JsonNode first = list.at("/items").get(0);
    assertEquals(user.account(), first.at("/actor").asText(), first.toString());
    assertEquals("todo", first.at("/objectType").asText(), first.toString());
    assertEquals("created", first.at("/action").asText(), first.toString());

    JsonNode adminList = data(send("GET", "/api/v1/my/activities", null, admin));
    assertFalse(adminList.at("/items").toString().contains(user.account() + "\",\"action\":\"created\""),
        "他人动态不入我的时间线");
  }

  private Actor newActor() throws Exception {
    String account = "myagg" + SEQ.incrementAndGet();
    return new Actor(account, accountWithPrivileges(admin, account, CODES));
  }

  private void addMember(long projectId, String account) throws Exception {
    assertEquals(200, send("POST", "/api/v1/projects/" + projectId + "/members",
        "{\"members\":[{\"account\":\"" + account + "\"}]}", admin).statusCode());
  }

  private record Actor(String account, String cookie) {}
}
