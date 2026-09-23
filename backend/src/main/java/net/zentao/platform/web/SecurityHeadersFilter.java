package net.zentao.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 安全响应头（06 对齐 A7-1）：全响应统一四个头，SSE/文件/错误响应一视同仁。
 *
 * <p>注册在最外层（HIGHEST_PRECEDENCE，先于 Csrf 与业务链），故 40301 这类过滤器阶段直接写出的拒绝响应也带头。
 *
 * <p>CSP 的 {@code style-src 'unsafe-inline'} 是必需的：antd 6 cssVar 模式在运行时注入内联样式变量，禁掉会整站白屏；
 * {@code script-src} 不放开——Vite 产物无内联脚本，放开等于给 XSS 开后门（放宽只允许 style 一个方向）。
 *
 * <p>T61/SEC-10 余项：CSP 补 {@code frame-ancestors 'none'}（与 X-Frame-Options: DENY 同口径，新浏览器只认前者）
 * 与 {@code form-action 'self'}（注入出的表单提交不到站外——前端 DOMPurify 同步禁表单族，两层各挡一半）。
 *
 * <p>HSTS（{@code Strict-Transport-Security}）**只在 HTTPS 请求上发**：判据是 {@link HttpServletRequest#isSecure()}
 * （反代部署由 T51 的 RemoteIpValve 按 {@code X-Forwarded-Proto} 采信后置位）——纯 HTTP 的 localhost 开发态绝不发，
 * 否则浏览器会把开发者钉死 HTTPS 半年且难恢复。max-age 走 {@code zentao.security.hsts-max-age}（秒，0=关闭）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

  static final String CSP =
      "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; form-action 'self'";

  private final long hstsMaxAge;

  public SecurityHeadersFilter(@Value("${zentao.security.hsts-max-age:31536000}") long hstsMaxAge) {
    this.hstsMaxAge = hstsMaxAge;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Referrer-Policy", "same-origin");
    response.setHeader("Content-Security-Policy", CSP);
    if (hstsMaxAge > 0 && request.isSecure()) {
      response.setHeader("Strict-Transport-Security", "max-age=" + hstsMaxAge);
    }
    chain.doFilter(request, response);
  }
}
