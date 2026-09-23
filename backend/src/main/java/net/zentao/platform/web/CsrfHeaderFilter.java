package net.zentao.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.i18n.MessageResolver;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * CSRF 防线（03 §7 + T61/SEC-19 二层）：
 *
 * <ol>
 *   <li>自定义头判据：写请求必须带 {@code X-Requested-With: fetch}，缺失 → 40301（跨站表单/图片等
 *       「简单请求」带不了自定义头）；</li>
 *   <li>同源二层（T61）：写请求带 {@code Origin} 时必须与 {@code X-Forwarded-Host}（反代采信后的 host）
 *       或 {@code Host} 同源；带 {@code Sec-Fetch-Site} 时拒绝 {@code cross-site}（{@code same-origin}/
 *       {@code same-site}/{@code none} 放行）。两者都缺（curl 等非浏览器客户端）照旧放行。</li>
 * </ol>
 *
 * <p><b>警告：将来若开 CORS（任何非同源的 Access-Control-Allow-Origin）必须同步收紧本层</b>——CORS 预检一旦
 * 放行，跨站 fetch 就能带上 {@code X-Requested-With} 与伪造的 {@code X-Forwarded-Host}，两层判据会同时失效；
 * 届时必须改成「Origin 显式白名单 + 服务端拒绝客户端自报的 X-Forwarded-Host」，不能只加 CORS 头了事。
 *
 * <p>过滤器阶段抛异常不走 @RestControllerAdvice，故直接写错误信封（文案按请求语言，见 {@link SurfaceGuardFilter}）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CsrfHeaderFilter extends OncePerRequestFilter {

  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
  private static final String HEADER = "X-Requested-With";

  private final JsonMapper jsonMapper;
  private final LocaleResolver localeResolver;
  private final MessageResolver messages;

  public CsrfHeaderFilter(JsonMapper jsonMapper, LocaleResolver localeResolver, MessageResolver messages) {
    this.jsonMapper = jsonMapper;
    this.localeResolver = localeResolver;
    this.messages = messages;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (SAFE_METHODS.contains(request.getMethod())) {
      chain.doFilter(request, response);
      return;
    }
    if (!"fetch".equals(request.getHeader(HEADER))) {
      reject(request, response, "error.csrf.missing");
      return;
    }
    if (!sameOrigin(request) || "cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"))) {
      reject(request, response, "error.csrf.origin");
      return;
    }
    chain.doFilter(request, response);
  }

  /** 带 Origin 的写请求必须与站点同源；不带 Origin（非浏览器客户端）放行。 */
  private static boolean sameOrigin(HttpServletRequest request) {
    String origin = request.getHeader("Origin");
    if (origin == null || origin.isBlank()) {
      return true;
    }
    String authority = authorityOf(origin);
    if (authority.isEmpty()) {
      return false; // 畸形 Origin 解析不出 authority：拒绝，别让两个空串「相等」蒙混过关
    }
    return authority.equals(authorityOf(request.getHeader("X-Forwarded-Host")))
        || authority.equals(authorityOf(request.getHeader("Host")));
  }

  /** 取 authority（host[:port]，小写）。Origin 取 URL 的 authority；Host/X-Forwarded-Host 本身即 authority（多值取首段）。 */
  private static String authorityOf(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.split(",", 2)[0].trim();
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      try {
        String authority = java.net.URI.create(trimmed).getAuthority();
        trimmed = authority == null ? "" : authority;
      } catch (IllegalArgumentException malformed) {
        return "";
      }
    }
    return trimmed.toLowerCase(Locale.ROOT);
  }

  private void reject(HttpServletRequest request, HttpServletResponse response, String messageKey)
      throws IOException {
    response.setStatus(ErrorCode.FORBIDDEN.httpStatus().value());
    response.setContentType("application/json;charset=UTF-8");
    String message = messages.forLocale(localeResolver.resolveLocale(request), messageKey, null);
    jsonMapper.writeValue(
        response.getWriter(), ErrorEnvelope.of(ErrorCode.FORBIDDEN.code(), message, Map.of(), MDC.get("traceId")));
  }
}
