package net.zentao.task;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;

/** 任务域用例基座：真 HTTP 造产品→项目→执行→任务的固定夹具（P3 链路最短前置）。 */
abstract class TaskTestSupport extends net.zentao.ApiTestSupport {

  protected String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  protected long createProduct() throws Exception {
    return dataId(send("POST", "/api/v1/products",
        "{\"name\":\"任务产品" + System.nanoTime() + "\",\"acl\":\"public\"}", admin));
  }

  protected long createProject(long productId) throws Exception {
    return dataId(send("POST", "/api/v1/projects",
        "{\"name\":\"任务项目" + System.nanoTime() + "\",\"beginDate\":\"2026-09-01\",\"endDate\":\"2026-12-31\","
            + "\"productIds\":[" + productId + "]}",
        admin));
  }

  protected long createExecution(long projectId) throws Exception {
    return dataId(send("POST", "/api/v1/projects/" + projectId + "/executions",
        "{\"type\":\"sprint\",\"name\":\"S" + System.nanoTime() + "\",\"beginDate\":\"2026-09-01\","
            + "\"endDate\":\"2026-09-30\"}",
        admin));
  }

  /** 建一个执行并直接返回 executionId（多数用例只关心任务本身）。 */
  protected long freshExecution() throws Exception {
    return createExecution(createProject(createProduct()));
  }

  protected long createTask(long executionId, String title) throws Exception {
    return dataId(send("POST", "/api/v1/executions/" + executionId + "/tasks",
        "{\"title\":\"" + title + "\"}", admin));
  }

  protected HttpResponse<String> act(long taskId, String action) throws Exception {
    return act(taskId, action, "{}");
  }

  protected HttpResponse<String> act(long taskId, String action, String body) throws Exception {
    return send("POST", "/api/v1/tasks/" + taskId + "/" + action, body, admin);
  }

  protected JsonNode task(long taskId) throws Exception {
    return data(send("GET", "/api/v1/tasks/" + taskId, null, admin));
  }

  protected void assertStatus(HttpResponse<String> response, String status, String action) throws Exception {
    assertEquals(200, response.statusCode(), action + " → " + response.body());
    assertEquals(status, json.readTree(response.body()).at("/data/status").asText(), action);
  }

  protected void assertRejected(HttpResponse<String> response, String code, String action) throws Exception {
    assertEquals(422, response.statusCode(), action + " → " + response.body());
    assertEquals(true, response.body().contains(code), action + " → " + response.body());
  }
}
