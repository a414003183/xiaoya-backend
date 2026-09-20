package net.zentao.platform.audit;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import org.springframework.stereotype.Component;

/**
 * 审计日志查询（B1 §H3 读侧；列表 DSL 见 03 §3）：流水只读，缺省 created_at/id 倒序。
 * 字段取值：account/action/objectType/objectId 等值或逗号 IN，createdAt 区间（含当日），
 * q 关键词 LIKE account/action；未注册字段过滤/排序 → 40001。
 */
@Component
public class AuditLogQueryService {

  /** filterable/sortable/searchable 白名单（契约同集声明，门禁三方对齐）。 */
  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("account", "action", "objectType", "objectId", "createdAt"),
      Set.of("id", "createdAt"),
      Set.of("account", "action"));

  private static final Map<String, String> COLUMNS = Map.of(
      "id", "id",
      "account", "account",
      "action", "action",
      "objectType", "object_type",
      "objectId", "object_id",
      "createdAt", "created_at");

  private static final QueryColumn ID = new QueryColumn("id");
  private static final QueryColumn ACCOUNT = new QueryColumn("account");
  private static final QueryColumn ACTION = new QueryColumn("action");
  private static final QueryColumn CREATED_AT = new QueryColumn("created_at");

  private final AuditLogRepository repository;

  public AuditLogQueryService(AuditLogRepository repository) {
    this.repository = repository;
  }

  /** AuditLogView（contract：id/account/action/objectType/objectId/detail/ip/traceId/createdAt）。 */
  public record AuditLogView(
      long id,
      String account,
      String action,
      String objectType,
      Long objectId,
      String detail,
      String ip,
      String traceId,
      Instant createdAt) {

    static AuditLogView of(AuditLogPO po) {
      return new AuditLogView(
          po.getId() == null ? 0 : po.getId(),
          po.getAccount(),
          po.getAction(),
          po.getObjectType(),
          po.getObjectId(),
          po.getDetail(),
          po.getIp(),
          po.getTraceId(),
          po.getCreatedAt());
    }
  }

  /** AuditLogList 载荷（items + total）。 */
  public record AuditLogList(List<AuditLogView> items, long total) {}

  public AuditLogList page(Map<String, String[]> params) {
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition keyword = keywordCondition(filters.q());
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> Optional.empty(), keyword);
    if (filters.sortKeys().isEmpty()) {
      query = query.orderBy(CREATED_AT.desc(), ID.desc());  // banned-words-ok：MyBatis-Flex 构造器方法名，非请求参数
    }
    List<AuditLogView> items =
        repository.page(query, filters.offset(), filters.limit()).stream().map(AuditLogView::of).toList();
    // count 与 page 同条件（去 order/limit）
    Filters countFilters = new Filters(filters.clauses(), List.of(), 1, 1, filters.q());
    QueryWrapper countQuery =
        FilterPredicate.compile(countFilters, COLUMNS::get, value -> Optional.empty(), keyword);
    return new AuditLogList(items, repository.countByQuery(countQuery));
  }

  /** q 关键词：操作人或动作名包含匹配（与 /accounts 的 q 同口径）。 */
  private QueryCondition keywordCondition(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    String like = "%" + q + "%";
    return ACCOUNT.like(like).or(ACTION.like(like));
  }
}
