package net.zentao.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 每请求追踪 id：取 X-Trace-Id 或生成 16 字节 hex，进 MDC（键 {@code traceId}，JSON 日志/审计关联消费）+ 响应头，
 * 供错误信封携带。
 *
 * <p>T61/SEC-16：入站值只收 {@code [A-Za-z0-9._-]{1,64}}（含长度上限）——其余（控制符/空白/超长/非 ASCII）
 * **丢弃改自生成**，不回显攻击者内容（回显即日志与响应头注入面）。生成值恒为 32 位 hex。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String given = request.getHeader("X-Trace-Id");
    String traceId = given != null && SAFE_TRACE_ID.matcher(given).matches()
        ? given
        : HexFormat.of().formatHex(random16());
    MDC.put("traceId", traceId);
    response.setHeader("X-Trace-Id", traceId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove("traceId");
    }
  }

  private static byte[] random16() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    return bytes;
  }
}
