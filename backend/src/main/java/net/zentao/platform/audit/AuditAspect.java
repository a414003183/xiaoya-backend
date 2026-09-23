package net.zentao.platform.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.session.SessionResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.databind.json.JsonMapper;

/**
 * 写端点审计横切（B1 §H3；T04 从「只审成功」升级为**成败都审 + 分类 + 设备 + 可选 diff**；
 * T10 补齐四类采集：声明式 keyFields 回落、审批类整快照、批量摘要、键寻址资源 diff）：
 * 拦全部写映射（POST/PUT/PATCH/DELETE）的控制器方法，成功落 result=success，抛错落 result=fail + reason。
 *
 * <p>本类只负责「谁（会话主体）/什么动作/对象/来源（IP·UA·设备）/结果/变更」，落库与链哈希委托
 * {@link AuditRecorder}，分类与采集口径由 {@link AuditCatalog} 决定。匿名请求不审：登录自身无会话（也用不上主体），
 * 由 {@code SessionController.login} 显式记账（含失败），避免同一动作两行。
 *
 * <p><b>失败行</b>：异常照旧往外抛（审计不吞异常），只旁路记一行——「谁试过而没成功」是审计的一半价值。
 * <b>diff</b>：方法标注 {@link AuditDiff} 时执行前后各取一次快照比对；字段集取注解声明，注解留空则回落
 * {@link AuditCatalog} 里该动作登记的 keyFields（声明式分级，ADR-004 决策 2）。取不到快照就不记 diff（不影响业务）。
 * <b>快照</b>：目录里 {@code snapshot=true} 的动作（发布/评审类）额外写 {@code snapshot} 列的整快照。
 * <b>批量</b>：请求体是批量体（类名带 Batch + 带 ids/items 组件）时写批次摘要（batch_id + 筛选条件/总数/成败数/清单截断）。
 * <b>键寻址</b>：{@link AuditDiff#idParam} 点名的 id 位不是数字（setting 的 key / menu 的 nodeKey）时走
 * {@link AuditSnapshotRegistry#snapshotKeyed}——这类资源进不了 BIGINT 的 object_id，diff 仍照采。
 *
 * <p><b>敏感读</b>（{@link AuditSensitive}）：另一条 advice 盯 GET 端点，见 {@link #auditSensitiveRead}。
 */
@Aspect
@Component
public class AuditAspect {

  private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

  private static final String WRITE_MAPPINGS =
      "@annotation(org.springframework.web.bind.annotation.PostMapping)"
          + " || @annotation(org.springframework.web.bind.annotation.PutMapping)"
          + " || @annotation(org.springframework.web.bind.annotation.PatchMapping)"
          + " || @annotation(org.springframework.web.bind.annotation.DeleteMapping)";

  /** reason 列宽 255：异常消息可能很长，截断保插入成功。 */
  private static final int REASON_MAX = 255;

  /** 批量清单截断（VISION 事项 4 第 5 行「逐条摘要」的上限；更多计数在 idsTruncated）。 */
  private static final int BATCH_IDS_MAX = 50;

  /** 失败明细上限（逐条摘要的失败半边，比清单更少——失败原因串会长）。 */
  private static final int BATCH_FAILURES_MAX = 20;

  /**
   * 批量识别线索（类名含 Batch + 带 ids/items 组件，见 {@link #batchArg}）——**不引 {@code platform.web} 类型**：
   * platform 关注点之间不许成环（ArchUnit A2），而 web 侧已经依赖 audit。
   * 与 {@code CsvExportSupport} 泛化读取 record 组件是同一手法。
   */
  private static final String BATCH_CLASS_HINT = "Batch";

  private final SessionResolver resolver;
  private final AuditRecorder recorder;
  private final AuditDiffer differ;
  private final AuditCatalog catalog;
  private final MessageResolver messages;
  private final JsonMapper jsonMapper;

  public AuditAspect(SessionResolver resolver, AuditRecorder recorder, AuditDiffer differ,
      AuditCatalog catalog, MessageResolver messages, JsonMapper jsonMapper) {
    this.resolver = resolver;
    this.recorder = recorder;
    this.differ = differ;
    this.catalog = catalog;
    this.messages = messages;
    this.jsonMapper = jsonMapper;
  }

