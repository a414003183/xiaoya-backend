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
import net.zentao.platform.i18n.LangCatalog;
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
    return send(method, path, body, contentType, cookie);
  }

  private HttpResponse<String> send(String method, String path, String body, String contentType, String session)
      throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("Content-Type", contentType)
        .header("X-Requested-With", "fetch");
    if (session != null) {
      builder.header("Cookie", session);
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

  private HttpResponse<String> upload(String fileName, byte[] workbook) throws Exception {
    return multipartSend("/api/v1/lang-imports", fileName, workbook, cookie);
  }

  /** 手造 multipart（java.net.http 无表单构造口）；T05 起只收 file 一段。 */
  private HttpResponse<String> multipartSend(String path, String fileName, byte[] workbook, String session)
      throws Exception {
    String boundary = "----zentao" + System.nanoTime();
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
        + "\"\r\nContent-Type: " + LangXlsx.CONTENT_TYPE + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    body.write(workbook);
    body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .header("X-Requested-With", "fetch")
        .header("Cookie", session)
        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
        .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  /** 造一份「en 列是公式」的工作簿：公式单元格必须被拒（ADR-005 ⑤）。 */
  private byte[] formulaWorkbook() throws Exception {
    try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("lang");
      Row headerRow = sheet.createRow(0);
      String[] header = header();
      for (int c = 0; c < header.length; c++) {
        headerRow.createCell(c).setCellValue(header[c]);
      }
      Row row = sheet.createRow(1);
      row.createCell(0).setCellValue(KEY);
      row.createCell(1).setCellValue("甲");
      row.createCell(2).setCellFormula("CONCATENATE(\"a\",\"b\")");
      workbook.write(out);
      return out.toByteArray();
    }
  }

  /** 建一个不含任何权限码的账号并登录——用于「登录即可读、写要码」的权限边界断言。 */
  private String plainAccount(String tag) throws Exception {
    String account = tag + "-" + System.nanoTime() % 100000000;
    long roleId = data(send("POST", "/api/v1/roles", "{\"name\":\"组-" + account + "\"}", "application/json"))
        .get("id").asLong();
    assertEquals(200, send("PUT", "/api/v1/roles/" + roleId + "/privileges", "{\"codes\":[]}",
        "application/json").statusCode());
    assertEquals(200, send("POST", "/api/v1/accounts",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\",\"realName\":\"" + account
            + "\",\"roleIds\":[" + roleId + "]}", "application/json").statusCode());
    HttpResponse<String> login = send("POST", "/api/v1/session",
        "{\"account\":\"" + account + "\",\"password\":\"secret123\"}", "application/json", null);
    assertEquals(200, login.statusCode(), login.body());
    return login.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("ZT_SESSION="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
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
    HttpResponse<String> response = upload(fileName,
        workbook(header(), new String[] {KEY, "界面语言", "Interface language"}));
    JsonNode view = data(response);
    assertEquals("success", view.get("status").asText(), response.body());
    assertEquals(1, view.get("totalRows").asInt());
    assertEquals(1, view.get("appliedRows").asInt());
    assertEquals(0, view.get("failedRows").asInt());
    // T05：单文件全语言——记录里的语言码恒 all（列头即语言，不再由表单声明）
    assertEquals("all", view.get("lang").asText());
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
    HttpResponse<String> response = upload(fileName,
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
    HttpResponse<String> response = upload(fileName,
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
  @DisplayName("表头缺失 key 列：文件级原因码 file=invalid-header")
  void missingKeyColumnRejected() throws Exception {
    String fileName = uniqueName("noheader");
    HttpResponse<String> response = upload(fileName,
        workbook(new String[] {"zh-cn", "en"}, new String[] {KEY, "甲"}));
    assertEquals(422, response.statusCode(), response.body());
    JsonNode fields = json.readTree(response.body()).get("error").get("fields");
    assertEquals("invalid-header", fields.get("file").asText(), response.body());
    assertEquals(1, logCount(fileName, "failed"));
  }

  @Test
  @DisplayName("表头多一列（无关文件）：file=invalid-header —— 精确匹配，不认「多出来的列」")
  void extraHeaderColumnRejected() throws Exception {
    HttpResponse<String> response = upload(uniqueName("extra-col"),
        workbook(new String[] {"key", "zh-cn", "en", "note"}, new String[] {KEY, "甲", "a", "备注"}));
    assertEquals(422, response.statusCode(), response.body());
    assertEquals("invalid-header", json.readTree(response.body()).get("error").get("fields").get("file").asText(),
        response.body());
  }

  @Test
  @DisplayName("表头缺 en 列：file=invalid-header（全语言模板不允许少语言）")
  void missingLanguageColumnRejected() throws Exception {
    HttpResponse<String> response = upload(uniqueName("no-en"),
        workbook(new String[] {"key", "zh-cn"}, new String[] {KEY, "甲"}));
    assertEquals(422, response.statusCode(), response.body());
    assertEquals("invalid-header", json.readTree(response.body()).get("error").get("fields").get("file").asText(),
        response.body());
  }

  @Test
  @DisplayName("表头列序错（key 不在首列）：file=invalid-header")
  void reorderedHeaderRejected() throws Exception {
    HttpResponse<String> response = upload(uniqueName("reorder"),
        workbook(new String[] {"zh-cn", "key", "en"}, new String[] {"甲", KEY, "a"}));
    assertEquals(422, response.statusCode(), response.body());
    assertEquals("invalid-header", json.readTree(response.body()).get("error").get("fields").get("file").asText(),
        response.body());
  }

  @Test
  @DisplayName("文件过大（>5MB）：file=too-large —— 体量闸门在解析之前，不拿大文件喂 POI")
  void tooLargeRejected() throws Exception {
    // 带合法魔数（PK）的超大文件：魔数过闸、体量被挡（否则会先去喂 POI）
    byte[] oversized = java.util.Arrays.copyOf(
        workbook(header(), new String[] {KEY, "甲", "a"}), 5 * 1024 * 1024 + 1);
    HttpResponse<String> response = upload(uniqueName("too-large"), oversized);
    assertEquals(422, response.statusCode(), response.body());
    assertEquals("too-large", json.readTree(response.body()).get("error").get("fields").get("file").asText(),
        response.body());
  }

  @Test
  @DisplayName("T60 解压比闸门：压缩包能解出 64MB+ → file=too-large（zip 炸弹不进 POI）")
  void zipBombRejected() throws Exception {
    byte[] bomb = zipBomb();
    assertTrue(bomb.length < LangImportService.MAX_FILE_BYTES,
        "炸弹本身在 5MB 内（体量闸门拦不住它，得靠解压比）: " + bomb.length);
    HttpResponse<String> response = upload(uniqueName("bomb"), bomb);
    assertEquals(422, response.statusCode(), response.body());
    assertEquals("too-large", json.readTree(response.body()).get("error").get("fields").get("file").asText(),
        response.body());
  }

  /** 能过魔数与体量闸门、但解压后超过 {@link LangXlsx#MAX_EXPANDED_BYTES} 的压缩包（0 填充极能压）。 */
  private static byte[] zipBomb() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
      zip.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"));
      byte[] block = new byte[1024 * 1024];
      for (int i = 0; i < 80; i++) {
        zip.write(block);
      }
      zip.closeEntry();
    }
    return out.toByteArray();
  }

  @Test
  @DisplayName("伪造魔数（不是 zip/OLE2）：file=unreadable —— 改了名的无关文件在解析前被挡")
  void badMagicRejected() throws Exception {
    HttpResponse<String> response = upload(uniqueName("bad-magic"),
        "这不是 Excel，只是改了扩展名".getBytes(StandardCharsets.UTF_8));
    assertEquals(422, response.statusCode(), response.body());
    assertEquals("unreadable", json.readTree(response.body()).get("error").get("fields").get("file").asText(),
        response.body());
  }

  @Test
  @DisplayName("扩展名不是 Excel：file=invalid-extension（伪造文件在这一步被挡）")
  void badExtensionRejected() throws Exception {
    HttpResponse<String> response = upload("lang-not-excel.txt",
        workbook(header(), new String[] {KEY, "甲", "a"}));
    assertEquals(422, response.statusCode(), response.body());
    assertEquals("invalid-extension", json.readTree(response.body()).get("error").get("fields").get("file").asText(),
        response.body());
  }

  @Test
  @DisplayName("公式单元格：行级 formula-cell（公式不进覆盖层，防注入）")
  void formulaCellRejected() throws Exception {
    String fileName = uniqueName("formula");
    // 用例间共享库：只断言「该行未被写入」（round-trip 用例会写全量覆盖），不断言全局无覆盖
    String before = overrideValue("zh-cn", KEY);
    HttpResponse<String> response = upload(fileName, formulaWorkbook());
    assertEquals(422, response.statusCode(), response.body());
    assertEquals("formula-cell", json.readTree(response.body()).get("error").get("fields").get("row:2").asText(),
        response.body());
    assertEquals(before, overrideValue("zh-cn", KEY), "整份不落库：公式行让整个文件被拒");
  }

  @Test
  @DisplayName("值含孤立代理项（非法 Unicode）→ 行级 invalid-unicode；emoji 这类合法代理对放行")
  void invalidUnicodeRejected() throws Exception {
    String fileName = uniqueName("surrogate");
    String before = overrideValue("zh-cn", KEY);
    HttpResponse<String> response = upload(fileName, xlsWorkbookWithSurrogate());
    assertEquals(422, response.statusCode(), response.body());
    assertEquals("invalid-unicode", json.readTree(response.body()).get("error").get("fields").get("row:2").asText(),
        response.body());
    assertEquals(before, overrideValue("zh-cn", KEY), "整份不落库");
  }

  @Test
  @DisplayName("合法代理对（emoji）不算非法 Unicode：正常落覆盖")
  void emojiValueAccepted() throws Exception {
    HttpResponse<String> response = upload(uniqueName("emoji"),
        workbook(header(), new String[] {KEY, "界面语言 😀", "Interface 😀"}));
    JsonNode view = data(response);
    assertEquals("success", view.get("status").asText(), response.body());
    assertEquals("界面语言 😀", overrideValue("zh-cn", KEY));
  }

  /**
   * 造一份 .xls（BIFF8）且值含**孤立高代理项**的工作簿。
   * 为什么用 .xls：.xlsx 走 XML，XML 1.0 本就不允许孤立代理项（文件根本读不进来），
   * 只有二进制格式能把这种值送到服务层——§7「拒绝非法 Unicode」这条闸门真正挡的就是它。
   */
  private byte[] xlsWorkbookWithSurrogate() throws Exception {
    try (org.apache.poi.hssf.usermodel.HSSFWorkbook workbook = new org.apache.poi.hssf.usermodel.HSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("lang");
      Row headerRow = sheet.createRow(0);
      String[] header = header();
      for (int c = 0; c < header.length; c++) {
        headerRow.createCell(c).setCellValue(header[c]);
      }
      Row row = sheet.createRow(1);
      row.createCell(0).setCellValue(KEY);
      row.createCell(1).setCellValue("甲\uD800乙");
      row.createCell(2).setCellValue("value");
      workbook.write(out);
      return out.toByteArray();
    }
  }

  @Test
  @DisplayName("登录即可读覆盖层（运行时合并数据源）、但上传要 lang-manage —— 覆盖层对普通用户生效")
  void overridesReadableWithoutLangManage() throws Exception {
    String plain = plainAccount("t05-plain");
    assertEquals(200, send("GET", "/api/v1/lang-items/overrides?lang=zh-cn", null, "application/json", plain)
        .statusCode(), "覆盖层是全站运行时数据源，普通用户必须能读（否则上传的文案对他们不生效）");
    HttpResponse<String> upload = multipartSend("/api/v1/lang-imports", "lang-noperm.xlsx",
        workbook(header(), new String[] {KEY, "甲", "a"}), plain);
    assertEquals(403, upload.statusCode(), "上传仍要 lang-manage：" + upload.body());
  }

  @Test
  @DisplayName("en 列出现中文：row 级 cjk-in-en")
  void cjkInEnRejected() throws Exception {
    String fileName = uniqueName("cjk");
    // 用例间共享库：只断言「该行未被写入」，不断言全局无覆盖（round-trip 用例会写全量覆盖）
    String before = overrideValue("zh-cn", KEY);
    HttpResponse<String> response = upload(fileName,
        workbook(header(), new String[] {KEY, "界面语言", "界面语言"}));
    assertEquals(422, response.statusCode(), response.body());
    assertEquals("cjk-in-en",
        json.readTree(response.body()).get("error").get("fields").get("row:2").asText(), response.body());
    assertEquals(before, overrideValue("zh-cn", KEY));
  }

  @Test
  @DisplayName("重复上传同一个键只更新那一行（覆盖层 upsert 不增行；原 LangItemMergeTest 的这条断言）")
  void repeatedUploadUpdatesSameRow() throws Exception {
    assertEquals(200, upload(uniqueName("once"),
        workbook(header(), new String[] {KEY, "第一次", null})).statusCode());
    assertEquals("第一次", overrideValue("zh-cn", KEY));
    assertEquals(200, upload(uniqueName("twice"),
        workbook(header(), new String[] {KEY, "第二次", null})).statusCode());
    assertEquals("第二次", overrideValue("zh-cn", KEY));
    LangCatalog.KeyParts parts = LangCatalog.parts(KEY);
    assertEquals(1, jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM lang_item WHERE lang = 'zh-cn' AND domain = ? AND section = ? AND item_key = ?",
        Integer.class, parts.domain(), parts.section(), parts.itemKey()), "同键重复上传不应增行");
  }

  @Test
  @DisplayName("T21：整行留空 = 清除该键的覆盖（恢复语言包默认），对两种语言一起生效")
  void emptyRowClearsOverride() throws Exception {
    // 先写一条覆盖（两语言），再用整行留空把它清掉
    assertEquals(200, upload(uniqueName("seed"),
        workbook(header(), new String[] {KEY, "改过的中文", "changed en"})).statusCode());
    assertEquals("改过的中文", overrideValue("zh-cn", KEY));
    assertEquals("changed en", overrideValue("en", KEY));

    String fileName = uniqueName("clear");
    HttpResponse<String> response = upload(fileName, workbook(header(), new String[] {KEY, null, null}));
    assertEquals(200, response.statusCode(), response.body());
    assertNull(overrideValue("zh-cn", KEY), "整行留空应清掉 zh-cn 的覆盖");
    assertNull(overrideValue("en", KEY), "清理行对所有语言生效");
  }

  @Test
  @DisplayName("整体拒绝：同一文件里一行合法一行非法时不落任何覆盖（全通过才写）")
  void partialFailureAppliesNothing() throws Exception {
    String fileName = uniqueName("atomic");
    String before = overrideValue("zh-cn", KEY_2);
    HttpResponse<String> response = upload(fileName,
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
      // T05：模板列宽加大（key 50 / 语言列 80，单位 1/256 字符）+ 冻结首行——长文案要在单元格里看得见
      assertEquals(50 * 256, sheet.getColumnWidth(0));
      assertEquals(80 * 256, sheet.getColumnWidth(1));
      assertEquals(80 * 256, sheet.getColumnWidth(2));
      assertTrue(sheet.getPaneInformation().isFreezePane(), "模板应冻结首行表头");
      // POI 命名与直觉相反：VerticalSplitPosition = xSplit（冻结列数），HorizontalSplitPosition = ySplit（冻结行数）
      assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());
      assertEquals(0, sheet.getPaneInformation().getVerticalSplitPosition());
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
    HttpResponse<String> reimport = upload(uniqueName("roundtrip"), exported.body());
    JsonNode view = data(reimport);
    assertEquals("success", view.get("status").asText(), reimport.body());
    assertEquals(catalog.keys().size(), view.get("totalRows").asInt());
    assertEquals(catalog.keys().size(), view.get("appliedRows").asInt());
    assertEquals(0, view.get("failedRows").asInt());
  }

  @Test
  @DisplayName("覆盖层全量：只回该语言的行，key 为全点分键（lang_item 拆列还原）")
  void overridesDumpUsesFullDottedKeys() throws Exception {
    // 写覆盖的唯一入口是 Excel 上传（在线编辑器已随 T21 删除）
    assertEquals(200, upload(uniqueName("dump"),
        workbook(header(), new String[] {KEY, "界面语言（覆盖）", null})).statusCode());

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
    upload("lang-list-ok-" + tag + ".xlsx", workbook(header(), new String[] {KEY, "界面语言", "Label"}));
    upload("lang-list-bad-" + tag + ".xlsx", workbook(header(), new String[] {"nope.nope", "x", "y"}));

    JsonNode all = data(send("GET", "/api/v1/lang-imports?q=" + tag + "&sort=-createdAt", null, "application/json"));
    assertEquals(2, all.get("total").asInt(), all.toString());

    JsonNode failed = data(send("GET", "/api/v1/lang-imports?q=" + tag + "&filters%5Bstatus%5D=failed", null,
        "application/json"));
    assertEquals(1, failed.get("total").asInt(), failed.toString());
    assertEquals("failed", failed.get("items").get(0).get("status").asText());

    JsonNode english = data(send("GET", "/api/v1/lang-imports?q=" + tag + "&filters%5Blang%5D=all", null,
        "application/json"));
    assertEquals(2, english.get("total").asInt(), english.toString());
    // T05 起新行不再落旧语言码（历史行 lang=zh-cn/en 仍按原值可筛——本库无历史行，故只断言新行为）
    assertEquals(0, data(send("GET", "/api/v1/lang-imports?q=" + tag + "&filters%5Blang%5D=en", null,
        "application/json")).get("total").asInt());

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
