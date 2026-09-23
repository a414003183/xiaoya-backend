package net.zentao.platform.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 审计日志记录器（B1 §H3，T04 起是**唯一落库入口**）：补全分类/结果/链哈希后追加一行。
 * 写端点由 {@link AuditAspect} 自动调用；无会话上下文的动作（登录）由调用点显式构造行。
 * traceId 取当前 MDC（TraceIdFilter 已写入），调用方只负责业务字段。
 *
 * <p><b>哈希链</b>（ADR-004 决策 4）：{@code hash = sha256(prev_hash + 规范序列化)}，{@code prevHash}
 * 取当前最后一行有哈希的行，链首为 null。插入后**不再改行**（表只有 INSERT/SELECT 授权），
 * 故 id 不参与哈希（自增列插入前拿不到，见 {@link AuditHasher}）。
 *
 * <p>ponytail: 链序靠**单实例同步**（方法级 {@code synchronized}）+「取尾行 → 插入」串行，
 * 上限是多实例部署会各自接自己的链尾，升级路径是 DB 行级锁（{@code SELECT … FOR UPDATE}）或独立审计服务。
 *
 * <p>审计失败只告警不抛出——审计不能反过来打断业务写。
 */
@Component
public class AuditRecorder {

  private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

  /**
   * 批次号（VISION 事项 4 第 5 行「批次 + 逐条摘要」）：{@code batch_id} 是 BIGINT 列，而 trace_id 是
   * 16 字节 hex 串（{@code TraceIdFilter}）塞不进去，故按「epoch 毫秒 × 1000 + 自增」发号——
   * 同一次批量动作的摘要行与（T22 起的）逐条行共用一个号，号本身也能看出批次发生在什么时候。
   *
   * <p>ponytail: 单 JVM 唯一（AtomicLong），上限是同毫秒跨实例可能撞号——与哈希链「单实例保证链序」
   * 同一取舍，升级路径是 DB 序列/雪花号（随 T80 容器化评估）。
   */
  private static final AtomicLong BATCH_SEQ = new AtomicLong(System.currentTimeMillis() * 1000);

  /** 新的批次号（见字段注释）。 */
  public static long nextBatchId() {
    return BATCH_SEQ.incrementAndGet();
  }

  private final AuditLogMapper mapper;
  private final AuditCatalog catalog;
  private final AuditLogRepository repository;
  private final JsonMapper jsonMapper;

  public AuditRecorder(AuditLogMapper mapper, AuditCatalog catalog, AuditLogRepository repository,
      JsonMapper jsonMapper) {
    this.mapper = mapper;
    this.catalog = catalog;
    this.repository = repository;
    this.jsonMapper = jsonMapper;
  }

  /** 追加一行（唯一落库入口）：分类/结果/时间/链哈希在此补全，调用方只填业务字段。 */
  public synchronized void record(AuditLogPO row) {
    try {
      if (row.getCategory() == null) {
        row.setCategory(catalog.categoryOf(row.getAction()).value());
      }
      if (row.getResult() == null) {
        row.setResult(AuditResult.SUCCESS.value());
      }
      if (row.getCreatedAt() == null) {
        // 库列是秒精度 TIMESTAMP：写入前截断，哈希用同一个值算（H2 微秒 / MySQL 秒两侧一致）
        row.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
      }
      row.setTraceId(MDC.get("traceId"));
      row.setPrevHash(repository.lastHashed().map(AuditLogPO::getHash).orElse(null));
      row.setHash(AuditHasher.hash(row.getPrevHash(), row));
      mapper.insert(row);
    } catch (RuntimeException e) {
      log.error("audit write failed action={} account={}", row.getAction(), row.getAccount(), e);
    }
  }

  /** 便捷入口：成功结果的简单动作（无 diff/批次/快照）。 */
  public void record(String account, String action, String objectType, Long objectId, String detail, String ip) {
    AuditLogPO row = new AuditLogPO();
    row.setAccount(account);
    row.setAction(action);
    row.setObjectType(objectType);
    row.setObjectId(objectId);
    row.setDetail(detail);
    row.setIp(ip);
    record(row);
  }

  /**
   * 便捷入口：权限拒绝（VISION 事项 4 的 {@code result=denied} 位，T10 接线）。
   * 拒绝发生在拦截器里——处理器根本没执行，横切看不到它，故由拦截器显式记一行：
   * 「谁想做什么而没权限」与失败行同等重要，且它是唯一能回答「有人在探权限面」的记录。
   */
  public void recordDenied(String account, String action, String detail, String reason, String ip,
      String userAgent) {
    AuditLogPO row = new AuditLogPO();
    row.setAccount(account);
    row.setAction(action);
    row.setDetail(detail);
    row.setResult(AuditResult.DENIED.value());
    row.setReason(reason);
    row.setIp(ip);
    row.setUa(UserAgents.truncate(userAgent));
    row.setDevice(UserAgents.device(userAgent));
    record(row);
  }

  /**
   * 便捷入口：导出/下载类（VISION 事项 4 第 6 行「筛选条件、字段、条数、文件哈希、下载 IP」，T10）。
   * 这些端点的「对象」是文件而不是资源行，故对象位由调用点给，详细口径全进 {@code extra}。
   */
  public void recordDownload(String account, String action, String objectType, Long objectId, String detail,
      String ip, String userAgent, Map<String, Object> extra) {
    AuditLogPO row = new AuditLogPO();
    row.setAccount(account);
    row.setAction(action);
    row.setObjectType(objectType);
    row.setObjectId(objectId);
    row.setDetail(detail);
    row.setIp(ip);
    row.setUa(UserAgents.truncate(userAgent));
    row.setDevice(UserAgents.device(userAgent));
    row.setResult(AuditResult.SUCCESS.value());
    row.setExtra(extra == null ? null : jsonMapper.writeValueAsString(extra));
    record(row);
  }

  /**
   * 便捷入口：登录/登出这类「无会话主体 + 结果由调用点判定」的动作，顺带落 UA 原文与设备摘要。
   * T04 之前登录行只有 account/action/detail/ip；V43 起补 result/reason/ua/device（VISION 事项 4 第 1 行）。
   */
  public void recordAuth(String account, String action, String result, String reason, String detail, String ip,
      String userAgent) {
    AuditLogPO row = new AuditLogPO();
    row.setAccount(account);
    row.setAction(action);
    row.setResult(result);
    row.setReason(reason);
    row.setDetail(detail);
    row.setIp(ip);
    row.setUa(UserAgents.truncate(userAgent));
    row.setDevice(UserAgents.device(userAgent));
    record(row);
  }
}
