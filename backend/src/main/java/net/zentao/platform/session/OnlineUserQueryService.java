package net.zentao.platform.session;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.filters.LikePatterns;
import org.springframework.stereotype.Component;

/**
 * 在线用户列表（T13 P1-1）：session 表的现存行即在线集——登出（SessionRepository#delete）与
 * 过期（SessionResolver 惰性删行）都物理删行，故「在线」不需要另算状态位。
 *
 * <p>对外 id 就是会话行主键 = token 摘要而非 cookie 值（T51 SEC-03：库里也只有摘要）；current 标出
 * 请求者自己那条，供页面不给出「强退自己」的入口。
 */
@Component
public class OnlineUserQueryService {

  /** filterable/sortable 白名单（契约同集声明，门禁三方对齐）。 */
  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("account"),
      Set.of("account", "createdAt", "lastSeenAt", "expiresAt"),
      Set.of());

  private static final Map<String, String> COLUMNS = Map.of(
      "account", "account",
      "createdAt", "created_at",
      "lastSeenAt", "last_seen_at",
      "expiresAt", "expires_at");

  private static final QueryColumn LAST_SEEN_AT = new QueryColumn("last_seen_at");

  private final SessionRepository repository;

  public OnlineUserQueryService(SessionRepository repository) {
    this.repository = repository;
  }

  /** 在线会话行（platform 卡 §4.1 的 session 表字段；id = 行主键 = token 摘要，userAgent 原样出）。 */
  public record OnlineUserView(String id, String account, String ip, String userAgent, Instant createdAt,
      Instant lastSeenAt, Instant expiresAt, boolean current) {

    static OnlineUserView of(SessionPO po, String currentSessionId) {
      return new OnlineUserView(po.getId(), po.getAccount(), po.getIp(), po.getUserAgent(), po.getCreatedAt(),
          po.getLastSeenAt(), po.getExpiresAt(), po.getId().equals(currentSessionId));
    }
  }

  /** OnlineUserList 载荷（items + total）。 */
  public record OnlineUserList(List<OnlineUserView> items, long total) {}

  /**
   * @param currentSessionId 请求者自己那条会话的 id（{@code SessionPrincipal#sessionId()}）；只用来标 current，
   *     不参与筛选——管理页要看得见自己那条，只是不给强退入口。
   */
  public OnlineUserList page(Map<String, String[]> params, String currentSessionId) {
    Filters filters = Filters.parse(params, REGISTRY);
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> Optional.empty());
    if (filters.sortKeys().isEmpty()) {
      // 缺省按最后活动倒序：管理页要看的是「谁刚刚在动」，不是谁登录得最早
      query = query.orderBy(LAST_SEEN_AT.desc()); // banned-words-ok：MyBatis-Flex 构造器方法名，非请求参数
    }
    List<OnlineUserView> items = repository.page(query, filters.offset(), filters.limit()).stream()
        .map(po -> OnlineUserView.of(po, currentSessionId))
        .toList();
    // count 与 page 同条件（去 order/limit）
    QueryWrapper countQuery = FilterPredicate.compile(filters.forCount(), COLUMNS::get, value -> Optional.empty());
    return new OnlineUserList(items, repository.countByQuery(countQuery));
  }
}