  /** 审计对象位：数值 id（进 object_id 列）与字符串键（键寻址资源，只进 detail/diff）二选一。 */
  private record DiffTarget(String objectType, Long objectId, String objectKey) {}

  @Around("execution(* net.zentao..web..*Controller.*(..)) && (" + WRITE_MAPPINGS + ")")
  public Object auditWrite(ProceedingJoinPoint joinPoint) throws Throwable {
    HttpServletRequest request = currentRequest();
    if (request == null) {
      return joinPoint.proceed();
    }
    SessionPrincipal principal;
    try {
      principal = resolver.resolve(request);
    } catch (ApiException anonymous) {
      // 无会话的写请求（登录）不自审：主体未知，且同一动作已有显式记账点。
      log.debug("audit skipped, no session uri={}", request.getRequestURI());
      return joinPoint.proceed();
    }
    Map<String, String> vars = templateVars(request);
    Map.Entry<String, String> pathId = pathId(vars);
    Audit annotation = annotationOf(joinPoint);
    AuditDiff diff = diffOf(joinPoint);
    String objectType = diff != null ? diff.objectType() : objectTypeOf(annotation, pathId);
    String rawId = idValue(diff, vars, request);
    Long objectId = numericOf(rawId);
    String objectKey = objectId == null ? rawId : null;
    Map<String, Object> before = diff == null ? null
        : objectKey != null ? differ.beforeKeyed(objectType, objectKey) : differ.before(objectType, objectId);
    Object batch = batchArg(joinPoint.getArgs());
    DiffTarget target = new DiffTarget(objectType, objectId, objectKey);
    try {
      Object result = joinPoint.proceed();
      record(principal, request, pathId, annotation, diff, createdTarget(diff, pathId, target, result), before, batch,
          result, AuditResult.SUCCESS, null);
      return result;
    } catch (Throwable failure) {
      record(principal, request, pathId, annotation, diff, target, before, batch, null, AuditResult.FAIL,
          reasonOf(failure));
      throw failure;
    }
  }

  /**
   * 新建端点的对象位修正（T10）：{@code POST /products/{productId}/bugs} 这类「挂在父资源路径下」的写端点，
   * 路由里唯一的 Id 是**父对象**的，照记会让 bug 行挂着产品 id（object_id 列说谎）。
   * 故在「无 diff 采集（新建没有旧值可比）+ 路由变量名与 objectType 不同名 + 返回值带数字 id」时改记后者。
   * 带 diff 的动作不改：它们的 before/after 就是按路由 id 取的，中途换 id 会把两个对象比在一起。
   */
  private static DiffTarget createdTarget(AuditDiff diff, Map.Entry<String, String> pathId, DiffTarget target,
      Object result) {
    if (diff != null || target.objectType() == null || pathId == null) {
      return target;
    }
    String name = pathId.getKey();
    if (name.substring(0, name.length() - 2).equalsIgnoreCase(target.objectType())) {
      return target;
    }
    Long created = createdIdOf(result);
    return created == null ? target : new DiffTarget(target.objectType(), created, null);
  }

  /** 响应体 {@code data.id}：新建端点回的对象 id（取不到返回 null，调用方保持原 target）。 */
  private static Long createdIdOf(Object result) {
    Object data = result instanceof Record envelope ? component(envelope, "data") : result;
    if (!(data instanceof Record record)) {
      return null;
    }
    return component(record, "id") instanceof Number number ? number.longValue() : null;
  }

  /**
   * 敏感读（VISION 事项 4 第 7 行）：{@link AuditSensitive} 标在 GET 端点上，每次查看落一行——
   * 查看人/IP/UA 与对象由横切采集，字段清单与（可选）原因进 {@code extra}。匿名请求不审（无主体可记）。
   */
  @Around("execution(* net.zentao..web..*Controller.*(..))"
      + " && @annotation(net.zentao.platform.audit.AuditSensitive)")
  public Object auditSensitiveRead(ProceedingJoinPoint joinPoint) throws Throwable {
    HttpServletRequest request = currentRequest();
    AuditSensitive sensitive = annotationOf(joinPoint, AuditSensitive.class);
    if (request == null || sensitive == null) {
      return joinPoint.proceed();
    }
    SessionPrincipal principal;
    try {
      principal = resolver.resolve(request);
    } catch (ApiException anonymous) {
      log.debug("sensitive audit skipped, no session uri={}", request.getRequestURI());
      return joinPoint.proceed();
    }
    Map.Entry<String, String> pathId = pathId(templateVars(request));
    Audit annotation = annotationOf(joinPoint);
    DiffTarget target = new DiffTarget(objectTypeOf(annotation, pathId), objectIdOf(pathId), null);
    String extra = json(extraOf(sensitive, request));
    try {
      Object result = joinPoint.proceed();
      readRecord(principal, request, pathId, annotation, target, extra, AuditResult.SUCCESS, null);
      return result;
    } catch (Throwable failure) {
      readRecord(principal, request, pathId, annotation, target, extra, AuditResult.FAIL, reasonOf(failure));
      throw failure;
    }
  }

