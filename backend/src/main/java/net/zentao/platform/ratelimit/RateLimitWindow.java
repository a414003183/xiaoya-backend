package net.zentao.platform.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 有界滑动窗计数器：键 = 账号 / 来源 IP / `scope:账号`，值 = 窗口内的发生时刻。
 *
 * <p>两种用法：
 * <ul>
 *   <li><b>失败计数</b>（登录）：{@code blocked} 判定 → 只在失败时 {@code record} → 成功 {@code clear}；
 *   <li><b>请求计数</b>（端点限流）：{@code tryAcquire} 计数与判定在同一把锁内完成。
 * </ul>
 *
 * <p>容量有界（T58 SEC-13）：键数超限时按 **LRU** 淘汰最久没动过的键——喷射不再无限增长内存。
 * 只在计数上锁，不把昂贵操作（BCrypt、SQL）罩进锁里，避免限流自己变成串行化瓶颈。
 *
 * ponytail: 一份表一把锁、内存态、单机各算一份；升级路径 = 每键独立锁 / Caffeine / T76 的 Redis 计数。
 */
public final class RateLimitWindow {

  private static final Logger log = LoggerFactory.getLogger(RateLimitWindow.class);

  private final Duration window;
  private final int maxTracked;
  private final Map<String, Deque<Instant>> table;

  public RateLimitWindow(Duration window, int maxTracked) {
    this.window = window;
    this.maxTracked = maxTracked;
    this.table = new LinkedHashMap<>(16, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
        return size() > maxTracked;
      }
    };
  }

  /** 该键在窗口内是否已达阈值（顺带清掉本键的过期记录）；不计数。 */
  public boolean blocked(String key, int limit, String scope) {
    if (!counted(key) || limit <= 0) {
      return false;
    }
    synchronized (table) {
      Deque<Instant> occurredAt = table.get(key);
      if (occurredAt == null) {
        return false;
      }
      occurredAt.removeIf(at -> at.isBefore(Instant.now().minus(window)));
      if (occurredAt.size() < limit) {
        return false;
      }
      log.warn("rate limited scope={} key={} hits={}", scope, key, occurredAt.size());
      return true;
    }
  }

  /** 判定并计数（同一把锁内，并发请求不会一起挤过阈值）：true = 放行。 */
  public boolean tryAcquire(String key, int limit, String scope) {
    if (!counted(key) || limit <= 0) {
      return true;
    }
    synchronized (table) {
      Deque<Instant> occurredAt = table.get(key);
      if (occurredAt != null) {
        occurredAt.removeIf(at -> at.isBefore(Instant.now().minus(window)));
        if (occurredAt.size() >= limit) {
          log.warn("rate limited scope={} key={} hits={}", scope, key, occurredAt.size());
          return false;
        }
      }
      table.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(Instant.now());
      return true;
    }
  }

  public void record(String key) {
    if (!counted(key)) {
      return;
    }
    synchronized (table) {
      table.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(Instant.now());
    }
  }

  public void clear(String key) {
    if (!counted(key)) {
      return;
    }
    synchronized (table) {
      table.remove(key);
    }
  }

  /** 当前登记的键数（测试断言「容量有界」用）。 */
  public int tracked() {
    synchronized (table) {
      return table.size();
    }
  }

  /** 空键（IP 取不到 / 测试直调）不参与计数。 */
  private static boolean counted(String key) {
    return key != null && !key.isBlank();
  }
}
