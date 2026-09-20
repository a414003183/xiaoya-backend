package net.zentao.platform.audit;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * 审计日志记录器（B1 §H3）：唯一的落库入口。写端点由 {@link AuditAspect} 自动调用；
 * 无会话上下文的动作（登录）由调用点显式调用。traceId 取当前 MDC（TraceIdFilter 已写入），
 * 调用方只负责业务字段。
 */
@Component
public class AuditRecorder {

  private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

  private final AuditLogMapper mapper;

  public AuditRecorder(AuditLogMapper mapper) {
    this.mapper = mapper;
  }

  /** 追加一行审计。审计失败只告警不抛出——审计不能反过来打断业务写。 */
  public void record(String account, String action, String objectType, Long objectId, String detail, String ip) {
    try {
      AuditLogPO po = new AuditLogPO();
      po.setAccount(account);
      po.setAction(action);
      po.setObjectType(objectType);
      po.setObjectId(objectId);
      po.setDetail(detail);
      po.setIp(ip);
      po.setTraceId(MDC.get("traceId"));
      po.setCreatedAt(Instant.now());
      mapper.insert(po);
    } catch (RuntimeException e) {
      log.error("audit write failed action={} account={}", action, account, e);
    }
  }
}
