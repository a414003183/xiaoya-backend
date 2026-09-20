package net.zentao.platform.session;

import com.mybatisflex.core.query.QueryColumn;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** session 表读写（platform 卡 §4.1）：登出物理删行；过期行惰性 + 每日清理；touch 滑动续期。 */
@Component
public class SessionRepository {

  static final QueryColumn ID = new QueryColumn("id");
  static final QueryColumn ACCOUNT = new QueryColumn("account");
  static final QueryColumn EXPIRES_AT = new QueryColumn("expires_at");

  private final SessionMapper mapper;

  public SessionRepository(SessionMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(SessionPO po) {
    mapper.insert(po);
  }

  public Optional<SessionPO> findById(String id) {
    return Optional.ofNullable(mapper.selectOneByCondition(ID.eq(id)));
  }

  public void delete(String id) {
    mapper.deleteByCondition(ID.eq(id));
  }

  public void deleteByAccount(String account) {
    mapper.deleteByCondition(ACCOUNT.eq(account));
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
