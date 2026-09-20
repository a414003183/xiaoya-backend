package net.zentao.platform.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.zentao.platform.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 登录处理器：同账号滑动窗内失败 ≥N 次 → 42901（platform 卡 §4.1）。
 * ponytail: 窗口存内存，重启清零，升级路径=session 表计数列。
 */
@Component
public class LoginHandler {

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
      throw ApiException.rateLimited("登录失败次数过多，请 1 分钟后再试。");
    }
    try {
      AccountView view = gateway.verifyLogin(account, rawPassword);
      timestamps.clear();
      return view;
    } catch (ApiException e) {
      timestamps.addLast(now);
      throw e;
    }
  }
}
