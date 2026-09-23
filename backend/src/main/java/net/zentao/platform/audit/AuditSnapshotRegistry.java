package net.zentao.platform.audit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 对象快照注册表（ADR-004 决策 2，T04）：`objectType → (id → 关键字段快照)`。
 *
 * <p>为什么是注册表而不是框架直接查库：{@code audit} 包不认识 product/account/role 的仓储，
 * 也不该认识（platform 环依赖，ArchUnit 看护）。各域在自己的 {@code *Registrar} 里注册一个纯函数，
 * 依赖方向仍是「域 → audit」，audit 只依赖这个函数式接口。
 *
 * <p>未注册 = 该对象不采集 diff（审计行照落）：T04 建机制，逐域注册随 T10 的分类落地。
 *
 * <p><b>provider 必须返回新快照</b>（每次调用新建一个 Map）：框架把 before 留在内存里，执行后再取一次
 * 比对——返回同一个可变 Map 会让 before/after 恒等，diff 永远为空。
 */
@Component
public class AuditSnapshotRegistry {

  private static final Logger log = LoggerFactory.getLogger(AuditSnapshotRegistry.class);

  private final Map<String, Function<Long, Map<String, Object>>> providers = new ConcurrentHashMap<>();
  private final Map<String, Function<String, Map<String, Object>>> keyedProviders = new ConcurrentHashMap<>();

  /** 注册快照函数；同一 objectType 重复注册 = 覆盖（后注册者为准）。 */
  public void register(String objectType, Function<Long, Map<String, Object>> provider) {
    providers.put(objectType, provider);
  }

  /** 取快照；未注册或对象不存在返回 null（调用方据此跳过 diff，不视为错误）。 */
  public Map<String, Object> snapshot(String objectType, Long id) {
    if (objectType == null || id == null) {
      return null;
    }
    Function<Long, Map<String, Object>> provider = providers.get(objectType);
    if (provider == null) {
      return null;
    }
    try {
      return provider.apply(id);
    } catch (RuntimeException notFoundOrBroken) {
      // provider 自己抛错的语义就是「取不到」：不让 diff 采集把业务写带崩。
      // 但**必须留痕**（T57 口径：宽容处理不许沉默）——否则「没采到 diff」与「provider 坏了」在审计里同貌。
      log.warn("audit snapshot provider failed objectType={} id={}", objectType, id, notFoundOrBroken);
      return null;
    }
  }

  /**
   * 注册「字符串键」快照函数（T10）：setting 的 `key`、menu 的 `nodeKey` 这类**键寻址**资源的主键不是数字，
   * 进不了 {@code object_id}（BIGINT），故单开一张表——键值原样给 provider，比对口径与 {@link #snapshot} 一致。
   */
  public void registerKeyed(String objectType, Function<String, Map<String, Object>> provider) {
    keyedProviders.put(objectType, provider);
  }

  /** 取键寻址快照；未注册/取不到返回 null（同 {@link #snapshot} 口径）。 */
  public Map<String, Object> snapshotKeyed(String objectType, String key) {
    if (objectType == null || key == null || key.isBlank()) {
      return null;
    }
    Function<String, Map<String, Object>> provider = keyedProviders.get(objectType);
    if (provider == null) {
      return null;
    }
    try {
      return provider.apply(key);
    } catch (RuntimeException notFoundOrBroken) {
      log.warn("audit snapshot provider failed objectType={} key={}", objectType, key, notFoundOrBroken);
      return null;
    }
  }
}
