package net.zentao.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

  static final String CSP = "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Referrer-Policy", "same-origin");
    response.setHeader("Content-Security-Policy", CSP);
    chain.doFilter(request, response);
  }
}
