package net.zentao.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import net.zentao.platform.audit.Audit;
import net.zentao.platform.audit.AuditRecorder;
import net.zentao.platform.audit.AuditResult;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.rbac.Anonymous;
import net.zentao.platform.rbac.PrivilegeChecker;
import net.zentao.platform.session.AccountView;
import net.zentao.platform.session.LoginHandler;
import net.zentao.platform.session.LoginAccountGateway;
import net.zentao.platform.session.LoginRequest;
import net.zentao.platform.session.LogoutHandler;
import net.zentao.platform.session.MeView;
import net.zentao.platform.session.SessionApi;
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
  private final SessionApi sessionApi;
  private final SessionResolver resolver;
  private final PrivilegeChecker privilegeChecker;
  private final AuditRecorder auditRecorder;
  private final MessageResolver messages;
  private final Duration ttl;
  private final boolean secureCookie;

  public SessionController(
      LoginHandler loginHandler,
      LogoutHandler logoutHandler,
      LoginAccountGateway gateway,
      SessionRepository repository,
      SessionApi sessionApi,
      SessionResolver resolver,
      PrivilegeChecker privilegeChecker,
      AuditRecorder auditRecorder,
      MessageResolver messages,
      @Value("${zentao.session.ttl:7d}") Duration ttl,
      @Value("${zentao.session.secure-cookie:false}") boolean secureCookie) {
    this.loginHandler = loginHandler;
    this.logoutHandler = logoutHandler;
    this.gateway = gateway;
    this.repository = repository;
    this.sessionApi = sessionApi;
    this.resolver = resolver;
    this.privilegeChecker = privilegeChecker;
    this.auditRecorder = auditRecorder;
    this.messages = messages;
    this.ttl = ttl;
    this.secureCookie = secureCookie;
  }

  @PostMapping("/session")
  @Anonymous("登录：登录前必然无会话；暴力破解防线在 LoginHandler 的限流 + 账号锁定内")
  public DataEnvelope<AccountView> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    // L1：登录失败也记账，否则「登录日志」只剩成功记录，看不出谁在什么 IP 试过而没进来。
    // detail 只记失败原因（网关文案本身不区分「账号不存在/口令错」），口令永不落库。
    AccountView account;
    try {
      // T58 SEC-14：来源 IP 一并交给限流器（账号维度之外的第二道窗）
      account = loginHandler.login(request.account(), request.password(), httpRequest.getRemoteAddr());
    } catch (ApiException failure) {
      // 落库原因走默认语言（审计跨语言可读，见 MessageResolver#reasonOf）。
      // T04：reason 与 detail 同值——reason 是新列（给 API 消费方），detail 沿用旧口径（登录日志页渲染的就是它），
      // 改 detail 会让既有页面的「失败原因」列变空。
      auditRecorder.recordAuth(request.account(), "login-failed", AuditResult.FAIL.value(), messages.reasonOf(failure),
          messages.reasonOf(failure), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
      throw failure;
    }
    String token = HexFormat.of().formatHex(random32());
    Instant now = Instant.now();
    SessionPO po = new SessionPO();
    po.setAccountId(account.id());
    po.setAccount(account.account());
    po.setCreatedAt(now);
    po.setExpiresAt(now.plus(ttl));
    po.setLastSeenAt(now);
    po.setIp(httpRequest.getRemoteAddr());
    po.setUserAgent(httpRequest.getHeader("User-Agent"));
    // 明文 token 只用于种 cookie；落库的是它的 sha256（T51 SEC-03，换算在仓库入口）
    repository.insert(po, token);
    // T51 SEC-18：同账号会话超上限时踢最旧的（插行之后裁，边界只此一处）
    sessionApi.enforceConcurrentLimit(account.id());
    httpResponse.addHeader("Set-Cookie", sessionCookie(token, ttl, secureCookie).toString());
    // B1 §H3：登录是无会话的写请求，AuditAspect 按设计不审（主体未知），故在此显式记账（T04 起带 UA/设备）。
    auditRecorder.recordAuth(account.account(), "login", AuditResult.SUCCESS.value(), null, "POST /api/v1/session",
        httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
    return DataEnvelope.of(account);
  }

  @DeleteMapping("/session")
  @Audit(action = "logout", objectType = "account")
  public DataEnvelope<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    logoutHandler.logout(resolver.resolve(request).sessionId());
    response.addHeader("Set-Cookie", sessionCookie("", Duration.ZERO, secureCookie).toString());
    return DataEnvelope.empty();
  }

  @GetMapping("/me")
  public DataEnvelope<MeView> getMe(HttpServletRequest request) {
    SessionPrincipal principal = resolver.resolve(request);
    AccountView account = gateway.view(principal.accountId());
    if (account == null) {
      throw ApiException.keyed(ErrorCode.UNAUTHENTICATED, "error.session.expired");
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
