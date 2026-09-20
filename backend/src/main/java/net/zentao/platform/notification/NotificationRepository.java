package net.zentao.platform.notification;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** notification 表读写（platform 卡 §3.4：查询恒注入 recipient；软删行不可见）。 */
@Component
public class NotificationRepository {

  static final QueryColumn ID = new QueryColumn("id");
  static final QueryColumn RECIPIENT = new QueryColumn("recipient");
  static final QueryColumn READ_AT = new QueryColumn("read_at");
  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");

  private final NotificationMapper mapper;

  public NotificationRepository(NotificationMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(NotificationPO po) {
    mapper.insert(po);
  }

  public Optional<NotificationPO> findById(long id) {
    return Optional.ofNullable(mapper.selectOneByCondition(ID.eq(id).and(DELETED_AT.isNull())));
  }

  public long countUnread(String recipient) {
    return mapper.selectCountByQuery(QueryWrapper.create()
        .where(RECIPIENT.eq(recipient).and(READ_AT.isNull()).and(DELETED_AT.isNull())));
  }

  /** 断线补发（platform 卡 §5.1）：该 id 之后的本人通知，不论已读与否。 */
  public List<NotificationPO> findAfter(String recipient, long afterId, int limit) {
    return mapper.selectListByQuery(QueryWrapper.create()
        .where(RECIPIENT.eq(recipient).and(ID.gt(afterId)).and(DELETED_AT.isNull()))
        .orderBy(ID.asc())  // banned-words-ok：MyBatis-Flex 构造器方法名
        .limit(limit));
  }

  public void update(NotificationPO po) {
    mapper.update(po);
  }

  public List<NotificationPO> page(String recipient, QueryWrapper query, int offset, int limit) {
    return mapper.selectListByQuery(query.limit(offset, limit));
  }

  public long countByQuery(QueryWrapper query) {
    return mapper.selectCountByQuery(query);
  }
}
