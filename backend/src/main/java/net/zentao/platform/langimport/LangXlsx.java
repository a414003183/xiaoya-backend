package net.zentao.platform.langimport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 语言包 Excel 读写（platform 卡 §3.12）——导入与导出共用同一列格式，故导出文件可原样回传导入：
 * 首行表头 {@code key | zh-cn | en}（键列名大小写不敏感，语言列名 = 语言码），一行一个全点分键。
 * 读取用 {@link DataFormatter}（单元格按显示文本取，数字/日期不会被强转成怪值）。
 */
public final class LangXlsx {

  /** 键列名（大小写不敏感比对）。 */
  public static final String KEY_COLUMN = "key";
  public static final String CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  private static final String SHEET_NAME = "lang";

  private LangXlsx() {}

  /** 读结果：{@code hasKeyColumn} = 表头有无 key 列；{@code languages} = 命中目录的语言列（按表头顺序）；rows 不含表头。 */
  public record LangSheet(boolean hasKeyColumn, List<String> languages, List<LangRow> rows) {}

  /** 一行数据：{@code rowNumber} 为 Excel 行号（表头是 1，故首条数据行是 2），报错信息靠它对上用户看到的行。 */
  public record LangRow(int rowNumber, String key, Map<String, String> values) {}

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
        return new LangSheet(false, List.of(), List.of());
      }
      int keyColumn = -1;
      Map<String, Integer> columnByLanguage = new LinkedHashMap<>();
      for (int c = header.getFirstCellNum(); c >= 0 && c < header.getLastCellNum(); c++) {
        String name = formatter.formatCellValue(header.getCell(c)).trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
          continue;
        }
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
        Map<String, String> values = new LinkedHashMap<>();
        boolean blank = key.isEmpty();
        for (Map.Entry<String, Integer> column : columnByLanguage.entrySet()) {
          String value = text(formatter, excelRow, column.getValue());
          values.put(column.getKey(), value);
          blank &= value.isEmpty();
        }
        if (blank) {
          // 完全空行（含键）：Excel 里的视觉空行，跳过而非判错
          continue;
        }
        rows.add(new LangRow(r + 1, key, values));
      }
      return new LangSheet(keyColumn >= 0, List.copyOf(columnByLanguage.keySet()), List.copyOf(rows));
    } catch (IOException | RuntimeException e) {
      // 非 Excel / 加密 / 空文件：统一交调用方按「文件不可解析」处理
      throw new IllegalArgumentException("Excel 不可解析：" + e.getMessage(), e);
    }
  }

  /** 写单工作表的 .xlsx（表头 key + 各语言码；行序即入参序）。 */
  public static byte[] write(List<String> languages, List<ExportRow> rows) throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet(SHEET_NAME);
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue(KEY_COLUMN);
      for (int i = 0; i < languages.size(); i++) {
        header.createCell(i + 1).setCellValue(languages.get(i));
      }
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

  private static String text(DataFormatter formatter, Row row, int column) {
    Cell cell = row.getCell(column);
    return cell == null ? "" : formatter.formatCellValue(cell).trim();
  }
}
