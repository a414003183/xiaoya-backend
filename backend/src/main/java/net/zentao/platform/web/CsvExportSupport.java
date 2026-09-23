package net.zentao.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.zentao.platform.audit.AuditHasher;
import net.zentao.platform.audit.AuditRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.ratelimit.RateLimits;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.session.SessionResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A-04 CSV 导出横切（03 §3：?format=csv）：拦全部 {@code net.zentao..web..*Controller} 方法，
 * controller 返回 DataEnvelope 且其 data 为含 {@code items()}/{@code total()} 组件的 record 时，
 * 将 items 序列化为 CSV（首行表头=首条记录组件名，record 声明序；标量取文本，对象/数组输出 JSON 串）。
 * total &gt; {@link Filters#CSV_MAX_LIMIT} → 40001（提示收窄过滤）；写 UTF-8 BOM + text/csv 后返回 null 短路 MVC（200）。
 *
 * <p>T59：导出按账号计窗限流（42901，拦在查询之前）；单元格值做公式注入防御（见 {@link #escape}）。
 */
@Aspect
@Component
public class CsvExportSupport {

  private static final Logger log = LoggerFactory.getLogger(CsvExportSupport.class);

  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
  /** 会被 Excel/Sheets 当公式起头的字符（OWASP 口径：四个符号 + 制表/回车）。 */
  private static final String FORMULA_PREFIXES = "=+-@\t\r";
  /** 纯数值（含符号/小数/科学计数）：它是数字不是公式，加前缀会把负数整列变成文本。 */
  private static final Pattern PLAIN_NUMBER = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

  private final JsonMapper jsonMapper;
  private final RateLimits rateLimits;
  private final SessionResolver sessionResolver;
  private final AuditRecorder auditRecorder;

  public CsvExportSupport(JsonMapper jsonMapper, RateLimits rateLimits, SessionResolver sessionResolver,
      AuditRecorder auditRecorder) {
    this.jsonMapper = jsonMapper;
    this.rateLimits = rateLimits;
    this.sessionResolver = sessionResolver;
    this.auditRecorder = auditRecorder;
  }

  @Around("execution(* net.zentao..web..*Controller.*(..))")
  public Object exportIfRequested(ProceedingJoinPoint joinPoint) throws Throwable {
    HttpServletRequest request = currentRequest();
    if (request == null || !"csv".equals(request.getParameter("format"))) {
      return joinPoint.proceed();
    }
    // T59 SEC-07：拦在 proceed **之前**——放在之后就只拦住了 CSV 拼装，没拦住上限 5000 行的那次查询。
    // 账号取拦截器已缓存的那份（非匿名端点必有）；匿名请求拿不到账号，按设计跳过（见 SessionResolver#cachedPrincipal）
    SessionPrincipal principal = sessionResolver.cachedPrincipal(request);
    if (principal != null) {
      rateLimits.requireAllowed(RateLimits.Scope.export, principal.account());
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
    if (total.longValue() > Filters.CSV_MAX_LIMIT) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "csv.export.tooManyRows", Filters.CSV_MAX_LIMIT, total.longValue());
    }
    if (!items.isEmpty() && !(items.iterator().next() instanceof Record)) {
      return result;
    }
    HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getResponse();
    if (response == null) {
      return result;
    }
    // 先成字节再写：同一份字节既是响应体，也是审计行 extra.sha256 的被哈希对象（VISION 事项 4 第 6 行）
    byte[] payload = csvPayload(items);
    response.setContentType("text/csv; charset=UTF-8");
    OutputStream out = response.getOutputStream();
    out.write(payload);
    out.flush();
    recordExport(request, principal, items, payload);
    return null;
  }

  /** 导出审计（T10）：筛选条件、字段（CSV 表头）、条数、文件哈希、下载 IP——明细全进 extra。 */
  private void recordExport(HttpServletRequest request, SessionPrincipal principal, Collection<?> items,
      byte[] payload) {
    try {
      Map<String, Object> extra = new LinkedHashMap<>();
      extra.put("endpoint", request.getMethod() + " " + request.getRequestURI());
      extra.put("filters", filtersOf(request));
      extra.put("fields", fieldsOf(items));
      extra.put("rows", items.size());
      extra.put("sha256", AuditHasher.sha256(payload));
      auditRecorder.recordDownload(principal == null ? null : principal.account(), "export-csv", null, null,
          request.getMethod() + " " + request.getRequestURI() + "?format=csv", request.getRemoteAddr(),
          request.getHeader("User-Agent"), extra);
    } catch (RuntimeException e) {
      log.error("export audit failed uri={}", request.getRequestURI(), e);
    }
  }

  /** 筛选条件 = 除 format 之外的查询参数（单值取标量，多值保序成列表）。 */
  private static Map<String, Object> filtersOf(HttpServletRequest request) {
    Map<String, Object> filters = new LinkedHashMap<>();
    for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
      if ("format".equals(entry.getKey())) {
        continue;
      }
      String[] values = entry.getValue();
      filters.put(entry.getKey(), values.length == 1 ? values[0] : List.of(values));
    }
    return filters;
  }

  private static List<String> fieldsOf(Collection<?> items) {
    if (items.isEmpty()) {
      return List.of();
    }
    return Arrays.stream(items.iterator().next().getClass().getRecordComponents())
        .map(RecordComponent::getName)
        .toList();
  }

  private byte[] csvPayload(Collection<?> items) throws ReflectiveOperationException {
    ByteArrayOutputStream payload = new ByteArrayOutputStream();
    payload.writeBytes(UTF8_BOM);
    if (items.isEmpty()) {
      return payload.toByteArray();
    }
    StringBuilder csv = new StringBuilder();
    RecordComponent[] components = items.iterator().next().getClass().getRecordComponents();
    csv.append(headerOf(components)).append('\n');
    for (Object item : items) {
      csv.append(rowOf((Record) item)).append('\n');
    }
    payload.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));
    return payload.toByteArray();
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

  /**
   * RFC4180（含逗号/引号/换行的字段整体包引号，内部引号翻倍）+ **公式注入防御**（T59 SEC-06）：
   * 以 `= + - @` `\t` `\r` 开头的单元格前缀单引号——Excel/LibreOffice 才会按文本渲染而不是当公式执行
   * （`=HYPERLINK(...)`/DDE 那套就是从这儿进来的）。
   *
   * <p>纯数值例外（`-5`/`+3.2`/`1e5`）：它们是数字不是公式，一律前缀会把导出里的负数整列变成文本，
   * 那是拿数据质量换安全。判断只看整串是不是一个数字，`-2+3` 这种照样文本化。
   */
  static String escape(String field) {
    String value = defuseFormula(field);
    if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
      return '"' + value.replace("\"", "\"\"") + '"';
    }
    return value;
  }

  private static String defuseFormula(String field) {
    if (field.isEmpty() || FORMULA_PREFIXES.indexOf(field.charAt(0)) < 0 || PLAIN_NUMBER.matcher(field).matches()) {
      return field;
    }
    return "'" + field;
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
