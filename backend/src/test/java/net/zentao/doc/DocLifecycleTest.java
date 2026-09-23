package net.zentao.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import net.zentao.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-3 文档生命周期与版本链（doc 卡 §4/§8）：首发 v1、再发 v(N+1)、v0 无改动 42203、
 * 快照逐字节不可变、save-draft 不升 version、hasDraft 翻转、阅读计数、章节 path 级联与删除级联、
 * 动态流动作序列、批量部分成功、version=0 权限、digest 缺省口径。
 */
class DocLifecycleTest extends ApiTestSupport {

  private static final String A = "doclife-a";
  private static final String B = "doclife-b";
  private String admin;
  private String reader;
  private long spaceId;
  private static boolean accountsReady;
  private static String unique = "";

  @BeforeEach
  void prepare() throws Exception {
    admin = login("admin", "admin123");
    if (!accountsReady) {
      accountWithPrivileges(admin, A, codes());
      accountsReady = true;
    }
    reader = login(A, "secret123");
    unique = "u" + System.nanoTime() % 100000;
    spaceId = dataId(send("POST", "/api/v1/doc-spaces",
        "{\"name\":\"生命周期库-" + unique + "\",\"type\":\"custom\"}", admin));
  }

  private static String codes() {
    return "\"doc-space-view\",\"doc-space-create\",\"doc-space-edit\",\"doc-view\",\"doc-create\",\"doc-edit\",\"doc-delete\"";
  }

  private JsonNode createDoc(String json) throws Exception {
    return data(send("POST", "/api/v1/doc-spaces/" + spaceId + "/docs", json, admin));
  }

  private JsonNode createDoc(String cookie, String json) throws Exception {
    return data(send("POST", "/api/v1/doc-spaces/" + spaceId + "/docs", json, cookie));
  }

  private JsonNode doc(long docId) throws Exception {
    return data(send("GET", "/api/v1/docs/" + docId, null, admin));
  }

  private JsonNode doc(String cookie, long docId) throws Exception {
    return data(send("GET", "/api/v1/docs/" + docId, null, cookie));
  }

  private HttpResponse<String> action(long docId, String action, String body, String cookie) throws Exception {
    return send("POST", "/api/v1/docs/" + docId + "/" + action, body, cookie);
  }

  private static int viewOf(JsonNode list, long docId) {
    for (JsonNode item : list.at("/items")) {
      if (item.at("/id").asLong() == docId) {
        return item.at("/views").asInt();
      }
    }
    throw new AssertionError("列表中缺少文档 " + docId + "：" + list);
  }

  @Test
  @DisplayName("首发：draft → publish 生成 v1 快照、version=1、status=published；create 直发缺 content → 42201")
  void firstPublish() throws Exception {
    HttpResponse<String> missingContent = send("POST", "/api/v1/doc-spaces/" + spaceId + "/docs",
        "{\"title\":\"直发无正文\",\"status\":\"published\"}", admin);
    assertEquals(422, missingContent.statusCode(), missingContent.body());
    assertEquals("required",
        json.readTree(missingContent.body()).at("/error/fields/content").asText(), missingContent.body());

    JsonNode draft = createDoc("{\"title\":\"首发文档\",\"content\":\"首版正文\"}");
    assertEquals("draft", draft.at("/status").asText(), draft.toString());
    assertEquals(0, draft.at("/version").asInt(), draft.toString());
    assertFalse(draft.at("/hasDraft").asBoolean(), draft.toString());

    JsonNode published = data(action(draft.at("/id").asLong(), "publish", "{}", admin));
    assertEquals("published", published.at("/status").asText(), published.toString());
    assertEquals(1, published.at("/version").asInt(), published.toString());

    JsonNode versions = data(send("GET", "/api/v1/docs/" + draft.at("/id").asLong() + "/versions", null, admin));
    assertEquals(1, versions.at("/total").asLong(), versions.toString());
    assertEquals(1, versions.at("/items/0/version").asInt(), versions.toString());
    assertEquals("首版正文", versions.at("/items/0/content").asText(), versions.toString());
  }

