package net.zentao.platform.session;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * session 表读写（platform 卡 §4.1；T51 SEC-03）：**id = cookie token 的 sha256**，凭据不落库；
 * 登出物理删行；过期行惰性 + 每日清理；touch 滑动续期。
 */
@Component
public class SessionRepository {

  static final QueryColumn ID = new QueryColumn("id");
  static final QueryColumn ACCOUNT = new QueryColumn("account");
  static final QueryColumn ACCOUNT_ID = new QueryColumn("account_id");
  static final QueryColumn EXPIRES_AT = new QueryColumn("expires_at");
  private static final QueryColumn LAST_SEEN_AT = new QueryColumn("last_seen_at");

  private final SessionMapper mapper;

  public SessionRepository(SessionMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * 换算摘要在这里而不是调用点（T13 P1-1 → T51 SEC-03）：写入口只此一个，凭据不可能漏算，也不可能被
   * 顺带写进库——调用点仍持有明文 token（种 cookie 要用），但 PO 自始至终拿不到它，库里只有摘要。
   */
  public void insert(SessionPO po, String token) {
    po.setId(SessionTokenHash.of(token));
    mapper.insert(po);
  }

  public Optional<SessionPO> findById(String id) {
    return Optional.ofNullable(mapper.selectOneByCondition(ID.eq(id)));
  }

  /** 按 cookie 值查会话：先算摘要再等值查主键（解析链上唯一的明文入口）。 */
  public Optional<SessionPO> findByToken(String token) {
    return findById(SessionTokenHash.of(token));
  }

  /** 在线用户列表分页（T13 P1-1）。 */
  public List<SessionPO> page(QueryWrapper query, int offset, int limit) {
    return mapper.selectListByQuery(query.limit(offset, limit));
  }

  public long countByQuery(QueryWrapper query) {
    return mapper.selectCountByQuery(query);
  }

  /** 该账号的全部会话，按最后活动升序（T51 SEC-18：并发上限从最久没动的开始踢）。 */
  public List<SessionPO> listByAccount(long accountId) {
    QueryWrapper query = QueryWrapper.create().where(ACCOUNT_ID.eq(accountId));
    query = query.orderBy(LAST_SEEN_AT.asc()); // banned-words-ok：MyBatis-Flex 构造器方法名，非请求参数
    return mapper.selectListByQuery(query);
  }

  public void delete(String id) {
    mapper.deleteByCondition(ID.eq(id));
  }

  public void deleteByAccount(String account) {
    mapper.deleteByCondition(ACCOUNT.eq(account));
  }

  /** 除保留那条外全删（T51 SEC-04：本人改密留当前会话，其他设备退）。 */
  public int deleteByAccountExcept(String account, String keepSessionId) {
    return mapper.deleteByCondition(ACCOUNT.eq(account).and(ID.ne(keepSessionId)));
  }

  /** 过期会话行（每日调度清理）。 */
  public int deleteExpiredBefore(Instant now) {
    return mapper.deleteByCondition(EXPIRES_AT.le(java.sql.Timestamp.from(now)));
  }

  public void touch(String id, Instant lastSeenAt, Instant expiresAt) {
    SessionPO patch = new SessionPO();
    patch.setId(id);
    patch.setLastSeenAt(lastSeenAt);
    patch.setExpiresAt(expiresAt);
    mapper.update(patch);
  }
}
