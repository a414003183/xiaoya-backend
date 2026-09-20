package net.zentao.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-3 双层数据权限（doc 卡 §7/§8）：库门禁（private 库白名单外 → 库 40302、库下文档 40401）、
 * 文档 ACL（private 白名单外列表 0 条 + 详情 40401、readers 只读写 40302、editors 可写）、
 * mine 库文档不进他人 GET /docs（超管亦不可见）、open 文档缺 doc-edit 功能码 → 40301、
 * acl=open 强制清空 editors/readers。
 */
class DocAclTest extends ApiTestSupport {

  private static final String EDITOR = "docacl-editor";
  private static final String OUTSIDER = "docacl-out";
  private static final String VIEWER = "docacl-viewer";
  private String admin;
  private String editor;
  private String outsider;
  private String viewer;
  private static String unique = "";

  @BeforeEach
  void prepare() throws Exception {
    admin = login("admin", "admin123");
    if (!accountsReady) {
      accountWithPrivileges(admin, EDITOR, editorCodes());
      accountWithPrivileges(admin, OUTSIDER, editorCodes());
      accountWithPrivileges(admin, VIEWER, "\"doc-view\",\"doc-space-view\"");
      accountsReady = true;
    }
    editor = login(EDITOR, "secret123");
    outsider = login(OUTSIDER, "secret123");
    viewer = login(VIEWER, "secret123");
    unique = "u" + System.nanoTime() % 100000;
  }

  private static String editorCodes() {
    return "\"doc-space-view\",\"doc-space-create\",\"doc-view\",\"doc-create\",\"doc-edit\",\"doc-delete\"";
  }

  private static boolean accountsReady;

  private long createSpace(String cookie, String json) throws Exception {
    return dataId(send("POST", "/api/v1/doc-spaces", json, cookie));
  }

  private long createDoc(String cookie, long spaceId, String json) throws Exception {
    return dataId(send("POST", "/api/v1/doc-spaces/" + spaceId + "/docs", json, cookie));
  }

  private long totalItems(String cookie, String query) throws Exception {
    return data(send("GET", "/api/v1/docs?" + query + "&limit=200", null, cookie)).at("/total").asLong();
  }

  @Test
  @DisplayName("private 文档白名单外：库内列表 0 条、详情 40401；editors 命中者可读可写")
  void privateDocHiddenFromOutsiders() throws Exception {
    long space = createSpace(admin, "{\"name\":\"文档 ACL 库-" + unique + "\",\"type\":\"custom\"}");
    long docId = createDoc(admin, space, "{\"title\":\"机密文档\",\"content\":\"机密\",\"acl\":\"private\","
        + "\"editors\":{\"accounts\":[\"" + EDITOR + "\"]}}");

    JsonNode spaceDocs = data(send("GET", "/api/v1/doc-spaces/" + space + "/docs", null, outsider));
    assertEquals(0, spaceDocs.at("/total").asLong(), spaceDocs.toString());
    assertEquals(0, totalItems(outsider, "filters%5BdocSpaceId%5D=" + space));
    assertEquals(404, send("GET", "/api/v1/docs/" + docId, null, outsider).statusCode());

    assertEquals(200, send("GET", "/api/v1/docs/" + docId, null, editor).statusCode());
    HttpResponse<String> edited = send("PATCH", "/api/v1/docs/" + docId,
        "{\"title\":\"机密文档改\",\"lockVersion\":0}", editor);
    assertEquals(200, edited.statusCode(), edited.body());
  }

  @Test
  @DisplayName("readers 只读：可读详情与快照，写动作一律 40302")
  void readersAreReadOnly() throws Exception {
    long space = createSpace(admin, "{\"name\":\"只读库-" + unique + "\",\"type\":\"custom\"}");
    long docId = createDoc(admin, space, "{\"title\":\"只读文档\",\"content\":\"正文\",\"status\":\"published\","
        + "\"acl\":\"private\",\"readers\":{\"accounts\":[\"" + EDITOR + "\"]}}");

    assertEquals(200, send("GET", "/api/v1/docs/" + docId, null, editor).statusCode());
    assertEquals(1, totalItems(editor, "filters%5BdocSpaceId%5D=" + space), "readers 命中者列表可见");
    assertEquals(403, send("POST", "/api/v1/docs/" + docId + "/save-draft",
        "{\"content\":\"越权\"}", editor).statusCode());
    assertEquals(403, send("POST", "/api/v1/docs/" + docId + "/publish", "{}", editor).statusCode());
    assertEquals(403, send("POST", "/api/v1/docs/" + docId + "/move",
        "{\"docSpaceId\":" + space + "}", editor).statusCode());
    assertEquals(403, send("GET", "/api/v1/docs/" + docId + "/versions/0", null, editor).statusCode());
    assertEquals(200, send("GET", "/api/v1/docs/" + docId + "/versions/1", null, editor).statusCode());
  }

