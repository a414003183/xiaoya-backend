package net.zentao.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.rbac.PrivilegeChecker;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.session.SessionResolver;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * 非 /api 管理面的守卫（B1 actuator + T14 接口文档）：按规则表要登录、要权限码。
 *
 * <p>为什么是过滤器而不是 MVC 拦截器：这些面由各自的 HandlerMapping/资源处理器暴露
 * （actuator 有自己的 mapping，springdoc 的 /v3/api-docs 与 /swagger-ui 静态资源同理），
 * {@code WebMvcConfigurer} 注册的 {@link PrivilegeInterceptor} 对它们不生效——实测匿名
 * /actuator/info 与 /v3/api-docs 都是 200。过滤器阶段抛异常不走 @RestControllerAdvice，
 * 故与 {@link CsrfHeaderFilter} 同法直接写错误信封。
 *
 * <p>规则表是「前缀 → 所需权限码（null = 登录即可）」。将来往暴露面加东西，在这里加一行，
 * 别指望它默认有鉴权。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class SurfaceGuardFilter extends OncePerRequestFilter {

  /** 前缀 → 权限码；null 表示「登录即可」。 */
  private static final List<Rule> RULES = List.of(
      // actuator 只放探活：exposure 里加端点无需再想起补鉴权（B1）
      new Rule("/actuator", null),
      // 接口文档（T14）：全量端点清单 + 可试调 UI，不该匿名可读
      new Rule("/v3/api-docs", "api-doc-view"),
      new Rule("/swagger-ui", "api-doc-view"));

  private static final String ACTUATOR_HEALTH_PATH = "/actuator/health";

  private record Rule(String prefix, String privilege) {}

  private final SessionResolver resolver;
  private final PrivilegeChecker checker;
  private final JsonMapper jsonMapper;
  private final LocaleResolver localeResolver;
  private final MessageResolver messages;

  public SurfaceGuardFilter(SessionResolver resolver, PrivilegeChecker checker, JsonMapper jsonMapper,
      LocaleResolver localeResolver, MessageResolver messages) {
    this.resolver = resolver;
    this.checker = checker;
    this.jsonMapper = jsonMapper;
    this.localeResolver = localeResolver;
    this.messages = messages;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (path.startsWith(ACTUATOR_HEALTH_PATH)) {
      chain.doFilter(request, response);
      return;
    }
    Rule rule = RULES.stream().filter(candidate -> path.startsWith(candidate.prefix())).findFirst().orElse(null);
    if (rule == null) {
      chain.doFilter(request, response);
      return;
    }
    SessionPrincipal principal;
    try {
      principal = resolver.resolve(request);
    } catch (ApiException unauthenticated) {
      writeError(response, ErrorCode.UNAUTHENTICATED, localized(request, unauthenticated));
      return;
    }
    if (rule.privilege() != null && !checker.hasPrivilege(principal, rule.privilege())) {
      // 与 PrivilegeInterceptor 同码同文案：两处判定对调用方是同一件事
      writeError(response, ErrorCode.FORBIDDEN, messages.forLocale(
          localeResolver.resolveLocale(request), "error.privilege.missing", new Object[] {rule.privilege()}));
      return;
    }
    chain.doFilter(request, response);
  }

  /**
   * 带键异常按**本请求语言**解析。语言在这里显式解析：过滤器早于 DispatcherServlet，
   * `LocaleContextHolder` 尚未落值（`MessageResolver#forRequest` 此时只会拿到 JVM 默认语言）。
   */
  private String localized(HttpServletRequest request, ApiException e) {
    if (e.messageKey() == null) {
      return e.getMessage();
    }
    return messages.forLocale(localeResolver.resolveLocale(request), e.messageKey(), e.messageArgs());
  }

  private void writeError(HttpServletResponse response, ErrorCode code, String message) throws IOException {
    response.setStatus(code.httpStatus().value());
    response.setContentType("application/json;charset=UTF-8");
    jsonMapper.writeValue(response.getWriter(), ErrorEnvelope.of(code.code(), message, Map.of(), MDC.get("traceId")));
  }
}
