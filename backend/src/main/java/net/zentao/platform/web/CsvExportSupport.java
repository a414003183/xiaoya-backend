package net.zentao.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import net.zentao.platform.error.ApiException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A-04 CSV 导出横切（03 §3：?format=csv）：拦全部 {@code net.zentao..web..*Controller} 方法，
 * controller 返回 DataEnvelope 且其 data 为含 {@code items()}/{@code total()} 组件的 record 时，
 * 将 items 序列化为 CSV（首行表头=首条记录组件名，record 声明序；标量取文本，对象/数组输出 JSON 串）。
 * total &gt; 5000 → 40001（提示收窄过滤）；写 UTF-8 BOM + text/csv 后返回 null 短路 MVC（200）。
 */
@Aspect
@Component
public class CsvExportSupport {

  static final int MAX_ROWS = 5000;
  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  private final JsonMapper jsonMapper;

  public CsvExportSupport(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Around("execution(* net.zentao..web..*Controller.*(..))")
  public Object exportIfRequested(ProceedingJoinPoint joinPoint) throws Throwable {
    HttpServletRequest request = currentRequest();
    if (request == null || !"csv".equals(request.getParameter("format"))) {
      return joinPoint.proceed();
    }
    Object result = joinPoint.proceed();
    if (!(result instanceof DataEnvelope<?> envelope) || !(envelope.data() instanceof Record list)) {
      return result;
    }
    Object itemsValue = component(list, "items");
    Object totalValue = component(list, "total");
    if (!(itemsValue instanceof Collection<?> items) || !(totalValue instanceof Number total)) {
      return result;
    }
    if (total.longValue() > MAX_ROWS) {
      throw ApiException.badRequest("CSV 导出上限 " + MAX_ROWS + " 行（当前 " + total.longValue() + "），请收窄过滤条件。");
    }
    if (!items.isEmpty() && !(items.iterator().next() instanceof Record)) {
      return result;
    }
    HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getResponse();
    if (response == null) {
      return result;
    }
    writeCsv(response, items);
    return null;
  }

  private void writeCsv(HttpServletResponse response, Collection<?> items) throws IOException, ReflectiveOperationException {
    response.setContentType("text/csv; charset=UTF-8");
    OutputStream out = response.getOutputStream();
    out.write(UTF8_BOM);
    if (items.isEmpty()) {
      out.flush();
      return;
    }
    StringBuilder csv = new StringBuilder();
    RecordComponent[] components = items.iterator().next().getClass().getRecordComponents();
    csv.append(headerOf(components)).append('\n');
    for (Object item : items) {
      csv.append(rowOf((Record) item)).append('\n');
    }
    out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
    out.flush();
  }

  private static String headerOf(RecordComponent[] components) {
    StringBuilder header = new StringBuilder();
    for (RecordComponent component : components) {
      if (header.length() > 0) {
        header.append(',');
      }
      header.append(escape(component.getName()));
    }
    return header.toString();
  }

  private String rowOf(Record item) throws ReflectiveOperationException {
    StringBuilder row = new StringBuilder();
    for (RecordComponent component : item.getClass().getRecordComponents()) {
      if (row.length() > 0) {
        row.append(',');
      }
      row.append(escape(csvValue(component.getAccessor().invoke(item))));
    }
    return row.toString();
  }

  /** 标量取文本（数字/布尔/字符串），null 为空，对象/数组 writeValueAsString 为 JSON 串。 */
  private String csvValue(Object value) {
    if (value == null) {
      return "";
    }
    JsonNode node = jsonMapper.valueToTree(value);
    if (node.isContainer()) {
      return jsonMapper.writeValueAsString(node);
    }
    return node.asString("");
  }

  /** RFC4180：含逗号/引号/换行的字段整体包引号，内部引号翻倍。 */
  static String escape(String field) {
    if (field.indexOf(',') >= 0 || field.indexOf('"') >= 0 || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0) {
      return '"' + field.replace("\"", "\"\"") + '"';
    }
    return field;
  }

  private static Object component(Record target, String name) throws ReflectiveOperationException {
    for (RecordComponent component : target.getClass().getRecordComponents()) {
      if (component.getName().equals(name)) {
        return component.getAccessor().invoke(target);
      }
    }
    return null;
  }

  private static HttpServletRequest currentRequest() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
      return attributes.getRequest();
    }
    return null;
  }
}
