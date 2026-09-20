package net.zentao.platform.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.session.SessionResolver;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 写端点审计横切（B1 §H3）：拦全部写映射（POST/PUT/PATCH/DELETE）的控制器方法，成功后落一行
 * audit_log。只审成功请求——失败的写由全局异常信封与登录安全日志负责。
 *
 * <p>委托 {@link AuditRecorder} 落库，本类只负责「谁（会话主体）/什么动作/对象/IP」。匿名请求不审：
 * 登录自身无会话（也用不上主体），由 {@code SessionController.login} 显式记账，避免同一动作两行。
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

  private final SessionResolver resolver;
  private final AuditRecorder recorder;

  public AuditAspect(SessionResolver resolver, AuditRecorder recorder) {
    this.resolver = resolver;
    this.recorder = recorder;
  }

  @AfterReturning("execution(* net.zentao..web..*Controller.*(..)) && (" + WRITE_MAPPINGS + ")")
  public void auditWrite(JoinPoint joinPoint) {
    HttpServletRequest request = currentRequest();
    if (request == null) {
      return;
    }
    SessionPrincipal principal;
    try {
      principal = resolver.resolve(request);
    } catch (ApiException anonymous) {
      // 无会话的写请求（登录）不自审：主体未知，且同一动作已有显式记账点。
      log.debug("audit skipped, no session uri={}", request.getRequestURI());
      return;
    }
    Map.Entry<String, String> pathId = pathId(request);
    Audit annotation = annotationOf(joinPoint);
    recorder.record(principal.account(), actionOf(annotation, request), objectTypeOf(annotation, pathId),
        objectIdOf(pathId), request.getMethod() + " " + request.getRequestURI(), request.getRemoteAddr());
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

  private static Audit annotationOf(JoinPoint joinPoint) {
    if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
      return null;
    }
    Method method = signature.getMethod();
    return AnnotatedElementUtils.findMergedAnnotation(method, Audit.class);
  }

  /** 路由里那个以 Id 结尾的模板变量（{@code {productId} → "123"}）；无路由变量时为 null。 */
  private static Map.Entry<String, String> pathId(HttpServletRequest request) {
    if (!(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables)) {
      return null;
    }
    for (Map.Entry<?, ?> entry : variables.entrySet()) {
      String name = String.valueOf(entry.getKey());
      if (name.toLowerCase().endsWith("id")) {
        return Map.entry(name, String.valueOf(entry.getValue()));
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

  /** 对象 id：路由变量为纯数字才记（非数字的 id 位不记，避免把账号名之类塞进 BIGINT）。 */
  private static Long objectIdOf(Map.Entry<String, String> pathId) {
    if (pathId == null) {
      return null;
    }
    try {
      return Long.valueOf(pathId.getValue());
    } catch (NumberFormatException notNumericId) {
      return null;
    }
  }

  private static HttpServletRequest currentRequest() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
      return attributes.getRequest();
    }
    return null;
  }
}
