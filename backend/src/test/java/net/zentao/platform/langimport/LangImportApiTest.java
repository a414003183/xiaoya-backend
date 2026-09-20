package net.zentao.platform.langimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 多语言上传 API（platform 卡 §3.12）：Excel 校验规则 / 全通过才落覆盖 / 失败留痕 / 导出回传（round-trip）/ 记录列表 DSL。
 * 用例内用 POI 现造工作簿（与 LangXlsx 同一格式口径），断言打到 HTTP + DB 两侧。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LangImportApiTest {

  /** 目录里真实存在的键（两语言键位一致），键映射断言按它做。 */
  private static final String KEY = "nav.language.label";
  private static final String KEY_2 = "common.action.submit";

  @Value("${local.server.port}")
  int port;

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Autowired
  LangCatalog catalog;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private final DataFormatter formatter = new DataFormatter();
  private String cookie;

  @BeforeEach
  void login() throws Exception {
    HttpResponse<String> response = send("POST", "/api/v1/session",
        "{\"account\":\"admin\",\"password\":\"admin123\"}", "application/json");
    assertEquals(200, response.statusCode(), response.body());
    cookie = response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private HttpResponse<String> send(String method, String path, String body, String contentType) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("Content-Type", contentType)
        .header("X-Requested-With", "fetch");
    if (cookie != null) {
      builder.header("Cookie", cookie);
    }
    return http.send(builder.method(method,
        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode data(HttpResponse<String> response) throws Exception {
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).get("data");
  }

  // ── 上传（手造 multipart：java.net.http 无表单构造口） ──

  private HttpResponse<String> upload(String fileName, String lang, byte[] workbook) throws Exception {
    String boundary = "----zentao" + System.nanoTime();
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
        + "\"\r\nContent-Type: " + LangXlsx.CONTENT_TYPE + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    body.write(workbook);
    body.write(("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"lang\"\r\n\r\n" + lang
        + "\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/lang-imports"))
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .header("X-Requested-With", "fetch")
        .header("Cookie", cookie)
        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
        .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  /** 造工作簿：header 为 null 时表头只有语言列（用于「缺 key 列」用例）。 */
  private byte[] workbook(String[] header, String[]... rows) throws Exception {
    try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("lang");
      if (header != null) {
        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < header.length; c++) {
          headerRow.createCell(c).setCellValue(header[c]);
        }
      }
      for (int r = 0; r < rows.length; r++) {
        Row row = sheet.createRow(r + 1);
        for (int c = 0; c < rows[r].length; c++) {
          if (rows[r][c] != null) {
            row.createCell(c).setCellValue(rows[r][c]);
          }
        }
      }
      workbook.write(out);
      return out.toByteArray();
    }
  }

  private static String[] header() {
    return new String[] {"key", "zh-cn", "en"};
  }

  private long logCount(String fileName, String status) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM lang_import WHERE file_name = ? AND status = ?", Long.class, fileName, status);
  }

  private String overrideValue(String lang, String fullKey) {
    LangCatalog.KeyParts parts = LangCatalog.parts(fullKey);
    List<String> values = jdbcTemplate.queryForList(
        "SELECT item_value FROM lang_item WHERE lang = ? AND domain = ? AND section = ? AND item_key = ?",
        String.class, lang, parts.domain(), parts.section(), parts.itemKey());
    return values.isEmpty() ? null : values.getFirst();
  }

  private String uniqueName(String tag) {
    return "lang-" + tag + "-" + System.nanoTime() % 100000000 + ".xlsx";
  }

  // ── 用例 ──

  @Test
  @DisplayName("合法文件：落覆盖（键拆回 domain/section/item_key）、记一条 success 记录")
  void validFileAppliesAndLogs() throws Exception {
    String fileName = uniqueName("ok");
    HttpResponse<String> response = upload(fileName, "zh-cn",
        workbook(header(), new String[] {KEY, "界面语言", "Interface language"}));
    JsonNode view = data(response);
    assertEquals("success", view.get("status").asText(), response.body());
    assertEquals(1, view.get("totalRows").asInt());
    assertEquals(1, view.get("appliedRows").asInt());
    assertEquals(0, view.get("failedRows").asInt());
    assertEquals("zh-cn", view.get("lang").asText());
    assertEquals("admin", view.get("createdBy").asText());
    assertTrue(view.get("message").isNull(), response.body());

    assertEquals("界面语言", overrideValue("zh-cn", KEY));
    assertEquals("Interface language", overrideValue("en", KEY));
    assertEquals(1, logCount(fileName, "success"));
  }

  @Test
  @DisplayName("未知键：422 带行号原因码（row:2），且失败也记一条 failed 记录")
  void unknownKeyRejectedAndLogged() throws Exception {
    String fileName = uniqueName("unknown");
    HttpResponse<String> response = upload(fileName, "zh-cn",
        workbook(header(), new String[] {"no.such.key", "x", "y"}));
    assertEquals(422, response.statusCode(), response.body());
    JsonNode error = json.readTree(response.body()).get("error");
    assertEquals(42201, error.get("code").asInt());
    assertEquals("unknown-key", error.get("fields").get("row:2").asText());
    assertEquals(1, logCount(fileName, "failed"));
    assertNull(overrideValue("zh-cn", "no.such.key"));
  }

  @Test
  @DisplayName("同一文件重复键：第二行报 duplicate-key（行号对得上）")
  void duplicateKeyRejected() throws Exception {
    String fileName = uniqueName("dup");
    HttpResponse<String> response = upload(fileName, "zh-cn",
        workbook(header(), new String[] {KEY, "甲", "a"}, new String[] {KEY, "乙", "b"}));
    assertEquals(422, response.statusCode(), response.body());
    JsonNode fields = json.readTree(response.body()).get("error").get("fields");
    assertEquals("duplicate-key", fields.get("row:3").asText(), response.body());
    assertEquals(1, jdbcTemplate.queryForObject(
        "SELECT failed_rows FROM lang_import WHERE file_name = ?", Integer.class, fileName));
    assertEquals(0, jdbcTemplate.queryForObject(
        "SELECT applied_rows FROM lang_import WHERE file_name = ?", Integer.class, fileName), "整体拒绝：一行不合法即不落任何覆盖");
  }

  @Test
  @DisplayName("表头缺 key 列：文件级原因码 file=missing-key-column")
  void missingKeyColumnRejected() throws Exception {
    String fileName = uniqueName("noheader");
    HttpResponse<String> response = upload(fileName, "zh-cn",
        workbook(new String[] {"zh-cn", "en"}, new String[] {KEY, "甲"}));
    assertEquals(422, response.statusCode(), response.body());
    JsonNode fields = json.readTree(response.body()).get("error").get("fields");
    assertEquals("missing-key-column", fields.get("file").asText(), response.body());
    assertEquals(1, logCount(fileName, "failed"));
  }

  @Test
  @DisplayName("en 列出现中文：row 级 cjk-in-en")
  void cjkInEnRejected() throws Exception {
    String fileName = uniqueName("cjk");
    // 用例间共享库：只断言「该行未被写入」，不断言全局无覆盖（round-trip 用例会写全量覆盖）
    String before = overrideValue("zh-cn", KEY);
    HttpResponse<String> response = upload(fileName, "zh-cn",
        workbook(header(), new String[] {KEY, "界面语言", "界面语言"}));
    assertEquals(422, response.statusCode(), response.body());
    assertEquals("cjk-in-en",
        json.readTree(response.body()).get("error").get("fields").get("row:2").asText(), response.body());
    assertEquals(before, overrideValue("zh-cn", KEY));
  }

  @Test
  @DisplayName("有键但语言列全空：row 级 empty-row")
  void emptyRowRejected() throws Exception {
    String fileName = uniqueName("empty");
    HttpResponse<String> response = upload(fileName, "zh-cn", workbook(header(), new String[] {KEY, null, null}));
    assertEquals(422, response.statusCode(), response.body());
    assertEquals("empty-row",
        json.readTree(response.body()).get("error").get("fields").get("row:2").asText(), response.body());
  }

  @Test
  @DisplayName("整体拒绝：同一文件里一行合法一行非法时不落任何覆盖（全通过才写）")
  void partialFailureAppliesNothing() throws Exception {
    String fileName = uniqueName("atomic");
    String before = overrideValue("zh-cn", KEY_2);
    HttpResponse<String> response = upload(fileName, "zh-cn",
        workbook(header(), new String[] {KEY_2, "提交按钮", "Submit"}, new String[] {"bad key!", "x", "y"}));
    assertEquals(422, response.statusCode(), response.body());
    JsonNode fields = json.readTree(response.body()).get("error").get("fields");
    assertEquals("invalid-key", fields.get("row:3").asText(), response.body());
    assertEquals(before, overrideValue("zh-cn", KEY_2));
  }

  @Test
  @DisplayName("导出 → 原样回传导入：表头 key|zh-cn|en 被接受，全量行扫描通过")
  void exportRoundTripsThroughImport() throws Exception {
    HttpResponse<byte[]> exported = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/lang-items/export"))
            .header("Cookie", cookie)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofByteArray());
    assertEquals(200, exported.statusCode());
    assertTrue(exported.headers().firstValue("Content-Type").orElse("").startsWith(LangXlsx.CONTENT_TYPE),
        exported.headers().firstValue("Content-Type").orElse(""));
    assertTrue(exported.headers().firstValue("Content-Disposition").orElse("").contains("attachment"));

    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(exported.body()))) {
      Sheet sheet = workbook.getSheetAt(0);
      Row header = sheet.getRow(0);
      assertEquals("key", formatter.formatCellValue(header.getCell(0)));
      assertEquals("zh-cn", formatter.formatCellValue(header.getCell(1)));
      assertEquals("en", formatter.formatCellValue(header.getCell(2)));
      // 全量目录键都在：导出行数 == 目录键数，且每行键与语言值非空
      assertEquals(catalog.keys().size(), sheet.getLastRowNum());
      List<String> keys = new ArrayList<>();
      for (int r = 1; r <= sheet.getLastRowNum(); r++) {
        keys.add(formatter.formatCellValue(sheet.getRow(r).getCell(0)));
      }
      assertTrue(keys.contains(KEY), "导出应含目录键 " + KEY);
      assertTrue(catalog.keys().containsAll(keys), "导出的键必须都在目录内");
    }

    // 原样回传：不带任何修改的导出文件必须整体通过校验（round-trip 的第一半）
    HttpResponse<String> reimport = upload(uniqueName("roundtrip"), "zh-cn", exported.body());
    JsonNode view = data(reimport);
    assertEquals("success", view.get("status").asText(), reimport.body());
    assertEquals(catalog.keys().size(), view.get("totalRows").asInt());
    assertEquals(catalog.keys().size(), view.get("appliedRows").asInt());
    assertEquals(0, view.get("failedRows").asInt());
  }

  @Test
  @DisplayName("覆盖层全量：只回该语言的行，key 为全点分键（lang_item 拆列还原）")
  void overridesDumpUsesFullDottedKeys() throws Exception {
    assertEquals(200, send("PUT", "/api/v1/lang-items/nav/language?lang=zh-cn",
        "{\"items\":{\"label\":\"界面语言（覆盖）\"}}", "application/json").statusCode());

    JsonNode items = data(send("GET", "/api/v1/lang-items/overrides?lang=zh-cn", null, "application/json"))
        .get("items");
    JsonNode row = null;
    for (JsonNode item : items) {
      if ("nav.language.label".equals(item.get("key").asText())) {
        row = item;
      }
    }
    assertNotNull(row, "覆盖行应还原成全点分键 nav.language.label");
    assertEquals("zh-cn", row.get("lang").asText());
    assertEquals("nav", row.get("domain").asText());
    assertEquals("language", row.get("section").asText());
    assertEquals("界面语言（覆盖）", row.get("value").asText());
  }

  @Test
  @DisplayName("上传记录列表：q 命中文件名、filters[lang]/filters[status] 过滤、page/limit 分页")
  void listImportsFiltersAndPages() throws Exception {
    String tag = String.valueOf(System.nanoTime() % 100000000);
    upload("lang-list-ok-" + tag + ".xlsx", "en", workbook(header(), new String[] {KEY, "界面语言", "Label"}));
    upload("lang-list-bad-" + tag + ".xlsx", "zh-cn", workbook(header(), new String[] {"nope.nope", "x", "y"}));

    JsonNode all = data(send("GET", "/api/v1/lang-imports?q=" + tag + "&sort=-createdAt", null, "application/json"));
    assertEquals(2, all.get("total").asInt(), all.toString());

    JsonNode failed = data(send("GET", "/api/v1/lang-imports?q=" + tag + "&filters%5Bstatus%5D=failed", null,
        "application/json"));
    assertEquals(1, failed.get("total").asInt(), failed.toString());
    assertEquals("failed", failed.get("items").get(0).get("status").asText());

    JsonNode english = data(send("GET", "/api/v1/lang-imports?q=" + tag + "&filters%5Blang%5D=en", null,
        "application/json"));
    assertEquals(1, english.get("total").asInt(), english.toString());
    assertEquals("lang-list-ok-" + tag + ".xlsx", english.get("items").get(0).get("fileName").asText());

    JsonNode firstPage = data(send("GET", "/api/v1/lang-imports?q=" + tag + "&limit=1&page=2", null,
        "application/json"));
    assertEquals(2, firstPage.get("total").asInt());
    assertEquals(1, firstPage.get("items").size());

    // 未注册的过滤字段 → 40001（白名单外的注入防线）；方括号按前端 URLSearchParams 口径编码
    HttpResponse<String> rejected = send("GET", "/api/v1/lang-imports?filters%5BobjectId%5D=1", null,
        "application/json");
    assertEquals(400, rejected.statusCode(), rejected.body());
    assertTrue(rejected.body().contains("40001"), rejected.body());
  }
}
