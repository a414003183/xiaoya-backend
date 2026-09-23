package net.zentao.platform.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 从请求 cookie 解析当前会话（platform 卡 §4.1）：
 * 过期行删并 40101；touch 滑动续期（距上次写库 &gt;1h 才顺延 expiresAt/lastSeenAt）。
 * 另设绝对过期上限（06 对齐 A7-2）：会话自 createdAt 起最长存活 max-lifetime（默认 30d），
 * 滑动续期不得突破——续期值截断到该上限，超限请求走与过期相同的删行 + 40101 路径。
 * 解析结果按请求缓存，拦截器与控制器同请求共享，不重复查库。
 *
 * <p>T51 SEC-03：库里存的是 cookie token 的 sha256（{@code SessionRepository#findByToken} 换算），
 * 故明文只在 cookie 与内存里过一手；对外要"本条会话"一律用 {@link SessionPrincipal#sessionId()}。
 */
@Component
public class SessionResolver {

  public static final String COOKIE_NAME = "ZT_SESSION";
  private static final String PRINCIPAL_ATTRIBUTE = SessionPrincipal.class.getName();
  private static final Duration TOUCH_INTERVAL = Duration.ofHours(1);

  private final SessionRepository repository;
  private final Duration ttl;
  private final Duration maxLifetime;

  public SessionResolver(
      SessionRepository repository,
      @Value("${zentao.session.ttl:7d}") Duration ttl,
      @Value("${zentao.session.max-lifetime:30d}") Duration maxLifetime) {
    this.repository = repository;
    this.ttl = ttl;
    this.maxLifetime = maxLifetime;
  }

  public SessionPrincipal resolve(HttpServletRequest request) {
    SessionPrincipal cached = cachedPrincipal(request);
    if (cached != null) {
      return cached;
    }
    SessionPO session = repository.findByToken(currentToken(request))
        .orElseThrow(() -> ApiException.keyed(ErrorCode.UNAUTHENTICATED, "error.session.expired"));
    Instant now = Instant.now();
    Instant absoluteDeadline = session.getCreatedAt().plus(maxLifetime);
    if (now.isAfter(session.getExpiresAt()) || now.isAfter(absoluteDeadline)) {
      repository.delete(session.getId());
      throw ApiException.keyed(ErrorCode.UNAUTHENTICATED, "error.session.expired");
    }
    if (session.getLastSeenAt() == null || session.getLastSeenAt().plus(TOUCH_INTERVAL).isBefore(now)) {
      Instant renewed = now.plus(ttl);
      repository.touch(session.getId(), now, renewed.isAfter(absoluteDeadline) ? absoluteDeadline : renewed);
    }
    SessionPrincipal principal = new SessionPrincipal(session.getId(), session.getAccountId(), session.getAccount());
    request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
    return principal;
  }

  /**
   * 已解析过的会话：只读请求属性里的缓存，**不查库、不抛错**，没有则返回 null（T59）。
   *
   * <p>给横切/工具类取账号用——拦截器（{@code PrivilegeInterceptor}）对非匿名端点已解析过一次，
   * 这里拿的是同一份，不产生额外查询。**匿名请求会拿到 null**：调用方须自行跳过（不要退化成查库或抛 40101），
   * 口径是"匿名请求不计入任何账号的配额"。
   */
  public SessionPrincipal cachedPrincipal(HttpServletRequest request) {
    Object cached = request.getAttribute(PRINCIPAL_ATTRIBUTE);
    return cached instanceof SessionPrincipal principal ? principal : null;
  }

  /** 取 cookie 里的原始 token：**只在本类内换算摘要用**（对外一律经 SessionPrincipal#sessionId）。 */
  private String currentToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
          return cookie.getValue();
        }
      }
    }
    throw ApiException.keyed(ErrorCode.UNAUTHENTICATED, "error.session.expired");
  }
}
