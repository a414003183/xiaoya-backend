package net.zentao.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import net.zentao.platform.audit.AuditRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.rbac.PrivilegeChecker;
import net.zentao.platform.session.AccountView;
import net.zentao.platform.session.LoginHandler;
import net.zentao.platform.session.LoginAccountGateway;
import net.zentao.platform.session.LoginRequest;
import net.zentao.platform.session.LogoutHandler;
import net.zentao.platform.session.MeView;
import net.zentao.platform.session.SessionPO;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.session.SessionResolver;
import net.zentao.platform.session.SessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录 / 登出 / 当前账号（platform 卡 §5 前三行；T-1 真实现）。 */
@RestController
@RequestMapping("/api/v1")
public class SessionController {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final LoginHandler loginHandler;
  private final LogoutHandler logoutHandler;
  private final LoginAccountGateway gateway;
  private final SessionRepository repository;
  private final SessionResolver resolver;
  private final PrivilegeChecker privilegeChecker;
  private final AuditRecorder auditRecorder;
  private final Duration ttl;
  private final boolean secureCookie;

  public SessionController(
      LoginHandler loginHandler,
      LogoutHandler logoutHandler,
      LoginAccountGateway gateway,
      SessionRepository repository,
      SessionResolver resolver,
      PrivilegeChecker privilegeChecker,
      AuditRecorder auditRecorder,
      @Value("${zentao.session.ttl:7d}") Duration ttl,
      @Value("${zentao.session.secure-cookie:false}") boolean secureCookie) {
    this.loginHandler = loginHandler;
    this.logoutHandler = logoutHandler;
    this.gateway = gateway;
    this.repository = repository;
    this.resolver = resolver;
    this.privilegeChecker = privilegeChecker;
    this.auditRecorder = auditRecorder;
    this.ttl = ttl;
    this.secureCookie = secureCookie;
  }

  @PostMapping("/session")
  public DataEnvelope<AccountView> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    AccountView account = loginHandler.login(request.account(), request.password());
    String token = HexFormat.of().formatHex(random32());
    Instant now = Instant.now();
    SessionPO po = new SessionPO();
    po.setId(token);
    po.setAccountId(account.id());
    po.setAccount(account.account());
    po.setCreatedAt(now);
    po.setExpiresAt(now.plus(ttl));
    po.setLastSeenAt(now);
    po.setIp(httpRequest.getRemoteAddr());
    po.setUserAgent(httpRequest.getHeader("User-Agent"));
    repository.insert(po);
    httpResponse.addHeader("Set-Cookie", sessionCookie(token, ttl, secureCookie).toString());
    // B1 §H3：登录是无会话的写请求，AuditAspect 按设计不审（主体未知），故在此显式记账。
    auditRecorder.record(account.account(), "login", null, null, "POST /api/v1/session", httpRequest.getRemoteAddr());
    return DataEnvelope.of(account);
  }

  @DeleteMapping("/session")
  public DataEnvelope<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    String token = resolver.currentToken(request);
    logoutHandler.logout(token);
    response.addHeader("Set-Cookie", sessionCookie("", Duration.ZERO, secureCookie).toString());
    return DataEnvelope.empty();
  }

  @GetMapping("/me")
  public DataEnvelope<MeView> getMe(HttpServletRequest request) {
    SessionPrincipal principal = resolver.resolve(request);
    AccountView account = gateway.view(principal.accountId());
    if (account == null) {
      throw ApiException.unauthenticated("未登录或会话已过期。");
    }
    return DataEnvelope.of(new MeView(account, privilegeChecker.privilegesOf(principal)));
  }

  /** secure 属性随 profile：prod 走 HTTPS（单 jar 直连）必须开，dev 走 http 开了浏览器不回种 cookie。 */
  static ResponseCookie sessionCookie(String token, Duration maxAge, boolean secure) {
    return ResponseCookie.from(SessionResolver.COOKIE_NAME, token)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/")
        .maxAge(maxAge)
        .build();
  }

  private static byte[] random32() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return bytes;
  }
}
