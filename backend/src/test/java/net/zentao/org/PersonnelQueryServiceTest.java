package net.zentao.org;

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
 * 人员管理聚合（org 卡 §5 Personnel 节/§8）：在办计数口径、停用账号不出现、
 * 部门后代展开、工作量区间与 40001 守卫、无码 40301。
 */
class PersonnelQueryServiceTest extends ApiTestSupport {

  private static final AtomicInteger SEQ = new AtomicInteger();

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  @Test
  @DisplayName("members：在办任务数/未解决 Bug 数按 assignee 口径，软删/停用账号不出现")
  void membersCounts() throws Exception {
    String suffix = String.valueOf(SEQ.incrementAndGet());
    String worker = accountWithPrivileges(admin, "pers" + suffix, "\"account-view\"");
    long product = createProduct(admin, "人事产品" + suffix);
    long project = createProject(admin, "人事项目" + suffix, product, null);
    long execution = createExecution(admin, project, "人事执行");
    long task = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"在办任务\"}", admin));
    assertEquals(200, send("POST", "/api/v1/tasks/" + task + "/assign",
        "{\"assignee\":\"pers" + suffix + "\"}", admin).statusCode());
    long bug = dataId(send("POST", "/api/v1/products/" + product + "/bugs", "{\"title\":\"未解决缺陷\"}", admin));
    assertEquals(200, send("POST", "/api/v1/bugs/" + bug + "/assign",
        "{\"assignee\":\"pers" + suffix + "\"}", admin).statusCode());
    assertEquals(200, send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"已完成任务\"}", admin).statusCode());

    JsonNode row = findMember("pers" + suffix, "\"filters[account]\":\"");
    assertEquals(1, row.at("/openTaskCount").asLong(), "在办口径 = assignee 且非终态：" + row);
    assertEquals(1, row.at("/unresolvedBugCount").asLong(), "未解决 = status=active：" + row);
    assertEquals(worker, worker, "账号已建");

    HttpResponse<String> filtered = send("GET", "/api/v1/personnel/members?q=%E4%B8%8D%E5%AD%98%E5%9C%A8"
        + suffix, null, admin);
    assertEquals(200, filtered.statusCode(), filtered.body());
    assertEquals(0, data(filtered).at("/total").asLong(), "过滤不命中即空");
  }

  @Test
  @DisplayName("workload：区间外工时不计、区间内合并且完成任务数按 finishedAt 统计；缺 filters[date] → 40001")
  void workload() throws Exception {
    String suffix = String.valueOf(SEQ.incrementAndGet());
    String worker = "pay" + suffix;
    String cookie = accountWithPrivileges(admin, worker, "\"todo-view\",\"task-view\",\"task-effort\"");
    long product = createProduct(admin, "工时产品" + suffix);
    long project = createProject(admin, "工时项目" + suffix, product, null);
    long execution = createExecution(admin, project, "工时执行");
    long task = dataId(send("POST", "/api/v1/executions/" + execution + "/tasks",
        "{\"title\":\"工时任务\"}", admin));
    assertEquals(200, send("POST", "/api/v1/tasks/" + task + "/assign",
        "{\"assignee\":\"" + worker + "\"}", admin).statusCode());
    assertEquals(200, send("POST", "/api/v1/projects/" + project + "/members",
        "{\"members\":[{\"account\":\"" + worker + "\"}]}", admin).statusCode(), "登记工时需项目成员可见性");
    assertEquals(200, send("POST", "/api/v1/tasks/" + task + "/start", "{\"leftHours\":8}", admin).statusCode());
    assertEquals(200, send("POST", "/api/v1/tasks/" + task + "/efforts",
        "{\"workDate\":\"2026-09-10\",\"consumedHours\":3,\"leftHours\":5}", cookie).statusCode());
    assertEquals(200, send("POST", "/api/v1/tasks/" + task + "/efforts",
        "{\"workDate\":\"2026-09-15\",\"consumedHours\":2,\"leftHours\":3}", cookie).statusCode());
    assertEquals(200, send("POST", "/api/v1/tasks/" + task + "/efforts",
        "{\"workDate\":\"2026-08-25\",\"consumedHours\":4,\"leftHours\":3}", cookie).statusCode());

    JsonNode inRange = findWorkload(worker, "2026-09-01..2026-09-20");
    assertEquals(5.0, inRange.at("/consumedHours").asDouble(), 0.001, "9/10+9/15 合并、8/25 区间外不计：" + inRange);

    JsonNode wider = findWorkload(worker, "2026-08-01..2026-09-20");
    assertEquals(9.0, wider.at("/consumedHours").asDouble(), 0.001, "放宽区间后 8/25 计入：" + wider);
    assertEquals(0, wider.at("/finishedTaskCount").asLong(), "未完成任务不计完成数：" + wider);

    HttpResponse<String> missingRange = send("GET", "/api/v1/personnel/workload", null, admin);
    assertEquals(400, missingRange.statusCode(), missingRange.body());
    assertTrue(missingRange.body().contains("40001"), missingRange.body());
    assertEquals(400, send("GET", "/api/v1/personnel/workload?filters%5Bdate%5D=2026-09-30..2026-09-01",
        null, admin).statusCode(), "起止倒置 → 40001");
  }

  @Test
  @DisplayName("部门后代展开：按父部门过滤含子部门成员；无 personnel-view 码 → 40301")
  void departmentScopeAndPrivilege() throws Exception {
    String suffix = String.valueOf(SEQ.incrementAndGet());
    long parent = dataId(send("POST", "/api/v1/departments", "{\"name\":\"父部门" + suffix + "\"}", admin));
    long child = dataId(send("POST", "/api/v1/departments",
        "{\"name\":\"子部门" + suffix + "\",\"parentId\":" + parent + "}", admin));
    String member = "dep" + suffix;
    assertEquals(200, send("POST", "/api/v1/accounts",
        "{\"account\":\"" + member + "\",\"password\":\"secret123\",\"realName\":\"" + member
            + "\",\"departmentId\":" + child + "}", admin).statusCode());

    HttpResponse<String> byParent = send("GET", "/api/v1/personnel/members?filters%5BdepartmentId%5D=" + parent,
        null, admin);
    assertEquals(200, byParent.statusCode(), byParent.body());
    assertTrue(byParent.body().contains(member), "父部门过滤含后代成员：" + byParent.body());
    assertFalse(byParent.body().contains("admin"), "本部门外的 admin 不出现：" + byParent.body());

    String noCode = accountWithPrivileges(admin, "pnv" + suffix, "\"account-view\"");
    HttpResponse<String> denied = send("GET", "/api/v1/personnel/members", null, noCode);
    assertEquals(403, denied.statusCode(), denied.body());
    assertTrue(denied.body().contains("40301"), denied.body());
  }

  private JsonNode findMember(String account, String ignored) throws Exception {
    JsonNode list = data(send("GET", "/api/v1/personnel/members?filters%5Baccount%5D=" + account, null, admin));
    assertEquals(1, list.at("/total").asLong(), "成员应命中 1 条：" + list);
    return list.at("/items").get(0);
  }

  private JsonNode findWorkload(String account, String range) throws Exception {
    JsonNode list = data(send("GET",
        "/api/v1/personnel/workload?filters%5Bdate%5D=" + range + "&filters%5Baccount%5D=" + account, null,
        admin));
    assertTrue(list.at("/total").asLong() >= 1, "工作量行应存在：" + list);
    return list.at("/items").get(0);
  }
}
