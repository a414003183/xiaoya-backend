package net.zentao.platform.audit;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * audit_log 表只读访问（B1 §H3 读侧）：流水只追加不改写，故无软删列、无改删路径；
 * 写路径唯一入口是 {@link AuditRecorder}，本类不暴露写方法（T04 的链尾读取也在此，只读）。
 */
@Component
public class AuditLogRepository {

  private static final QueryColumn ID = new QueryColumn("id");
  private static final QueryColumn HASH = new QueryColumn("hash");

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

  public Optional<AuditLogPO> findById(long id) {
    return Optional.ofNullable(mapper.selectOneById(id));
  }

  /** 链尾：最后一行有哈希的行（{@link AuditRecorder} 取它的 hash 当新行的 prev_hash）。 */
  public Optional<AuditLogPO> lastHashed() {
    return Optional.ofNullable(mapper.selectOneByQuery(
        QueryWrapper.create().where(HASH.isNotNull()).orderBy(ID.desc())  // banned-words-ok：MyBatis-Flex 构造器方法名，非请求参数
            .limit(1)));
  }

  /** 校验扫描面：有哈希的行按 id 升序（含边界）；取 limit 行，多取一行由调用方判断是否截断。 */
  public List<AuditLogPO> hashedRange(Long fromId, Long toId, int limit) {
    QueryWrapper query = QueryWrapper.create().where(HASH.isNotNull());
    if (fromId != null) {
      query = query.and(ID.ge(fromId));
    }
    if (toId != null) {
      query = query.and(ID.le(toId));
    }
    return mapper.selectListByQuery(query.orderBy(ID.asc()).limit(limit));  // banned-words-ok：MyBatis-Flex 构造器方法名，非请求参数
  }

  /** 无哈希的历史行数（V43 之前写入；不参与校验，只做口径披露）。 */
  public long countUnhashed() {
    return mapper.selectCountByQuery(QueryWrapper.create().where(HASH.isNull()));
  }
}
