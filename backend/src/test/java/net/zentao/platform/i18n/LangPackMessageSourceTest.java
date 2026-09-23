package net.zentao.platform.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;

/**
 * 后端文案的「上传即生效」（T21）：**语言包 + 覆盖层**两级解析，与前端同源。
 *
 * <p>关键断言：同一个请求，在上传一份把 `error.notFound` 改掉的 Excel 前后，返回的 message 不同——
 * 这就是「无论前端还是后端都遵守同一份多语言，Excel 一上传就生效」的后端侧证据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LangPackMessageSourceTest {

  /** 后端消息键（T21 起与前端文案同在语言包内，故可被 Excel 覆盖）。 */
  private static final String KEY = "error.notFound";
  private static final String ENTITY_KEY = "entity.account";

  @LocalServerPort
  int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private String cookie;

  @BeforeEach
  void login() throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/session",
        "{\"account\":\"admin\",\"password\":\"admin123\"}", null);
    assertEquals(200, response.statusCode(), response.body());
    cookie = response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  @Test
  @DisplayName("缺省：后端消息来自语言包（entity 名词随语言解析，参数是键就跟着翻译）")
  void defaultsComeFromLanguagePacks() throws Exception {
    JsonNode zh = sendJson("GET", "/api/v1/accounts/999999999", null);
    assertEquals(40401, zh.at("/error/code").asInt(), zh.toString());
    assertEquals("账号不存在。", zh.at("/error/message").asText(), zh.toString());

    HttpResponse<String> en = send("GET", "/api/v1/accounts/999999999?lang=en", null, cookie);
    JsonNode enBody = json.readTree(en.body());
    assertEquals("Account not found.", enBody.at("/error/message").asText(), enBody.toString());
  }

  @Test
  @DisplayName("上传 Excel 覆盖 error.notFound → 同一请求的 message 立即变；覆盖值参与 MessageFormat 参数")
  void uploadOverridesBackendMessagesImmediately() throws Exception {
    assertEquals("账号不存在。", sendJson("GET", "/api/v1/accounts/999999999", null).at("/error/message").asText());

    byte[] workbook = workbook(List.<String[]>of(new String[] {KEY, "【改】{0}没了", "【EN】{0} is gone"}));
    HttpResponse<String> imported = sendMultipart("/api/v1/lang-imports", workbook, "zh-cn");
    assertEquals(200, imported.statusCode(), imported.body());

    // 覆盖层生效：先是「上传语言」那一语言（zh-cn），{0} 照 MessageFormat 用实体名词填
    assertEquals("【改】账号没了", sendJson("GET", "/api/v1/accounts/999999999", null)
        .at("/error/message").asText());
    // 另一语言不受影响（Excel 里 en 列单独给值）
    HttpResponse<String> en = send("GET", "/api/v1/accounts/999999999?lang=en", null, cookie);
    assertEquals("【EN】Account is gone", json.readTree(en.body()).at("/error/message").asText(), en.body());

    // 覆盖 entity.account（参数里的键）同样即时生效
    assertEquals(200, sendMultipart("/api/v1/lang-imports", workbook(List.<String[]>of(new String[] {ENTITY_KEY, "通行证", "Passport"})),
        "zh-cn").statusCode());
    assertTrue(sendJson("GET", "/api/v1/accounts/999999999", null).at("/error/message").asText().contains("通行证"),
        "实体的键也要走覆盖层");
  }

  private JsonNode sendJson(String method, String path, String body) throws Exception {
    HttpResponse<String> response = send(method, path, body, cookie);
    assertEquals(404, response.statusCode(), response.body());
    return json.readTree(response.body());
  }

  /** 单行工作簿（表头 key | zh-cn | en，一行一个键——与 LangXlsx 的格式口径一致）。 */
  private static byte[] workbook(List<String[]> rows) throws Exception {
    try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = book.createSheet("lang");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("key");
      header.createCell(1).setCellValue("zh-cn");
      header.createCell(2).setCellValue("en");
      int index = 1;
      for (String[] row : rows) {
        Row line = sheet.createRow(index++);
        for (int column = 0; column < row.length; column++) {
          line.createCell(column).setCellValue(row[column]);
        }
      }
      book.write(out);
      return out.toByteArray();
    }
  }

  private HttpResponse<String> send(String method, String path, String body, String sessionCookie) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Content-Type", "application/json");
    if (sessionCookie != null) {
      builder.header("Cookie", sessionCookie);
    }
    builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  /** multipart 上传（file + lang），字段名与服务端 @RequestParam 对齐。 */
  private HttpResponse<String> sendMultipart(String path, byte[] content, String lang) throws Exception {
    String boundary = "----T21Boundary";
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    body.write(("Content-Disposition: form-data; name=\"lang\"\r\n\r\n" + lang + "\r\n").getBytes(StandardCharsets.UTF_8));
    body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    body.write(("Content-Disposition: form-data; name=\"file\"; filename=\"lang.xlsx\"\r\n")
        .getBytes(StandardCharsets.UTF_8));
    body.write(("Content-Type: " + MediaType.APPLICATION_OCTET_STREAM_VALUE + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    body.write(content);
    body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Requested-With", "fetch")
        .header("Cookie", cookie)
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
        .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
