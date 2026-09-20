package net.zentao.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import net.zentao.platform.error.ErrorCode;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * CSRF 简易防线（03 §7）：写请求必须带 X-Requested-With: fetch，缺失 → 40301。
 * 过滤器阶段抛异常不走 @RestControllerAdvice，故直接写错误信封。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CsrfHeaderFilter extends OncePerRequestFilter {

  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
  private static final String HEADER = "X-Requested-With";

  private final JsonMapper jsonMapper;

  public CsrfHeaderFilter(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (SAFE_METHODS.contains(request.getMethod()) || "fetch".equals(request.getHeader(HEADER))) {
      chain.doFilter(request, response);
      return;
    }
    response.setStatus(ErrorCode.FORBIDDEN.httpStatus().value());
    response.setContentType("application/json;charset=UTF-8");
    jsonMapper.writeValue(
        response.getWriter(), ErrorEnvelope.of(ErrorCode.FORBIDDEN.code(), "缺少 CSRF 头。", Map.of(), MDC.get("traceId")));
  }
}
