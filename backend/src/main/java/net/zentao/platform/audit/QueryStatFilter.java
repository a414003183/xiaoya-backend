package net.zentao.platform.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.session.SessionResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 查询埋点（T04/ADR-004 决策 3）：登录后的**读请求**按 账号+资源+耗时 累加，交给
 * {@link AuditQueryStatAccumulator} 周期刷写。
 *
 * <p>为什么在过滤器而不是横切：埋点要的是「请求发生了」，包括 404/403 这类没有控制器执行的结果；
 * 且这里只做一次内存累加（纳秒→毫秒），不碰数据库。
 *
 * <p>口径：只统计 GET（写请求已经有审计行，再进聚合就是重复计数）；资源取 {@code /api/v1/} 之后的
 * 首段路径（{@code /api/v1/products/12} → {@code products}）；未登录请求不计（与限流「匿名不计配额」同法）。
 * 顺序取 LOWEST_PRECEDENCE 附近，让耗时包含前面所有过滤器的开销。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class QueryStatFilter extends OncePerRequestFilter {

  private static final String API_PREFIX = "/api/v1/";

  private final SessionResolver resolver;
  private final AuditQueryStatAccumulator accumulator;

  public QueryStatFilter(SessionResolver resolver, AuditQueryStatAccumulator accumulator) {
    this.resolver = resolver;
    this.accumulator = accumulator;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long startedAt = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      accumulate(request, (System.nanoTime() - startedAt) / 1_000_000);
    }
  }

  private void accumulate(HttpServletRequest request, long millis) {
    if (!"GET".equals(request.getMethod())) {
      return;
    }
    SessionPrincipal principal = resolver.cachedPrincipal(request);
    String resource = resourceOf(request.getRequestURI());
    if (principal == null || resource == null) {
      return;
    }
    accumulator.add(principal.account(), resource, millis);
  }

  /** 资源 = /api/v1/ 之后的首段路径；不在 API 面（静态资源/actuator）返回 null。 */
  private static String resourceOf(String uri) {
    if (uri == null || !uri.startsWith(API_PREFIX)) {
      return null;
    }
    String rest = uri.substring(API_PREFIX.length());
    int slash = rest.indexOf('/');
    String resource = slash < 0 ? rest : rest.substring(0, slash);
    return resource.isBlank() ? null : resource;
  }
}
