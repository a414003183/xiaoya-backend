package net.zentao.platform.notification;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Map;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.filters.LikePatterns;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/** 通知列表/未读数查询（platform 卡 §7.2：恒注入 recipient=@me；软删行不可见）。 */
@Component
public class NotificationQueryService {

  private static final QueryColumn RECIPIENT = new QueryColumn("recipient");
  private static final QueryColumn DELETED_AT = new QueryColumn("deleted_at");
  private static final QueryColumn TITLE = new QueryColumn("title");
  private static final QueryColumn CONTENT = new QueryColumn("content");

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      java.util.Set.of("type", "objectType", "readAt", "createdAt"),
      java.util.Set.of("id", "createdAt"),
      java.util.Set.of("title", "content"));

  private static final Map<String, String> COLUMNS = Map.of(
      "type", "type",
      "objectType", "object_type",
      "readAt", "read_at",
      "createdAt", "created_at",
      "id", "id");

  private final NotificationRepository repository;

  public NotificationQueryService(NotificationRepository repository) {
    this.repository = repository;
  }

  /** NotificationList 载荷（items + total）。 */
  public record NotificationList(List<NotificationView> items, long total) {}

  public NotificationList page(SessionPrincipal principal, Map<String, String[]> params) {
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition injected = RECIPIENT.eq(principal.account()).and(DELETED_AT.isNull());
    if (filters.q() != null) {
      injected = injected.and(TITLE.likeRaw(LikePatterns.contains(filters.q())).or(CONTENT.likeRaw(LikePatterns.contains(filters.q()))));
    }
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> java.util.Optional.empty(), injected);
    List<NotificationView> items = repository.page(principal.account(), query, filters.offset(), filters.limit())
        .stream()
        .map(NotificationViews::toView)
        .toList();
    // count 与 page 同条件（去 order/limit）
    QueryWrapper countQuery = FilterPredicate.compile(filters.forCount(), COLUMNS::get, value -> java.util.Optional.empty(), injected);
    return new NotificationList(items, repository.countByQuery(countQuery));
  }

  public long unreadCount(SessionPrincipal principal) {
    return repository.countUnread(principal.account());
  }
}