  /** 审计写入自身不许抛（记录器已兜底，这里的 diff/取头也一并兜住）。 */
  private void record(SessionPrincipal principal, HttpServletRequest request, Map.Entry<String, String> pathId,
      Audit annotation, AuditDiff diff, DiffTarget target, Map<String, Object> before, Object batch, Object result,
      AuditResult resultFlag, String reason) {
    try {
      AuditLogPO row = baseRow(principal, request, pathId, annotation, target, resultFlag, reason);
      if (diff != null) {
        AuditCatalog.AuditSpec spec = catalog.spec(row.getAction());
        // 字段集：注解声明优先，留空回落目录里该动作的 keyFields（声明式分级，ADR-004 决策 2）
        List<String> keyFields = diff.keyFields().length > 0 ? List.of(diff.keyFields()) : spec.keyFields();
        boolean keyed = target.objectKey() != null;
        row.setChanges(keyed
            ? differ.keyedChangesJson(before, target.objectType(), target.objectKey(), keyFields)
            : differ.changesJson(before, target.objectType(), target.objectId(), keyFields));
        if (spec.snapshot()) {
          row.setSnapshot(keyed
              ? differ.keyedSnapshotJson(before, target.objectType(), target.objectKey())
              : differ.snapshotJson(before, target.objectType(), target.objectId()));
        }
      }
      if (batch != null) {
        row.setBatchId(AuditRecorder.nextBatchId());
        row.setExtra(json(batchExtra(batch, result)));
      }
      recorder.record(row);
    } catch (RuntimeException e) {
      log.error("audit context collection failed uri={}", request.getRequestURI(), e);
    }
  }

  /** 敏感读行：与写行同构，只多一份 extra（字段清单 + 可选原因）。 */
  private void readRecord(SessionPrincipal principal, HttpServletRequest request, Map.Entry<String, String> pathId,
      Audit annotation, DiffTarget target, String extra, AuditResult resultFlag, String reason) {
    try {
      AuditLogPO row = baseRow(principal, request, pathId, annotation, target, resultFlag, reason);
      row.setExtra(extra);
      recorder.record(row);
    } catch (RuntimeException e) {
      log.error("sensitive audit collection failed uri={}", request.getRequestURI(), e);
    }
  }

  /** 两条采集轨共用的行骨架：谁 / 什么动作 / 哪个对象 / 从哪来 / 结果。 */
  private AuditLogPO baseRow(SessionPrincipal principal, HttpServletRequest request,
      Map.Entry<String, String> pathId, Audit annotation, DiffTarget target, AuditResult resultFlag, String reason) {
    AuditLogPO row = new AuditLogPO();
    row.setAccount(principal.account());
    row.setAction(actionOf(annotation, request));
    row.setObjectType(target.objectType());
    row.setObjectId(target.objectId());
    row.setDetail(detailOf(request, pathId));
    row.setIp(request.getRemoteAddr());
    row.setUa(UserAgents.truncate(request.getHeader("User-Agent")));
    row.setDevice(UserAgents.device(request.getHeader("User-Agent")));
    row.setResult(resultFlag.value());
    row.setReason(reason);
    return row;
  }

  /** 敏感读的 extra：字段清单 +（填了才有的）原因——VISION 要求「字段、原因」两项。 */
  private static Map<String, Object> extraOf(AuditSensitive sensitive, HttpServletRequest request) {
    Map<String, Object> extra = new LinkedHashMap<>();
    extra.put("fields", List.of(sensitive.fields()));
    String reason = request.getParameter("reason");
    if (reason != null && !reason.isBlank()) {
      extra.put("reason", reason.length() > REASON_MAX ? reason.substring(0, REASON_MAX) : reason);
    }
    return extra;
  }

