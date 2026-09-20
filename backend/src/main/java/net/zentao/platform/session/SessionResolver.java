package net.zentao.platform.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import net.zentao.platform.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 从请求 cookie 解析当前会话（platform 卡 §4.1）：
 * 过期行删并 40101；touch 滑动续期（距上次写库 &gt;1h 才顺延 expiresAt/lastSeenAt）。
 * 另设绝对过期上限（06 对齐 A7-2）：会话自 createdAt 起最长存活 max-lifetime（默认 30d），
 * 滑动续期不得突破——续期值截断到该上限，超限请求走与过期相同的删行 + 40101 路径。
 * 解析结果按请求缓存，拦截器与控制器同请求共享，不重复查库。
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
    Object cached = request.getAttribute(PRINCIPAL_ATTRIBUTE);
    if (cached instanceof SessionPrincipal principal) {
      return principal;
    }
    SessionPO session = repository.findById(currentToken(request))
        .orElseThrow(() -> ApiException.unauthenticated("未登录或会话已过期。"));
    Instant now = Instant.now();
    Instant absoluteDeadline = session.getCreatedAt().plus(maxLifetime);
    if (now.isAfter(session.getExpiresAt()) || now.isAfter(absoluteDeadline)) {
      repository.delete(session.getId());
      throw ApiException.unauthenticated("未登录或会话已过期。");
    }
    if (session.getLastSeenAt() == null || session.getLastSeenAt().plus(TOUCH_INTERVAL).isBefore(now)) {
      Instant renewed = now.plus(ttl);
      repository.touch(session.getId(), now, renewed.isAfter(absoluteDeadline) ? absoluteDeadline : renewed);
    }
    SessionPrincipal principal = new SessionPrincipal(session.getAccountId(), session.getAccount());
    request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
    return principal;
  }

  /** 供登出/解析取原始 token（登出后 cookie 立即失效）。 */
  public String currentToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
          return cookie.getValue();
        }
      }
    }
    throw ApiException.unauthenticated("未登录或会话已过期。");
  }
}