  @Test
  @DisplayName("再发：v0 无改动 42203；有改动 version+1 且旧快照逐字节不变（三连版本链）")
  void republishAndImmutability() throws Exception {
    long docId = createDoc("{\"title\":\"版本链\",\"content\":\"正文一\"}").at("/id").asLong();
    data(action(docId, "publish", "{}", admin));

    // 无改动再发 → 42203（workflow 守卫 draft-changed）
    HttpResponse<String> unchanged = action(docId, "publish", "{}", admin);
    assertEquals(422, unchanged.statusCode(), unchanged.body());
    assertEquals(42203, json.readTree(unchanged.body()).at("/error/code").asInt(), unchanged.body());

    data(action(docId, "save-draft", "{\"content\":\"正文二\"}", admin));
    JsonNode second = data(action(docId, "publish", "{}", admin));
    assertEquals(2, second.at("/version").asInt(), second.toString());

    data(action(docId, "save-draft", "{\"content\":\"正文三\"}", admin));
    JsonNode third = data(action(docId, "publish", "{}", admin));
    assertEquals(3, third.at("/version").asInt(), third.toString());

    JsonNode versions = data(send("GET", "/api/v1/docs/" + docId + "/versions", null, admin));
    assertEquals(3, versions.at("/total").asLong(), versions.toString());
    assertEquals(3, versions.at("/items/0/version").asInt(), versions.toString());
    assertEquals(2, versions.at("/items/1/version").asInt(), versions.toString());
    assertEquals(1, versions.at("/items/2/version").asInt(), versions.toString());
    assertEquals("正文一", versions.at("/items/2/content").asText(), "v1 快照必须逐字节不变");
    assertEquals("正文二", versions.at("/items/1/content").asText(), versions.toString());
  }

  @Test
  @DisplayName("save-draft：不升 version、不改已发布正文、hasDraft 随差异翻转、不产生动态流")
  void saveDraftKeepsPublishedSnapshot() throws Exception {
    long docId = createDoc("{\"title\":\"草稿文档\",\"content\":\"已发布正文\"}").at("/id").asLong();
    data(action(docId, "publish", "{}", admin));

    JsonNode saved = data(action(docId, "save-draft", "{\"title\":\"草稿标题\",\"content\":\"未发布正文\"}", admin));
    assertEquals(1, saved.at("/version").asInt(), "save-draft 不升 version");
    assertEquals("published", saved.at("/status").asText(), saved.toString());
    assertTrue(saved.at("/hasDraft").asBoolean(), "v0 与快照有差异 → hasDraft=true");
    assertEquals("未发布正文", saved.at("/content").asText(), "可编辑者详情见 v0 工作副本");

    // 列表 hasDraft 徽标翻转 + 列表不带正文
    JsonNode list = data(send("GET", "/api/v1/docs?filters%5BdocSpaceId%5D=" + spaceId, null, admin));
    assertEquals(1, list.at("/total").asLong(), list.toString());
    JsonNode row = list.at("/items/0");
    assertTrue(row.at("/hasDraft").asBoolean(), list.toString());
    assertTrue(row.at("/content").isNull(), "列表不携带 content");

    // 动态流：save-draft 不写记录（当前仅 created + published）
    JsonNode activities = data(send("GET", "/api/v1/docs/" + docId + "/activities", null, admin));
    assertEquals(2, activities.at("/items").size(), activities.toString());
    assertEquals("published", activities.at("/items/0/action").asText(), activities.toString());
    assertEquals("created", activities.at("/items/1/action").asText(), activities.toString());
  }