  /**
   * 批量摘要（VISION 事项 4 第 5 行）：筛选条件（action/params）、总数、成功/失败数、
   * 对象清单（≤{@link #BATCH_IDS_MAX}，超出只计数）、失败明细（≤{@link #BATCH_FAILURES_MAX}）。
   *
   * <p>批量体有两代形态，都按组件名通用读取：共享的 {@code BatchActionRequest(ids, action, params)}
   * 与各域自建的 {@code *BatchCreateRequest(items)}（批量建档没有现成 id）；结果侧统一是
   * {@code results[].ok/.id/.error}。
   */
  private Map<String, Object> batchExtra(Object batchRequest, Object result) {
    Map<String, Object> extra = new LinkedHashMap<>();
    Object innerAction = component(batchRequest, "action");
    if (innerAction != null) {
      extra.put("action", innerAction);
    }
    if (component(batchRequest, "params") instanceof Map<?, ?> params && !params.isEmpty()) {
      extra.put("params", params);
    }
    List<?> ids = component(batchRequest, "ids") instanceof List<?> list ? list : List.of();
    List<?> items = component(batchRequest, "items") instanceof List<?> list ? list : List.of();
    extra.put("total", ids.isEmpty() ? items.size() : ids.size());
    if (!ids.isEmpty()) {
      extra.put("ids", ids.size() > BATCH_IDS_MAX ? ids.subList(0, BATCH_IDS_MAX) : ids);
      if (ids.size() > BATCH_IDS_MAX) {
        extra.put("idsTruncated", ids.size() - BATCH_IDS_MAX);
      }
    }
    Object outcome = result instanceof Record envelope ? component(envelope, "data") : result;
    if (outcome instanceof Record outcomeRecord && component(outcomeRecord, "results") instanceof List<?> entries) {
      List<Map<String, Object>> failures = new ArrayList<>();
      int succeeded = 0;
      for (Object entry : entries) {
        if (!(entry instanceof Record item)) {
          continue;
        }
        if (Boolean.TRUE.equals(component(item, "ok"))) {
          succeeded++;
        } else if (failures.size() < BATCH_FAILURES_MAX) {
          Map<String, Object> failure = new LinkedHashMap<>();
          failure.put("id", component(item, "id"));
          failure.put("error", component(item, "error"));
          failures.add(failure);
        }
      }
      extra.put("succeeded", succeeded);
      extra.put("failed", entries.size() - succeeded);
      if (!failures.isEmpty()) {
        extra.put("failures", failures);
      }
    }
    return extra;
  }

  /** 批量请求参数：类名带 {@link #BATCH_CLASS_HINT} 且带 ids/items 组件的请求体（两代形态通吃）。 */
  private static Object batchArg(Object[] args) {
    for (Object arg : args) {
      if (arg != null && arg.getClass().getSimpleName().contains(BATCH_CLASS_HINT)
          && (component(arg, "ids") != null || component(arg, "items") != null)) {
        return arg;
      }
    }
    return null;
  }

  /** record 组件读取（同 {@code CsvExportSupport} 手法）：取不到返回 null。 */
  private static Object component(Object target, String name) {
    for (RecordComponent component : target.getClass().getRecordComponents()) {
      if (component.getName().equals(name)) {
        try {
          return component.getAccessor().invoke(target);
        } catch (ReflectiveOperationException brokenAccessor) {
          return null;
        }
      }
    }
    return null;
  }

  private String json(Object value) {
    return value == null ? null : jsonMapper.writeValueAsString(value);
  }

  /** 失败原因：带键异常按默认语言解析成句子（落库要人能读），其余留异常名。 */
  private String reasonOf(Throwable failure) {
    String reason = failure instanceof ApiException api && api.messageKey() != null
        ? messages.reasonOf(api)
        : failure.getClass().getSimpleName();
    return reason.length() > REASON_MAX ? reason.substring(0, REASON_MAX) : reason;
  }

