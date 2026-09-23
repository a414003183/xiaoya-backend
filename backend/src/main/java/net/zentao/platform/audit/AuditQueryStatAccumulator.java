package net.zentao.platform.audit;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import net.zentao.platform.meta.SettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 查询聚合累加器（T04/ADR-004 决策 3）：请求线程只做一次内存累加，落库交给定时刷写。
 *
 * <p>为什么不每请求写库：审计的 query 类是**高频低价值**面（每次翻页一行会把主表撑爆），
 * 内存累加 + 周期 upsert 把写入摊成「每 账号×资源×天 一行」。
 *
 * <p>采样率：设置项 {@code audit.query-sample-rate}（默认 100，0 = 关）在每次刷写时刷新一次
 * （每分钟一次读库，不是每请求一次）；采样未命中的请求直接丢弃。
 *
 * <p>ponytail: 内存累加的上限是**进程重启丢一个刷写周期的计数**（默认 ≤60s 的窗口），
 * 升级路径是把累加器换成 Redis INCR（T76）或写事件表后聚合。
 */
@Component
public class AuditQueryStatAccumulator {

  private static final Logger log = LoggerFactory.getLogger(AuditQueryStatAccumulator.class);

  /** 累加键：账号 + 资源 + 聚合日（UTC，与库列口径一致）。 */
  private record Key(String account, String resource, LocalDate day) {}

  /** 一次刷写的载荷（从 pending 取出后就不再变动）。 */
  private record Batch(String account, String resource, LocalDate day, long count, long millis) {}

  private static final class Slot {
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong millis = new AtomicLong();
  }

  private final Map<Key, Slot> pending = new ConcurrentHashMap<>();
  private final AuditQueryStatRepository repository;
  private final SettingRepository settings;
  private final JsonMapper jsonMapper;
  private final int defaultSampleRate;

  /** 采样率（百分比）：刷写时从设置刷新，请求线程只读。 */
  private volatile int sampleRate;

  public AuditQueryStatAccumulator(AuditQueryStatRepository repository, SettingRepository settings,
      JsonMapper jsonMapper, @Value("${zentao.audit.query-sample-rate:100}") int defaultSampleRate) {
    this.repository = repository;
    this.settings = settings;
    this.jsonMapper = jsonMapper;
    this.defaultSampleRate = defaultSampleRate;
    this.sampleRate = defaultSampleRate;
  }

  /** 请求结束时的累加（认证请求的读面，见 {@code QueryStatFilter}）。 */
  public void add(String account, String resource, long millis) {
    int rate = sampleRate;
    if (rate <= 0) {
      return;
    }
    if (rate < 100 && ThreadLocalRandom.current().nextInt(100) >= rate) {
      return;
    }
    Slot slot = pending.computeIfAbsent(new Key(account, resource, LocalDate.now(ZoneOffset.UTC)), key -> new Slot());
    slot.count.incrementAndGet();
    slot.millis.addAndGet(Math.max(0, millis));
  }

  /** 周期刷写：取出即清（同一窗口的累加不丢），逐行 upsert。 */
  @Scheduled(fixedDelayString = "${zentao.audit.query-flush-interval:60000}")
  public void flush() {
    refreshSampleRate();
    List<Batch> batches = drain();
    for (Batch batch : batches) {
      repository.add(batch.account(), batch.resource(), batch.day(), batch.count(), batch.millis());
    }
  }

  /** 取出并清空当前窗口（测试直接调用它做同步断言）。 */
  List<Batch> drain() {
    List<Batch> batches = new ArrayList<>();
    for (Iterator<Map.Entry<Key, Slot>> it = pending.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<Key, Slot> entry = it.next();
      Slot slot = entry.getValue();
      batches.add(new Batch(entry.getKey().account(), entry.getKey().resource(), entry.getKey().day(),
          slot.count.getAndSet(0), slot.millis.getAndSet(0)));
      it.remove();
    }
    return batches;
  }

  private void refreshSampleRate() {
    Integer configured = settings.findSystem("audit", "query-sample-rate")
        .map(po -> parseRate(po.getItemValue()))
        .orElse(null);
    sampleRate = configured == null ? defaultSampleRate : configured;
  }

  private Integer parseRate(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return jsonMapper.readTree(json).asInt(defaultSampleRate);
    } catch (RuntimeException broken) {
      log.warn("audit.query-sample-rate 值不可解析：{}", json);
      return null;
    }
  }
}
