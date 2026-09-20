package net.zentao.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-4 干系人（project 卡 §3.8/§5/§8）：program/project 两类对象添加、重复 account → 42201、
 * 软删后再添加同 account 成功（同键复活）、移除后 40401，以及干系人可见 private 项目集/项目（§7）。
 */
class StakeholderHandlerTest extends ApiTestSupport {

  private String admin;

  @BeforeEach
  void loginAdmin() throws Exception {
    admin = login("admin", "admin123");
  }

  private long createProgram(String name) throws Exception {
    return dataId(send("POST", "/api/v1/programs", "{\"name\":\"" + name + "\"}", admin));
  }

  @Test
  @DisplayName("干系人生命周期：添加/列表/重复 42201/软删/再添加同 account 成功且复用同键行")
  void lifecycle() throws Exception {
    long product = createProduct(admin, "干系人产品");
    long project = createProject(admin, "干系人项目", product, null);
    String url = "/api/v1/projects/" + project + "/stakeholders";
    accountWithPrivileges(admin, "t4-stk-a", "\"stakeholder-view\"");

    JsonNode created = data(send("POST", url,
        "{\"account\":\"t4-stk-a\",\"type\":\"outside\",\"isKey\":true,\"source\":\"客户\"}", admin));
    long stakeholderId = created.at("/id").asLong();
    assertEquals("project", created.at("/objectType").asText(), created.toString());
    assertEquals(project, created.at("/objectId").asLong(), created.toString());
    assertEquals("outside", created.at("/type").asText(), created.toString());
    assertTrue(created.at("/isKey").asBoolean(), created.toString());
    assertEquals("客户", created.at("/source").asText(), created.toString());
    assertEquals("admin", created.at("/createdBy").asText(), created.toString());

    HttpResponse<String> duplicated = send("POST", url, "{\"account\":\"t4-stk-a\",\"type\":\"inside\"}", admin);
    assertEquals(422, duplicated.statusCode(), duplicated.body());
    assertTrue(duplicated.body().contains("42201"), duplicated.body());

    JsonNode list = data(send("GET", url + "?filters%5Btype%5D=outside", null, admin));
    assertEquals(1, list.at("/total").asLong(), list.toString());
    assertEquals(stakeholderId, list.at("/items/0/id").asLong(), list.toString());

    assertEquals(200, send("DELETE", url + "/" + stakeholderId, null, admin).statusCode());
    assertEquals(0, data(send("GET", url, null, admin)).at("/total").asLong());
    HttpResponse<String> removedAgain = send("DELETE", url + "/" + stakeholderId, null, admin);
    assertEquals(404, removedAgain.statusCode(), removedAgain.body());
    assertTrue(removedAgain.body().contains("40401"), removedAgain.body());

    // 软删后再添加同 account：成功且沿用软删行（同 id——unique 键占位，只能复活不能插新行）
    JsonNode revived = data(send("POST", url, "{\"account\":\"t4-stk-a\",\"type\":\"inside\"}", admin));
    assertEquals(stakeholderId, revived.at("/id").asLong(), revived.toString());
    assertEquals("inside", revived.at("/type").asText(), revived.toString());
    assertEquals(1, data(send("GET", url, null, admin)).at("/total").asLong());
  }

  @Test
  @DisplayName("校验与对象守卫：account 不存在/type 非法 42201，对象不存在 40401，跨对象删除 40401")
  void validation() throws Exception {
    long product = createProduct(admin, "干系人校验产品");
    long project = createProject(admin, "干系人校验项目", product, null);
    long program = createProgram("干系人校验项目集");
    accountWithPrivileges(admin, "t4-stk-b", "\"stakeholder-view\"");

    String url = "/api/v1/projects/" + project + "/stakeholders";
    HttpResponse<String> unknownAccount = send("POST", url, "{\"account\":\"t4-ghost\",\"type\":\"inside\"}", admin);
    assertEquals(422, unknownAccount.statusCode(), unknownAccount.body());
    assertTrue(unknownAccount.body().contains("account"), unknownAccount.body());

    HttpResponse<String> badType = send("POST", url, "{\"account\":\"t4-stk-b\",\"type\":\"other\"}", admin);
    assertEquals(422, badType.statusCode(), badType.body());
    assertTrue(badType.body().contains("type"), badType.body());

    assertEquals(404, send("POST", "/api/v1/projects/999999/stakeholders",
        "{\"account\":\"t4-stk-b\",\"type\":\"inside\"}", admin).statusCode());

    long projectStakeholder = dataId(send("POST", url, "{\"account\":\"t4-stk-b\",\"type\":\"inside\"}", admin));
    // 项目干系人 id 拿到项目集端点删除 → 40401（对象不匹配）
    HttpResponse<String> crossObject = send("DELETE",
        "/api/v1/programs/" + program + "/stakeholders/" + projectStakeholder, null, admin);
    assertEquals(404, crossObject.statusCode(), crossObject.body());

    // program 端点同族：添加/列表
    dataId(send("POST", "/api/v1/programs/" + program + "/stakeholders",
        "{\"account\":\"t4-stk-b\",\"type\":\"inside\",\"isKey\":true}", admin));
    JsonNode programList = data(send("GET", "/api/v1/programs/" + program + "/stakeholders", null, admin));
    assertEquals(1, programList.at("/total").asLong(), programList.toString());
    assertEquals("program", programList.at("/items/0/objectType").asText(), programList.toString());
  }

  @Test
  @DisplayName("§7 可见性：干系人可见 private 项目（详情 200），移除后 40302")
  void stakeholderSeesPrivateProject() throws Exception {
    long product = createProduct(admin, "干系人可见产品");
    long project = createProject(admin, "干系人可见项目", product, "\"acl\":\"private\"");
    String stakeholder = accountWithPrivileges(admin, "t4-stk-vis", "\"project-view\"");

    String url = "/api/v1/projects/" + project + "/stakeholders";
    assertEquals(403, send("GET", "/api/v1/projects/" + project, null, stakeholder).statusCode());
    long id = dataId(send("POST", url, "{\"account\":\"t4-stk-vis\",\"type\":\"inside\"}", admin));
    assertEquals(200, send("GET", "/api/v1/projects/" + project, null, stakeholder).statusCode());

    assertEquals(200, send("DELETE", url + "/" + id, null, admin).statusCode());
    HttpResponse<String> hidden = send("GET", "/api/v1/projects/" + project, null, stakeholder);
    assertEquals(403, hidden.statusCode(), hidden.body());
    assertTrue(hidden.body().contains("40302"), hidden.body());
  }
}