  @Test
  @DisplayName("阅读计数：published 详情 views+1、draft 不计、软删详情 40401")
  void viewCounting() throws Exception {
    long draftId = createDoc("{\"title\":\"草稿不计数\",\"content\":\"x\"}").at("/id").asLong();
    assertEquals(0, doc(draftId).at("/views").asInt());
    assertEquals(0, doc(draftId).at("/views").asInt());

    long publishedId = createDoc("{\"title\":\"发布计数\",\"content\":\"x\",\"status\":\"published\"}")
        .at("/id").asLong();
    assertEquals(1, doc(publishedId).at("/views").asInt(), "首次详情 views+1");
    assertEquals(2, doc(publishedId).at("/views").asInt(), "再次详情继续累计");
    send("GET", "/api/v1/docs/" + publishedId + "/versions/1", null, admin);
    JsonNode list = data(send("GET", "/api/v1/docs?filters%5BdocSpaceId%5D=" + spaceId, null, admin));
    assertEquals(2, viewOf(list, publishedId), "列表不计数：" + list);
    assertEquals(3, doc(publishedId).at("/views").asInt(), "仅详情累计");

    // views 自增不推进 lockVersion：详情返回的 lockVersion 仍可用于 PATCH（无关字段乐观锁不受影响）
    JsonNode current = doc(publishedId);
    HttpResponse<String> patched = send("PATCH", "/api/v1/docs/" + publishedId,
        "{\"title\":\"发布计数改\",\"lockVersion\":" + current.at("/lockVersion").asInt() + "}", admin);
    assertEquals(200, patched.statusCode(), patched.body());

    assertEquals(200, send("DELETE", "/api/v1/docs/" + publishedId, null, admin).statusCode());
    assertEquals(404, send("GET", "/api/v1/docs/" + publishedId, null, admin).statusCode());
  }

  @Test
  @DisplayName("章节树：path 按父链生成、move 改 parentId 级联重建、删父连带软删子文档")
  void chapterTreeCascade() throws Exception {
    long root = createDoc("{\"title\":\"章节根\",\"content\":\"r\"}").at("/id").asLong();
    long child = createDoc("{\"title\":\"子章节\",\"content\":\"c\",\"parentId\":" + root + "}").at("/id").asLong();
    long leaf = createDoc("{\"title\":\"孙章节\",\"content\":\"l\",\"parentId\":" + child + "}").at("/id").asLong();
    assertEquals("," + root + "," + child + "," + leaf + ",", doc(leaf).at("/path").asText());

    // 成环：父章节选自身/后代 → 42201
    HttpResponse<String> cycle = send("PATCH", "/api/v1/docs/" + root,
        "{\"parentId\":" + child + ",\"lockVersion\":0}", admin);
    assertEquals(422, cycle.statusCode(), cycle.body());
    assertEquals("cycle", json.readTree(cycle.body()).at("/error/fields/parentId").asText(), cycle.body());

    long other = createDoc("{\"title\":\"新父章节\",\"content\":\"o\"}").at("/id").asLong();
    HttpResponse<String> moved = send("POST", "/api/v1/docs/" + root + "/move",
        "{\"docSpaceId\":" + spaceId + ",\"parentId\":" + other + "}", admin);
    assertEquals(200, moved.statusCode(), moved.body());
    String movedPath = json.readTree(moved.body()).at("/data/path").asText();
    assertEquals("," + other + "," + root + ",", movedPath, moved.body());
    assertEquals(movedPath + child + "," + leaf + ",", doc(leaf).at("/path").asText(), "子树 path 级联重建");

    // 子树查询：按 parentId 过滤命中直接子文档
    JsonNode subtree = data(send("GET",
        "/api/v1/doc-spaces/" + spaceId + "/docs?filters%5BparentId%5D=" + other, null, admin));
    assertEquals(1, subtree.at("/total").asLong(), subtree.toString());
    assertEquals(root, subtree.at("/items/0/id").asLong(), subtree.toString());

    // 删父文档：子文档一并软删
    assertEquals(200, send("DELETE", "/api/v1/docs/" + root, null, admin).statusCode());
    assertEquals(404, send("GET", "/api/v1/docs/" + child, null, admin).statusCode());
    assertEquals(404, send("GET", "/api/v1/docs/" + leaf, null, admin).statusCode());
  }

