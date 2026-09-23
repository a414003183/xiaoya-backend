package net.zentao.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.zentao.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-12 project 数据权限 IT（project 卡 §7/§8，Testcontainers MySQL 8.4 真库）：
 * private 项目外人列表 0 条/详情 40302；acl=program 继承项目集可见性；白名单账号可见；
 * board acl=extend 继承空间；超管全见。
 */
class ProjectDataScopeIT extends MySqlContainerSupport {

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  @org.springframework.beans.factory.annotation.Value("${local.server.port}")
  int port;

  private String adminCookie;
  private long viewerGroupId;

  @BeforeEach
  void login() throws Exception {
    adminCookie = loginAs("admin", "admin123");
  }

  private String loginAs(String account, String password) throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/session",
        "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}", null);
    assertEquals(200, response.statusCode(), response.body());
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private HttpResponse<String> send(String method, String path, String body, String cookie) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Content-Type", "application/json");
    if (cookie != null) {
      builder.header("Cookie", cookie);
    }
    builder.method(method,
        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private long dataId(HttpResponse<String> response) throws Exception {
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).at("/data/id").asLong();
  }

  private void ensureAccount(String account) throws Exception {
    if (viewerGroupId == 0) {
      viewerGroupId = dataId(send("POST", "/api/v1/roles",
          "{\"name\":\"IT 项目组 " + System.nanoTime() + "\"}", adminCookie));
      send("PUT", "/api/v1/roles/" + viewerGroupId + "/privileges",
          "{\"codes\":[\"project-view\",\"program-view\",\"execution-view\",\"task-view\",\"board-view\","
              + "\"task-start\",\"task-finish\"]}",
          adminCookie);
    }
    HttpResponse<String> created = send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"" + account
            + "\",\"roleIds\":[" + viewerGroupId + "]}",
        adminCookie);
    assertTrue(created.statusCode() == 200 || created.statusCode() == 422, created.body());
  }

  private long createProduct() throws Exception {
    return dataId(send("POST", "/api/v1/products",
        "{\"name\":\"IT 产品 " + System.nanoTime() + "\",\"acl\":\"public\"}", adminCookie));
  }

  private long createProject(long productId, String acl, Long programId, String whitelistJson) throws Exception {
    String body = "{\"name\":\"IT 项目 " + System.nanoTime() + "\",\"beginDate\":\"2026-09-01\","
        + "\"endDate\":\"2026-12-31\",\"productIds\":[" + productId + "],\"acl\":\"" + acl + "\""
        + (programId == null ? "" : ",\"parentId\":" + programId)
        + (whitelistJson == null ? "" : ",\"whitelist\":" + whitelistJson) + "}";
    return dataId(send("POST", "/api/v1/projects", body, adminCookie));
  }

  @Test
  @DisplayName("private 项目/项目集外人列表 0 条与详情 40302、白名单可见、acl=program 继承、超管全见")
  void projectVisibility() throws Exception {
    ensureAccount("it-proj-wl");
    ensureAccount("it-proj-guest");
    long product = createProduct();
    long program = dataId(send("POST", "/api/v1/programs",
        "{\"name\":\"IT 私有项目集 " + System.nanoTime() + "\",\"acl\":\"private\",\"whitelist\":[\"it-proj-wl\"]}",
        adminCookie));
    long inherited = createProject(product, "program", program, null);
    long privateProject = createProject(product, "private", null, "[\"it-proj-wl\"]");

    String viewerCookie = loginAs("it-proj-wl", "secret123");
    String guestCookie = loginAs("it-proj-guest", "secret123");

    JsonNode viewerList = json.readTree(send("GET",
        "/api/v1/projects?filters%5Bid%5D=" + inherited + "," + privateProject, null, viewerCookie).body())
        .at("/data");
    assertEquals(2, viewerList.at("/items").size(), viewerList.toString());
    assertEquals(200, send("GET", "/api/v1/projects/" + inherited, null, viewerCookie).statusCode());

    JsonNode guestList = json.readTree(send("GET",
        "/api/v1/projects?filters%5Bid%5D=" + inherited + "," + privateProject, null, guestCookie).body())
        .at("/data");
    assertEquals(0, guestList.at("/items").size(), guestList.toString());
    HttpResponse<String> guestDetail = send("GET", "/api/v1/projects/" + privateProject, null, guestCookie);
    assertEquals(403, guestDetail.statusCode(), guestDetail.body());
    assertTrue(guestDetail.body().contains("40302"), guestDetail.body());
    HttpResponse<String> guestProgram = send("GET", "/api/v1/programs/" + program, null, guestCookie);
    assertEquals(403, guestProgram.statusCode(), guestProgram.body());

    JsonNode adminProjects = json.readTree(send("GET", "/api/v1/projects", null, adminCookie).body()).at("/data");
    assertTrue(adminProjects.at("/total").asLong() >= 2, adminProjects.toString());
  }

  @Test
  @DisplayName("board acl=extend 继承空间可见性：空间白名单可见整板，外人 40302")
  void boardExtendInheritsSpace() throws Exception {
    ensureAccount("it-board-wl");
    ensureAccount("it-board-guest");
    long space = dataId(send("POST", "/api/v1/board-spaces",
        "{\"name\":\"IT 私有空间 " + System.nanoTime() + "\",\"acl\":\"private\",\"whitelist\":[\"it-board-wl\"]}",
        adminCookie));
    long board = dataId(send("POST", "/api/v1/board-spaces/" + space + "/boards",
        "{\"name\":\"IT 继承看板 " + System.nanoTime() + "\",\"acl\":\"extend\"}", adminCookie));
    send("POST", "/api/v1/boards/" + board + "/lanes", "{\"name\":\"待办\"}", adminCookie);

    String viewerCookie = loginAs("it-board-wl", "secret123");
    String guestCookie = loginAs("it-board-guest", "secret123");

    HttpResponse<String> viewerBoard = send("GET", "/api/v1/boards/" + board, null, viewerCookie);
    assertEquals(200, viewerBoard.statusCode(), viewerBoard.body());
    assertTrue(json.readTree(viewerBoard.body()).at("/data/lanes").size() >= 1, viewerBoard.body());

    HttpResponse<String> guestBoard = send("GET", "/api/v1/boards/" + board, null, guestCookie);
    assertEquals(403, guestBoard.statusCode(), guestBoard.body());
    assertTrue(guestBoard.body().contains("40302"), guestBoard.body());
  }
}