  /**
   * 详情串：`动词 + URI`。没有路径 id 可记对象时（如 T03 起按 `?nodeKey=` 寻址的菜单写端点，
   * key 是含 `/`/`#` 的字符串，塞不进 BIGINT 的 object_id）带上查询串，否则审计里看不出改的是哪一条。
   */
  private static String detailOf(HttpServletRequest request, Map.Entry<String, String> pathId) {
    String detail = request.getMethod() + " " + request.getRequestURI();
    String query = request.getQueryString();
    if (pathId == null && query != null && !query.isBlank()) {
      return detail + "?" + query;
    }
    return detail;
  }

  /** 标注了 @Audit 用其业务动作名；否则退化为「动词 + 路由模板」（用模板而非真实 URI，便于按动作聚合）。 */
  private static String actionOf(Audit annotation, HttpServletRequest request) {
    if (annotation != null) {
      return annotation.action();
    }
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String route = pattern instanceof String value ? value : request.getRequestURI();
    return request.getMethod().toLowerCase() + " " + route;
  }

  private static <A extends java.lang.annotation.Annotation> A annotationOf(ProceedingJoinPoint joinPoint,
      Class<A> type) {
    Method method = methodOf(joinPoint);
    return method == null ? null : AnnotatedElementUtils.findMergedAnnotation(method, type);
  }

  private static Audit annotationOf(ProceedingJoinPoint joinPoint) {
    return annotationOf(joinPoint, Audit.class);
  }

  private static AuditDiff diffOf(ProceedingJoinPoint joinPoint) {
    return annotationOf(joinPoint, AuditDiff.class);
  }

  private static Method methodOf(ProceedingJoinPoint joinPoint) {
    if (joinPoint.getSignature() instanceof MethodSignature signature) {
      return signature.getMethod();
    }
    return null;
  }

  /** 路由模板变量（{@code {taskId} → "123"}）；无路径变量时为空表。 */
  private static Map<String, String> templateVars(HttpServletRequest request) {
    if (!(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables)) {
      return Map.of();
    }
    Map<String, String> named = new LinkedHashMap<>();
    variables.forEach((key, value) -> named.put(String.valueOf(key), String.valueOf(value)));
    return named;
  }

  /**
   * diff 注解点名的 id 位（T10）：路由变量优先，其次查询参数——菜单写端点自 T03 起按 {@code ?nodeKey=}
   * 寻址，键不在路径里。注解没点名时回落「以 Id 结尾」的路由变量（T04 原口径）。
   */
  private static String idValue(AuditDiff diff, Map<String, String> vars, HttpServletRequest request) {
    if (diff == null || diff.idParam().isBlank()) {
      Map.Entry<String, String> auto = pathId(vars);
      return auto == null ? null : auto.getValue();
    }
    String name = diff.idParam();
    String value = vars.get(name);
    if (value == null) {
      value = request.getParameter(name);
    }
    return value == null || value.isBlank() ? null : value;
  }

  /** 路由里那个以 Id 结尾的模板变量（{@code {productId} → "123"}）；无路由变量时为 null。 */
  private static Map.Entry<String, String> pathId(Map<String, String> vars) {
    for (Map.Entry<String, String> entry : vars.entrySet()) {
      if (entry.getKey().toLowerCase().endsWith("id")) {
        return entry;
      }
    }
    return null;
  }

  /** 对象类型优先取注解声明；否则由路由变量名去掉 Id 后缀推导（{@code {productId}} → product）。 */
  private static String objectTypeOf(Audit annotation, Map.Entry<String, String> pathId) {
    if (annotation != null && !annotation.objectType().isBlank()) {
      return annotation.objectType();
    }
    return pathId == null ? null : pathId.getKey().substring(0, pathId.getKey().length() - 2);
  }

  /** 纯数字才是对象 id（非数字的 id 位属键寻址资源，见 {@link #idValue}）。 */
  private static Long numericOf(String rawId) {
    if (rawId == null) {
      return null;
    }
    try {
      return Long.valueOf(rawId);
    } catch (NumberFormatException notNumeric) {
      return null;
    }
  }

  /** 对象 id：路由变量为纯数字才记（非数字的 id 位不记，避免把账号名之类塞进 BIGINT）。 */
  private static Long objectIdOf(Map.Entry<String, String> pathId) {
    return pathId == null ? null : numericOf(pathId.getValue());
  }

  private static HttpServletRequest currentRequest() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
      return attributes.getRequest();
    }
    return null;
  }
}
