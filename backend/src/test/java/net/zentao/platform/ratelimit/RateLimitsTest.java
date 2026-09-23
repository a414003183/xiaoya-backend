package net.zentao.platform.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.ratelimit.RateLimits.Scope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T59 端点限流器单测：配额按端点与账号各自独立、窗口滑动后恢复、超限码 42901、阈值 ≤0 即关闭。 */
class RateLimitsTest {

  /** 四个面同阈值，便于逐个验证「另一面/另一账号不受影响」。 */
  private static RateLimits limits(Duration window, int perScope) {
    return new RateLimits(window, 1000, perScope, perScope, perScope, perScope);
  }

  @Test
  @DisplayName("配额按端点独立：打满搜索不影响上传（键 = scope:账号）")
  void quotasArePerScope() {
    RateLimits limits = limits(Duration.ofMinutes(1), 2);
    limits.requireAllowed(Scope.search, "admin");
    limits.requireAllowed(Scope.search, "admin");

    ApiException error = assertThrows(ApiException.class, () -> limits.requireAllowed(Scope.search, "admin"));
    assertEquals(42901, error.errorCode().code());

    limits.requireAllowed(Scope.upload, "admin");
  }

  @Test
  @DisplayName("配额按账号独立：一个账号打满不影响另一个")
  void quotasArePerAccount() {
    RateLimits limits = limits(Duration.ofMinutes(1), 1);
    limits.requireAllowed(Scope.export, "admin");
    assertThrows(ApiException.class, () -> limits.requireAllowed(Scope.export, "admin"));

    limits.requireAllowed(Scope.export, "someone-else");
  }

  @Test
  @DisplayName("窗口滑动后恢复放行（不是永久封禁）")
  void windowSlides() throws Exception {
    RateLimits limits = limits(Duration.ofMillis(80), 1);
    limits.requireAllowed(Scope.search, "admin");
    assertThrows(ApiException.class, () -> limits.requireAllowed(Scope.search, "admin"));

    Thread.sleep(160);
    limits.requireAllowed(Scope.search, "admin");
  }

  @Test
  @DisplayName("阈值 ≤0 = 该端点不限")
  void nonPositiveLimitDisables() {
    RateLimits limits = limits(Duration.ofMinutes(1), 0);
    for (int index = 0; index < 10; index++) {
      limits.requireAllowed(Scope.upload, "admin");
    }
  }
}
