package net.zentao.platform.audit;

import com.mybatisflex.core.query.QueryWrapper;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * audit_query_stat 读写（T04）：累加用「单条 SQL 自增」，新行用 INSERT——
 * 并发下 INSERT 撞唯一键（uq_audit_query_stat）不是错误，是对面先建了行，退回去 UPDATE 即可。
 */
@Component
public class AuditQueryStatRepository {

  private static final Logger log = LoggerFactory.getLogger(AuditQueryStatRepository.class);

  private final AuditQueryStatMapper mapper;

  public AuditQueryStatRepository(AuditQueryStatMapper mapper) {
    this.mapper = mapper;
  }

  /** 累加一行（count 次 + millis 毫秒）；失败只告警——聚合是统计口径，不该反向影响请求。 */
  public void add(String account, String resource, LocalDate day, long count, long millis) {
    try {
      if (mapper.addToExisting(account, resource, day, count, millis) == 0) {
        try {
          mapper.insertRow(account, resource, day, count, millis);
        } catch (DuplicateKeyException concurrentInsert) {
          mapper.addToExisting(account, resource, day, count, millis);
        }
      }
    } catch (RuntimeException e) {
      log.warn("audit query stat flush failed account={} resource={} day={}", account, resource, day, e);
    }
  }

  public List<AuditQueryStatPO> page(QueryWrapper query, int offset, int limit) {
    return mapper.selectListByQuery(query.limit(offset, limit));
  }

  public long countByQuery(QueryWrapper query) {
    return mapper.selectCountByQuery(query);
  }
}
