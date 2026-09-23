package net.zentao.platform.langimport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 语言包 Excel 读写（platform 卡 §3.12；T05 单文件全语言）——导入与导出共用同一列格式，故导出文件可原样回传导入：
 * 首行表头 {@code key | zh-cn | en}（键列名大小写不敏感，语言列名 = 语言码），一行一个全点分键；
 * 读取用 {@link DataFormatter}（单元格按显示文本取，数字/日期不会被强转成怪值），并**记录公式类型单元格**
 * （缓存值不算数，见 {@link LangRow#hasFormula()}）；导出侧带模板列宽与冻结首行。
 */
public final class LangXlsx {

  /** 键列名（大小写不敏感比对）。 */
  public static final String KEY_COLUMN = "key";
  public static final String CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  /** 模板列宽（Excel 单位 = 1/256 字符宽）：键列 50、语言列 80（ADR-005 ④，长文案要看得见）。 */
  private static final int KEY_COLUMN_WIDTH = 50;
  private static final int LANGUAGE_COLUMN_WIDTH = 80;
  private static final String SHEET_NAME = "lang";

  /**
   * 解压后总量上限（T60 / SEC-09）：5MB 的 xlsx 正常解压后是 1~2MB 量级，64MB 留了两个数量级余量，
   * 同时把「一次请求的 inflate 上界」钉死——zip 炸弹在到达上限时即被中止。
   */
  public static final long MAX_EXPANDED_BYTES = 64L * 1024 * 1024;

  private LangXlsx() {}

  /** 读结果：{@code header} = 表头名（按列序，小写去空白，空列跳过）供**精确匹配**；
   *  {@code languages} = 命中目录的语言列（按表头序）；rows 不含表头。 */
  public record LangSheet(List<String> header, List<String> languages, List<LangRow> rows) {}

  /**
   * 一行数据：{@code rowNumber} 为 Excel 行号（表头是 1，故首条数据行是 2），报错信息靠它对上用户看到的行；
   * {@code hasFormula} = 本行有公式类型单元格（ADR-005 ⑤：公式一律不收，哪怕缓存值是纯文本）。
   */
  public record LangRow(int rowNumber, String key, Map<String, String> values, boolean hasFormula) {}

  /** 导出的一行：语言码 → 生效文案。 */
  public record ExportRow(String key, Map<String, String> values) {}

  /**
   * 读首个工作表（.xlsx/.xls 由 {@link WorkbookFactory} 自行识别）。
   *
   * @param knownLanguages 目录认识的语言码（以此识别语言列，未知表头列忽略）
   */
  public static LangSheet read(InputStream in, List<String> knownLanguages) {
    try (Workbook workbook = WorkbookFactory.create(in)) {
      if (workbook.getNumberOfSheets() == 0) {
        throw new IllegalArgumentException("工作簿没有工作表");
      }
      Sheet sheet = workbook.getSheetAt(0);
      DataFormatter formatter = new DataFormatter();
      Row header = sheet.getRow(sheet.getFirstRowNum());
      if (header == null) {
        return new LangSheet(List.of(), List.of(), List.of());
      }
      int keyColumn = -1;
      List<String> names = new ArrayList<>();
      Map<String, Integer> columnByLanguage = new LinkedHashMap<>();
      for (int c = header.getFirstCellNum(); c >= 0 && c < header.getLastCellNum(); c++) {
        String name = formatter.formatCellValue(header.getCell(c)).trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
          continue;
        }
        names.add(name);
        if (KEY_COLUMN.equals(name)) {
          keyColumn = c;
          continue;
        }
        if (knownLanguages.contains(name) && !columnByLanguage.containsKey(name)) {
          columnByLanguage.put(name, c);
        }
      }
      List<LangRow> rows = new ArrayList<>();
      for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
        Row excelRow = sheet.getRow(r);
        if (excelRow == null) {
          continue;
        }
        String key = keyColumn < 0 ? "" : text(formatter, excelRow, keyColumn);
        boolean formula = keyColumn >= 0 && isFormula(excelRow, keyColumn);
        Map<String, String> values = new LinkedHashMap<>();
        boolean blank = key.isEmpty();
        for (Map.Entry<String, Integer> column : columnByLanguage.entrySet()) {
          String value = text(formatter, excelRow, column.getValue());
          values.put(column.getKey(), value);
          blank &= value.isEmpty();
          formula |= isFormula(excelRow, column.getValue());
        }
        if (blank) {
          // 完全空行（含键）：Excel 里的视觉空行，跳过而非判错
          continue;
        }
        rows.add(new LangRow(r + 1, key, values, formula));
      }
      return new LangSheet(List.copyOf(names), List.copyOf(columnByLanguage.keySet()), List.copyOf(rows));
    } catch (IOException | RuntimeException e) {
      // 非 Excel / 加密 / 空文件：统一交调用方按「文件不可解析」处理
      throw new IllegalArgumentException("Excel 不可解析：" + e.getMessage(), e);
    }
  }

  /** 模板/导出：表头 key + 各语言码；列宽 key 50 / 语言列 80（×256），冻结首行（ADR-005 ④）。 */
  public static byte[] write(List<String> languages, List<ExportRow> rows) throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet(SHEET_NAME);
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue(KEY_COLUMN);
      sheet.setColumnWidth(0, KEY_COLUMN_WIDTH * 256);
      for (int i = 0; i < languages.size(); i++) {
        header.createCell(i + 1).setCellValue(languages.get(i));
        sheet.setColumnWidth(i + 1, LANGUAGE_COLUMN_WIDTH * 256);
      }
      sheet.createFreezePane(0, 1);
      for (int r = 0; r < rows.size(); r++) {
        ExportRow row = rows.get(r);
        Row excelRow = sheet.createRow(r + 1);
        excelRow.createCell(0).setCellValue(row.key());
        for (int i = 0; i < languages.size(); i++) {
          excelRow.createCell(i + 1).setCellValue(row.values().getOrDefault(languages.get(i), ""));
        }
      }
      workbook.write(out);
      return out.toByteArray();
    }
  }

  /** 单元格是公式类型（缓存值不算数：`=cmd|…` 这类注入正是靠类型识别）。 */
  private static boolean isFormula(Row row, int column) {
    Cell cell = row.getCell(column);
    return cell != null && cell.getCellType() == CellType.FORMULA;
  }

  /**
   * 解压比闸门（T60 / SEC-09）：xlsx 是 zip，几 MB 的压缩流能解出几十 GB（"zip 炸弹"），
   * POI 打开工作簿会把它们全解出来。逐条计数解压后字节，**到上限立即返回 false**——
   * 早停是关键：不把一个炸弹读完，CPU 与内存的上界才由本闸门决定。
   *
   * <p>判据取**绝对上限**而非压缩比：比值会误伤合法的稀疏工作簿（小而极能压），
   * 而绝对上限直接限定本次请求要保护的东西。`.xls`（OLE2）不是 zip，本闸门不适用，
   * 其体量已由 ≤5MB 的体量闸门兜住。
   *
   * <p>不是合法 zip（读不动）时返回 true：格式合法性归 POI 判（按"不可解析"处理），这里只管大小。
   */
  public static boolean expansionWithinLimit(byte[] content) {
    long total = 0;
    byte[] buffer = new byte[8192];
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
      while (true) {
        if (zip.getNextEntry() == null) {
          return true;
        }
        int read;
        while ((read = zip.read(buffer)) > 0) {
          total += read;
          if (total > MAX_EXPANDED_BYTES) {
            return false;
          }
        }
      }
    } catch (IOException e) {
      return true;
    }
  }

  private static String text(DataFormatter formatter, Row row, int column) {
    Cell cell = row.getCell(column);
    return cell == null ? "" : formatter.formatCellValue(cell).trim();
  }
}
