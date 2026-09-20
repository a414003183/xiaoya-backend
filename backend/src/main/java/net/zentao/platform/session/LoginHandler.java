package net.zentao.platform.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.zentao.platform.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 登录处理器：同账号滑动窗内失败 ≥N 次 → 42901（platform 卡 §4.1）。
 * 登录成功/失败/锁定三类安全事件在此落日志（B1 §H3 的观测半边；落库半边见 AuditRecorder）。
 * ponytail: 窗口存内存，重启清零，升级路径=session 表计数列。
 */
@Component
public class LoginHandler {

  private static final Logger log = LoggerFactory.getLogger(LoginHandler.class);

  private final LoginAccountGateway gateway;
  private final Duration window;
  private final int maxFailures;
  private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

  public LoginHandler(
      LoginAccountGateway gateway,
      @Value("${zentao.login.rate-limit-window:60s}") Duration window,
      @Value("${zentao.login.rate-limit-max:10}") int maxFailures) {
    this.gateway = gateway;
    this.window = window;
    this.maxFailures = maxFailures;
  }

  public AccountView login(String account, String rawPassword) {
    Instant now = Instant.now();
    Deque<Instant> timestamps = failures.computeIfAbsent(account, key -> new ConcurrentLinkedDeque<>());
    timestamps.removeIf(occurredAt -> occurredAt.isBefore(now.minus(window)));
    if (timestamps.size() >= maxFailures) {
      log.warn("login blocked account={} failures={}", account, timestamps.size());
      throw ApiException.rateLimited("登录失败次数过多，请 1 分钟后再试。");
    }
    try {
      AccountView view = gateway.verifyLogin(account, rawPassword);
      timestamps.clear();
      log.info("login success account={}", account);
      return view;
    } catch (ApiException e) {
      timestamps.addLast(now);
      log.warn("login failed account={} reason={}", account, e.getMessage());
      throw e;
    }
  }
}
