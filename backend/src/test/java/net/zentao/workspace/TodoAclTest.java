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
 * 待办数据权限（workspace 卡 §7/§8）：列表归属收敛、私有详情 40302、
 * 非当事人写 40302、指派后归属转移、超管豁免、objectTitle 现算。
 */
class TodoAclTest extends ApiTestSupport {

  private static final String ALL_CODES =
      "\"todo-view\",\"todo-create\",\"todo-edit\",\"todo-start\",\"todo-finish\",\"todo-activate\",\"todo-close\","
          + "\"todo-assign\"";

  private static final AtomicInteger SEQ = new AtomicInteger();

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  /** 本用例内的当事人（组名/账号名全局唯一：同 JVM 共享 H2 库，重复建组会 42201 duplicate）。 */
  private Actor newActor(String codes) throws Exception {
    String account = "todoacl" + SEQ.incrementAndGet();
    return new Actor(account, accountWithPrivileges(admin, account, codes));
  }

  private record Actor(String account, String cookie) {}

  @Test
  @DisplayName("列表归属：他人待办不出现在我的列表；指派后归入接收人列表")
  void listOwnership() throws Exception {
    Actor alice = newActor(ALL_CODES);
    Actor bob = newActor(ALL_CODES);
    long aliceTodo = dataId(send("POST", "/api/v1/todos", "{\"title\":\"爱丽丝的待办\"}", alice.cookie()));

    HttpResponse<String> bobList = send("GET", "/api/v1/todos", null, bob.cookie());
    assertEquals(200, bobList.statusCode(), bobList.body());
    assertFalse(bobList.body().contains("爱丽丝的待办"), "他人待办不得出现在我的列表：" + bobList.body());
    assertTrue(send("GET", "/api/v1/todos", null, alice.cookie()).body().contains("爱丽丝的待办"), "本人列表可见");

    assertEquals(200, send("POST", "/api/v1/todos/" + aliceTodo + "/assign",
        "{\"assignee\":\"" + bob.account() + "\"}", alice.cookie()).statusCode());
    assertTrue(send("GET", "/api/v1/todos", null, bob.cookie()).body().contains("爱丽丝的待办"), "指派后归属接收人");
  }

  @Test
  @DisplayName("私有待办：仅创建人与负责人可读，他人详情 40302；超管豁免；非私有可读")
  void privateDetail() throws Exception {
    Actor alice = newActor(ALL_CODES);
    Actor bob = newActor(ALL_CODES);
    long todo = dataId(send("POST", "/api/v1/todos", "{\"title\":\"私有待办\",\"isPrivate\":true}", alice.cookie()));

    assertEquals(200, send("GET", "/api/v1/todos/" + todo, null, alice.cookie()).statusCode(), "创建人可读");
    HttpResponse<String> bobRead = send("GET", "/api/v1/todos/" + todo, null, bob.cookie());
    assertEquals(403, bobRead.statusCode(), bobRead.body());
    assertTrue(bobRead.body().contains("40302"), bobRead.body());
    assertEquals(200, send("GET", "/api/v1/todos/" + todo, null, admin).statusCode(), "超管豁免");

    long plain = dataId(send("POST", "/api/v1/todos", "{\"title\":\"普通待办\"}", alice.cookie()));
    assertEquals(200, send("GET", "/api/v1/todos/" + plain, null, bob.cookie()).statusCode(), "非私有待办可读");
  }

  @Test
  @DisplayName("写权：非创建人且非负责人 PATCH/动作 → 40302；负责人接手后可写")
  void writeAccess() throws Exception {
    Actor alice = newActor(ALL_CODES);
    Actor bob = newActor(ALL_CODES);
    long todo = dataId(send("POST", "/api/v1/todos", "{\"title\":\"只读待办\"}", alice.cookie()));

    HttpResponse<String> bobPatch = send("PATCH", "/api/v1/todos/" + todo,
        "{\"priority\":2,\"lockVersion\":0}", bob.cookie());
    assertEquals(403, bobPatch.statusCode(), bobPatch.body());
    assertTrue(bobPatch.body().contains("40302"), bobPatch.body());
    assertEquals(403, send("POST", "/api/v1/todos/" + todo + "/start", null, bob.cookie()).statusCode(),
        "非当事人 start");

    assertEquals(200, send("POST", "/api/v1/todos/" + todo + "/assign",
        "{\"assignee\":\"" + bob.account() + "\"}", alice.cookie()).statusCode());
    assertEquals(200, send("POST", "/api/v1/todos/" + todo + "/start", null, bob.cookie()).statusCode(), "负责人可写");
  }

  @Test
  @DisplayName("功能权限：无 todo-view 码账号访问列表 → 40301")
  void missingPrivilege() throws Exception {
    Actor viewer = newActor("\"account-view\"");
    HttpResponse<String> denied = send("GET", "/api/v1/todos", null, viewer.cookie());
    assertEquals(403, denied.statusCode(), denied.body());
    assertTrue(denied.body().contains("40301"), denied.body());
  }

  @Test
  @DisplayName("objectTitle 现算：关联 Bug 取标题、对象不存在 42201、custom 为空")
  void objectTitle() throws Exception {
    long product = createProduct(admin, "待办产品");
    long bug = dataId(send("POST", "/api/v1/products/" + product + "/bugs", "{\"title\":\"待办关联缺陷\"}", admin));
    JsonNode view = data(send("POST", "/api/v1/todos",
        "{\"title\":\"跟进缺陷\",\"type\":\"bug\",\"objectId\":" + bug + "}", admin));
    assertEquals("待办关联缺陷", view.at("/objectTitle").asText(), view.toString());

    assertEquals(422, send("POST", "/api/v1/todos",
        "{\"title\":\"关联不存在对象\",\"type\":\"task\",\"objectId\":99999999}", admin).statusCode(),
        "type≠custom 且对象不存在 → 42201");

    JsonNode custom = data(send("POST", "/api/v1/todos", "{\"title\":\"自建待办\"}", admin));
    assertTrue(custom.at("/objectTitle").isNull(), "custom 无关联对象标题");
  }
}
