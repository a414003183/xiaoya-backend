package net.zentao.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.zentao.platform.audit.AuditRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.rbac.Anonymous;
import net.zentao.platform.rbac.PrivilegeChecker;
import net.zentao.platform.rbac.RequirePrivilege;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.session.SessionResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 端点判定三态（platform 卡 §7.1；T50 收口 SEC-02/BE-04）：
 *
 * <ul>
 *   <li>{@code @RequirePrivilege("code")}：解析会话（无 → 40101）+ 查码（无 → 40301）；
 *   <li>{@code @Anonymous("理由")}：真匿名，不解析会话，直接放行；
 *   <li><b>两者都没有：默认拒绝匿名</b>——解析会话（无 → 40101）。
 * </ul>
 *
 * <p>为什么默认态是"要求认证"而不是原先的"直接放行"：忘一个注解不该把端点变成匿名公开面
 * （SEC-02 的 `/menus/routes`、`/dicts/{name}`、`/meta/{domain}`、`/departments/tree` 就是这样裸奔的），
 * 而不带码的端点本来就都要当前身份（本人 / 会话自身 / 处理器内判定），认证是它们的安全前提。
 * 构建期 `check-privilege-coverage` 管"每个端点要么带码、要么匿名带理由、要么白名单带理由"，运行时这层管默认拒绝。
 *
 * <p>解析结果按请求属性缓存（见 {@link SessionResolver}），处理器内再 resolve 不重复查库——
 * 原先自己 resolve 的端点走这条默认态时无额外开销，同一次查询被复用。
 */
@Component
public class PrivilegeInterceptor implements HandlerInterceptor {

  private final SessionResolver resolver;
  private final PrivilegeChecker checker;
  private final AuditRecorder auditRecorder;
  private final MessageResolver messages;

  public PrivilegeInterceptor(SessionResolver resolver, PrivilegeChecker checker, AuditRecorder auditRecorder,
      MessageResolver messages) {
    this.resolver = resolver;
    this.checker = checker;
    this.auditRecorder = auditRecorder;
    this.messages = messages;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }
    RequirePrivilege annotation = handlerMethod.getMethodAnnotation(RequirePrivilege.class);
    if (annotation == null) {
      annotation = handlerMethod.getBeanType().getAnnotation(RequirePrivilege.class);
    }
    if (annotation != null) {
      SessionPrincipal principal = resolver.resolve(request);
      if (!checker.hasPrivilege(principal, annotation.value())) {
        // T10：拒绝也留痕（result=denied）——处理器根本没执行到，审计横切看不到这类请求；
        // 原因按带键异常解析成句子（与失败行同口径），缺的权限码在 detail 里
        ApiException denied =
            ApiException.keyed(ErrorCode.FORBIDDEN, "error.privilege.missing", annotation.value());
        auditRecorder.recordDenied(principal.account(), "access-denied",
            request.getMethod() + " " + request.getRequestURI() + "（缺权限码 " + annotation.value() + "）",
            messages.reasonOf(denied), request.getRemoteAddr(), request.getHeader("User-Agent"));
        throw denied;
      }
      return true;
    }
    if (isAnonymous(handlerMethod)) {
      return true;
    }
    resolver.resolve(request);
    return true;
  }

  /** 方法级优先，其次类级（与 {@code @RequirePrivilege} 同规则）。 */
  private static boolean isAnonymous(HandlerMethod handlerMethod) {
    return handlerMethod.getMethodAnnotation(Anonymous.class) != null
        || handlerMethod.getBeanType().getAnnotation(Anonymous.class) != null;
  }
}