  @Test
  @DisplayName("动态流动作名序列 created → published → edited → moved（倒序响应）")
  void activitySequence() throws Exception {
    long docId = createDoc("{\"title\":\"动态流\",\"content\":\"一\"}").at("/id").asLong();
    data(action(docId, "publish", "{}", admin));
    data(action(docId, "save-draft", "{\"content\":\"二\"}", admin));
    data(action(docId, "publish", "{}", admin));
    data(send("POST", "/api/v1/docs/" + docId + "/move", "{\"docSpaceId\":" + spaceId + "}", admin));

    JsonNode activities = data(send("GET", "/api/v1/docs/" + docId + "/activities", null, admin));
    assertEquals("moved", activities.at("/items/0/action").asText(), activities.toString());
    assertEquals("edited", activities.at("/items/1/action").asText(), activities.toString());
    assertEquals("published", activities.at("/items/2/action").asText(), activities.toString());
    assertEquals("created", activities.at("/items/3/action").asText(), activities.toString());
  }

  @Test
  @DisplayName("批量：delete/move 逐项部分成功，越权项 error=40302，成功项副作用齐全")
  void batchPartialSuccess() throws Exception {
    long mine = createDoc(reader, "{\"title\":\"批量甲\",\"content\":\"a\"}").at("/id").asLong();
    // 只读者可见不可写：越权项按 40302 逐项返回（doc 卡 §8）
    long other = createDoc(admin, "{\"title\":\"批量乙\",\"content\":\"b\",\"acl\":\"private\","
        + "\"readers\":{\"accounts\":[\"" + A + "\"]}}").at("/id").asLong();
    long targetSpace = dataId(send("POST", "/api/v1/doc-spaces",
        "{\"name\":\"批量目标库-" + unique + "\",\"type\":\"custom\"}", admin));

    // reader 只能动自己的文档：admin 的文档项越权 40302，逐项返回
    HttpResponse<String> result = send("POST", "/api/v1/docs/batch",
        "{\"ids\":[" + mine + "," + other + "],\"action\":\"move\",\"params\":{\"docSpaceId\":" + targetSpace + "}}",
        reader);
    assertEquals(200, result.statusCode(), result.body());
    JsonNode results = json.readTree(result.body()).at("/data/results");
    assertTrue(results.at("/0/ok").asBoolean(), result.body());
    assertFalse(results.at("/1/ok").asBoolean(), result.body());
    assertTrue(results.at("/1/error").asText().startsWith("40302:"), result.body());
    assertEquals(targetSpace, doc(mine).at("/docSpaceId").asLong(), "成功项副作用落库");

    // delete 批量：reader 自己的文档删除成功
    HttpResponse<String> deleted = send("POST", "/api/v1/docs/batch",
        "{\"ids\":[" + mine + "],\"action\":\"delete\"}", reader);
    assertEquals(200, deleted.statusCode(), deleted.body());
    assertTrue(json.readTree(deleted.body()).at("/data/results/0/ok").asBoolean(), deleted.body());
    assertEquals(404, send("GET", "/api/v1/docs/" + mine, null, reader).statusCode());
  }