  @Test
  @DisplayName("库门禁：private 库白名单外 → 库详情 40302、库下文档详情 40401（库不可见文档一律不可见）")
  void spaceGateHidesAllDocs() throws Exception {
    long space = createSpace(admin, "{\"name\":\"门禁库-" + unique + "\",\"type\":\"custom\",\"acl\":\"private\","
        + "\"whitelist\":{\"accounts\":[\"" + EDITOR + "\"]}}");
    long docId = createDoc(admin, space, "{\"title\":\"门禁文档\",\"content\":\"正文\"}");

    assertEquals(200, send("GET", "/api/v1/doc-spaces/" + space, null, editor).statusCode());
    assertEquals(200, send("GET", "/api/v1/docs/" + docId, null, editor).statusCode());

    assertEquals(403, send("GET", "/api/v1/doc-spaces/" + space, null, outsider).statusCode());
    assertEquals(403, send("GET", "/api/v1/doc-spaces/" + space + "/docs", null, outsider).statusCode());
    assertEquals(404, send("GET", "/api/v1/docs/" + docId, null, outsider).statusCode());
    assertEquals(0, totalItems(outsider, "filters%5BdocSpaceId%5D=" + space), "库不可见 → 文档列表不出现");
  }

  @Test
  @DisplayName("mine 库文档：不进他人 GET /docs（超管亦不可见），创建者自己可见")
  void mineSpaceDocsStayPrivate() throws Exception {
    long space = createSpace(editor, "{\"name\":\"我的空间-" + unique + "\",\"type\":\"mine\"}");
    long docId = createDoc(editor, space, "{\"title\":\"我的私密文档\",\"content\":\"私密\"}");

    assertEquals(1, totalItems(editor, "filters%5BdocSpaceId%5D=" + space));
    assertEquals(0, totalItems(admin, "filters%5BdocSpaceId%5D=" + space), "超管也不可见 mine 库文档");
    assertEquals(404, send("GET", "/api/v1/docs/" + docId, null, admin).statusCode());
    assertEquals(200, send("GET", "/api/v1/docs/" + docId, null, editor).statusCode());
  }

  @Test
  @DisplayName("功能权限与数据权限独立：无 doc-edit 码账号 PATCH open 文档 → 40301")
  void openDocWithoutEditPrivilege() throws Exception {
    long space = createSpace(admin, "{\"name\":\"功能码库-" + unique + "\",\"type\":\"custom\"}");
    long docId = createDoc(admin, space, "{\"title\":\"公开文档\",\"content\":\"正文\"}");

    assertEquals(200, send("GET", "/api/v1/docs/" + docId, null, viewer).statusCode());
    HttpResponse<String> denied = send("PATCH", "/api/v1/docs/" + docId,
        "{\"title\":\"改\",\"lockVersion\":0}", viewer);
    assertEquals(403, denied.statusCode(), denied.body());
    assertEquals(40301, json.readTree(denied.body()).at("/error/code").asInt(), denied.body());
  }

  @Test
  @DisplayName("acl=open 提交非空 editors/readers 被强制清空（doc 卡 §3.2/§8）")
  void openAclClearsWhitelists() throws Exception {
    long space = createSpace(admin, "{\"name\":\"清空库-" + unique + "\",\"type\":\"custom\"}");
    JsonNode created = data(send("POST", "/api/v1/doc-spaces/" + space + "/docs",
        "{\"title\":\"强制清空\",\"content\":\"x\",\"acl\":\"open\","
            + "\"editors\":{\"accounts\":[\"" + EDITOR + "\"]},\"readers\":{\"accounts\":[\"" + OUTSIDER + "\"]}}",
        admin));
    assertEquals(0, created.at("/editors/accounts").size(), created.toString());
    assertEquals(0, created.at("/readers/accounts").size(), created.toString());

    long docId = created.at("/id").asLong();
    JsonNode switched = data(send("PATCH", "/api/v1/docs/" + docId,
        "{\"acl\":\"private\",\"editors\":{\"accounts\":[\"" + EDITOR + "\"]},\"lockVersion\":0}", admin));
    assertTrue(switched.at("/editors/accounts").size() > 0, switched.toString());
    JsonNode reopened = data(send("PATCH", "/api/v1/docs/" + docId,
        "{\"acl\":\"open\",\"lockVersion\":1}", admin));
    assertEquals(0, reopened.at("/editors/accounts").size(), reopened.toString());
  }
}
