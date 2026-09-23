package net.zentao.platform.session;

import java.time.Duration;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.ratelimit.RateLimitWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 登录处理器：同账号滑动窗内失败 ≥N 次 → 42901（platform 卡 §4.1）；同一来源 IP 另有独立阈值（T58 SEC-14
 * ——账号维度对"一个 IP 刷一堆不同账号"的撞库不设防，每个账号各自只欠一次）。
 * 登录成功/失败/锁定三类安全事件在此落日志（B1 §H3 的观测半边；落库半边见 AuditRecorder）。
 *
 * <p>两个维度的失败窗都是 {@link RateLimitWindow}（容量有界、只在计数上锁，与端点限流共用一份实现）。
 * 只计**失败**：成功即清空该账号与来源 IP 的窗。
 */
@Component
public class LoginHandler {

  private static final Logger log = LoggerFactory.getLogger(LoginHandler.class);

  private final LoginAccountGateway gateway;
  private final RateLimitWindow accountFailures;
  private final RateLimitWindow ipFailures;
  private final int maxFailures;
  private final int maxFailuresPerIp;

  public LoginHandler(
      LoginAccountGateway gateway,
      @Value("${zentao.login.rate-limit-window:60s}") Duration window,
      @Value("${zentao.login.rate-limit-max:10}") int maxFailures,
      @Value("${zentao.login.rate-limit-max-per-ip:100}") int maxFailuresPerIp,
      @Value("${zentao.login.rate-limit-tracked-keys:10000}") int maxTrackedKeys) {
    this.gateway = gateway;
    this.maxFailures = maxFailures;
    this.maxFailuresPerIp = maxFailuresPerIp;
    this.accountFailures = new RateLimitWindow(window, maxTrackedKeys);
    this.ipFailures = new RateLimitWindow(window, maxTrackedKeys);
  }

  /**
   * 登录校验。{@code clientIp} 取自 {@code request.getRemoteAddr()}——T51 SEC-10 之后它才是可信来源
   * （默认不采信 X-Forwarded-*；代理部署须按 README 启用 Valve）。取不到时不计数：
   * 宁可少一层限流，也不把所有人算成同一个来源。
   */
  public AccountView login(String account, String rawPassword, String clientIp) {
    requireNotLimited(accountFailures, account, maxFailures, "account");
    requireNotLimited(ipFailures, clientIp, maxFailuresPerIp, "ip");
    try {
      AccountView view = gateway.verifyLogin(account, rawPassword);
      accountFailures.clear(account);
      ipFailures.clear(clientIp);
      log.info("login success account={}", account);
      return view;
    } catch (ApiException e) {
      accountFailures.record(account);
      ipFailures.record(clientIp);
      log.warn("login failed account={} reason={}", account, e.getMessage());
      throw e;
    }
  }

  private static void requireNotLimited(RateLimitWindow window, String key, int limit, String scope) {
    if (window.blocked(key, limit, scope)) {
      throw ApiException.keyed(ErrorCode.RATE_LIMITED, "session.login.rateLimited");
    }
  }

  /** 账号失败窗当前登记的键数——测试断言「喷射不撑爆内存」（SEC-13；IP 窗是同款同上限，不另立口）。 */
  int trackedAccounts() {
    return accountFailures.tracked();
  }
}
