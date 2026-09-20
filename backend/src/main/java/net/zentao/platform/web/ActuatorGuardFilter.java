package net.zentao.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.session.SessionResolver;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * B1：actuator 的探活面（health）匿名放行，其余一律要求已登录，无会话 → 40101。
 *
 * <p>用过滤器而非 MVC 拦截器：actuator 端点由自己的 HandlerMapping 暴露，WebMvcConfigurer 注册的
 * 拦截器对它不生效（实测匿名 /actuator/info 仍得 200）。过滤器阶段抛异常不走 @RestControllerAdvice，
 * 故与 CsrfHeaderFilter 同法直接写错误信封。
 *
 * <p>暴露面本身由 application.yml 的 management.endpoints.web.exposure.include 控制；本过滤器保证
 * 「暴露出来的东西不会匿名可见」——将来往 exposure 里加端点，无需再想起补鉴权。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class ActuatorGuardFilter extends OncePerRequestFilter {

  private static final String BASE_PATH = "/actuator";
  private static final String HEALTH_PATH = "/actuator/health";

  private final SessionResolver resolver;
  private final JsonMapper jsonMapper;

  public ActuatorGuardFilter(SessionResolver resolver, JsonMapper jsonMapper) {
    this.resolver = resolver;
    this.jsonMapper = jsonMapper;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (!path.startsWith(BASE_PATH) || path.startsWith(HEALTH_PATH)) {
      chain.doFilter(request, response);
      return;
    }
    try {
      resolver.resolve(request);
    } catch (ApiException unauthenticated) {
      response.setStatus(ErrorCode.UNAUTHENTICATED.httpStatus().value());
      response.setContentType("application/json;charset=UTF-8");
      jsonMapper.writeValue(response.getWriter(), ErrorEnvelope.of(
          ErrorCode.UNAUTHENTICATED.code(), unauthenticated.getMessage(), Map.of(), MDC.get("traceId")));
      return;
    }
    chain.doFilter(request, response);
  }
}
