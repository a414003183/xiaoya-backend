package net.zentao.platform.ratelimit;

import java.time.Duration;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 端点级限流（T59 SEC-07）：把「一次请求换大量 CPU/IO/存储」的四个面按**账号**计窗，窗口内超限 42901
 * （文案 `common.message.rateLimited`）。
 *
 * <p>键 = `scope:账号`：各端点独立配额，打满搜索不影响上传。口径是**计所有请求**而不是只计失败——
 * 这几个面没有"失败"语义（导出/搜索/上传成功也算消耗），频率本身就是风险。
 *
 * <p>取不到账号的匿名请求不在这里兜（调用方跳过），理由见 {@code SessionResolver#cachedPrincipal}。
 * ponytail: 内存态、单机各算一份；升级路径 = T76 的 Redis 计数（多副本部署必须换）。
 */
@Component
public class RateLimits {

  /** 受控面：名字进日志与配置键（`zentao.ratelimit.<name>-per-window`）。 */
  public enum Scope { changePassword, export, search, upload }

  private final RateLimitWindow window;
  private final Map<Scope, Integer> limits;

  public RateLimits(
      @Value("${zentao.ratelimit.window:60s}") Duration window,
      @Value("${zentao.ratelimit.tracked-keys:10000}") int trackedKeys,
      @Value("${zentao.ratelimit.change-password-per-window:10}") int changePasswordLimit,
      @Value("${zentao.ratelimit.export-per-window:20}") int exportLimit,
      @Value("${zentao.ratelimit.search-per-window:60}") int searchLimit,
      @Value("${zentao.ratelimit.upload-per-window:60}") int uploadLimit) {
    this.window = new RateLimitWindow(window, trackedKeys);
    this.limits = Map.of(
        Scope.changePassword, changePasswordLimit,
        Scope.export, exportLimit,
        Scope.search, searchLimit,
        Scope.upload, uploadLimit);
  }

  /** 计一次该账号在该面上的请求；已到阈值即 42901（≤0 的阈值 = 该端点不限）。 */
  public void requireAllowed(Scope scope, String account) {
    if (!window.tryAcquire(scope.name() + ":" + account, limits.get(scope), scope.name())) {
      throw ApiException.keyed(ErrorCode.RATE_LIMITED, "common.message.rateLimited");
    }
  }
}
