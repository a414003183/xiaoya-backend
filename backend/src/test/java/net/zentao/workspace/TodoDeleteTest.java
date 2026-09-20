package net.zentao.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A-07 待办软删（workspace 卡 §5 DELETE /todos/{id}）：
 * 创建人可删、删后详情 40401、重复删 40401；非创建人且非负责人 → 40302；超管豁免。
 */
class TodoDeleteTest extends ApiTestSupport {

  private static final String CODES = "\"todo-view\",\"todo-create\",\"todo-delete\"";

  private static final AtomicInteger SEQ = new AtomicInteger();

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  private record Actor(String account, String cookie) {}

  private Actor newActor() throws Exception {
    String account = "todoremove" + SEQ.incrementAndGet();
    return new Actor(account, accountWithPrivileges(admin, account, CODES));
  }

  @Test
  @DisplayName("软删矩阵：创建人删成功且详情/重复删 40401；他人删 40302；超管豁免可删")
  void deleteMatrix() throws Exception {
    Actor alice = newActor();
    Actor bob = newActor();
    long todo = dataId(send("POST", "/api/v1/todos", "{\"title\":\"待删待办\"}", alice.cookie()));

    HttpResponse<String> bobDelete = send("DELETE", "/api/v1/todos/" + todo, null, bob.cookie());
    assertEquals(403, bobDelete.statusCode(), bobDelete.body());
    assertTrue(bobDelete.body().contains("40302"), bobDelete.body());
    assertEquals(200, send("GET", "/api/v1/todos/" + todo, null, alice.cookie()).statusCode(), "未被他人删掉");

    long own = dataId(send("POST", "/api/v1/todos", "{\"title\":\"本人删除\"}", alice.cookie()));
    assertEquals(200, send("DELETE", "/api/v1/todos/" + own, null, alice.cookie()).statusCode());
    HttpResponse<String> gone = send("GET", "/api/v1/todos/" + own, null, alice.cookie());
    assertEquals(404, gone.statusCode(), gone.body());
    assertTrue(gone.body().contains("40401"), gone.body());
    HttpResponse<String> again = send("DELETE", "/api/v1/todos/" + own, null, alice.cookie());
    assertEquals(404, again.statusCode(), again.body());
    assertTrue(again.body().contains("40401"), again.body());

    long superAdminTarget = dataId(send("POST", "/api/v1/todos", "{\"title\":\"超管可删\"}", alice.cookie()));
    assertEquals(200, send("DELETE", "/api/v1/todos/" + superAdminTarget, null, admin).statusCode(), "超管豁免");
  }
}
