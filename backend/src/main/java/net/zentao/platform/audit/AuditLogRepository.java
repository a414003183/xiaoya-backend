package net.zentao.platform.audit;

import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * audit_log 表只读访问（B1 §H3 读侧）：流水只追加不改写，故无软删列、无改删路径；
 * 写路径唯一入口是 {@link AuditRecorder}，本类不暴露写方法。
 */
@Component
public class AuditLogRepository {

  private final AuditLogMapper mapper;

  public AuditLogRepository(AuditLogMapper mapper) {
    this.mapper = mapper;
  }

  public List<AuditLogPO> page(QueryWrapper query, int offset, int limit) {
    return mapper.selectListByQuery(query.limit(offset, limit));
  }

  public long countByQuery(QueryWrapper query) {
    return mapper.selectCountByQuery(query);
  }
}