  @Test
  @DisplayName("乐观锁与版本权限：PATCH lockVersion 不符 40901；versions/0 仅可编辑者，只读者 40302")
  void optimisticLockAndDraftPermission() throws Exception {
    long docId = createDoc("{\"title\":\"权限文档\",\"content\":\"正文\"}").at("/id").asLong();
    HttpResponse<String> conflict = send("PATCH", "/api/v1/docs/" + docId,
        "{\"title\":\"改名\",\"lockVersion\":7}", admin);
    assertEquals(409, conflict.statusCode(), conflict.body());
    JsonNode updated = data(send("PATCH", "/api/v1/docs/" + docId,
        "{\"title\":\"改名\",\"keywords\":\"k\",\"lockVersion\":0}", admin));
    assertEquals("改名", updated.at("/title").asText(), updated.toString());
    assertEquals("k", updated.at("/keywords").asText(), updated.toString());

    assertEquals(200, send("GET", "/api/v1/docs/" + docId + "/versions/0", null, admin).statusCode());

    // 只读者（readers 命中）：可读不可写，save-draft / PATCH → 40302；versions/0 对只读者同样 40302
    long shared = createDoc("{\"title\":\"共享文档\",\"content\":\"正文\",\"status\":\"published\",\"acl\":\"private\","
        + "\"readers\":{\"accounts\":[\"" + A + "\"]}}").at("/id").asLong();
    assertEquals(403, action(shared, "save-draft", "{\"content\":\"x\"}", reader).statusCode());
    assertEquals(403, send("PATCH", "/api/v1/docs/" + shared,
        "{\"title\":\"x\",\"lockVersion\":0}", reader).statusCode());
    assertEquals(403, send("GET", "/api/v1/docs/" + shared + "/versions/0", null, reader).statusCode());
    assertEquals(200, send("GET", "/api/v1/docs/" + shared + "/versions/1", null, reader).statusCode());
    assertEquals(200, send("GET", "/api/v1/docs/" + shared + "/versions/0", null, admin).statusCode());
  }

  @Test
  @DisplayName("过滤与搜索：q 命中标题/当前发布正文（非草稿）、未注册过滤字段 40001")
  void filtersAndKeyword() throws Exception {
    long docId = createDoc("{\"title\":\"关键词文档\",\"content\":\"阿尔法正文\",\"status\":\"published\"}")
        .at("/id").asLong();
    data(action(docId, "save-draft", "{\"content\":\"贝塔草稿正文\"}", admin));

    JsonNode byTitle = data(send("GET", "/api/v1/docs?q=关键词文档", null, admin));
    assertEquals(1, byTitle.at("/total").asLong(), byTitle.toString());
    JsonNode bySnapshot = data(send("GET", "/api/v1/docs?q=阿尔法", null, admin));
    assertEquals(1, bySnapshot.at("/total").asLong(), bySnapshot.toString());
    JsonNode byDraft = data(send("GET", "/api/v1/docs?q=贝塔", null, admin));
    assertEquals(0, byDraft.at("/total").asLong(), "草稿正文不参与关键词命中");

    JsonNode byMe = data(send("GET", "/api/v1/docs?filters%5BcreatedBy%5D=@me&filters%5Bstatus%5D=published", null, admin));
    assertTrue(byMe.at("/total").asLong() >= 1, byMe.toString());

    // 全局搜索 scope=doc 接入（platform 卡 §5.2；DataScope 先于 LIKE）
    JsonNode search = data(send("GET", "/api/v1/search?q=关键词文档&scope=doc", null, admin));
    assertEquals(1, search.at("/total").asLong(), search.toString());
    assertEquals("doc", search.at("/items/0/objectType").asText(), search.toString());
    assertEquals(docId, search.at("/items/0/objectId").asLong(), search.toString());
    HttpResponse<String> bad = send("GET", "/api/v1/docs?filters%5Bheadline%5D=x", null, admin);
    assertEquals(400, bad.statusCode(), bad.body());
  }

  @Test
  @DisplayName("digest 缺省：正文去标记后前 200 字")
  void digestStripsMarkers() throws Exception {
    JsonNode created = createDoc("{\"title\":\"摘要\",\"content\":\"## 小节标题\\n\\n**加粗**正文与 [链接](http://x)\\n\"}");
    String digest = created.at("/digest").asText();
    assertFalse(digest.contains("#"), digest);
    assertFalse(digest.contains("**"), digest);
    assertTrue(digest.contains("小节标题"), digest);
    assertTrue(digest.contains("加粗正文"), digest);
    assertTrue(digest.length() <= 200, digest);
  }
}
