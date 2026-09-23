package net.zentao.platform.audit;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.time.LocalDate;
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
 * 查询聚合读侧（T04/ADR-004 决策 3）：audit_query_stat 分页列表（resource = API 路径首段，如 products）——审计页用它回答
 * 「谁在什么时候频繁查哪个资源、慢不慢」，主表 audit_log 不存 query 类明细。
 *
 * <p>缺省按 日期倒序 + id 倒序（同日多资源要有稳定序，翻页才不会重复/漏行）。
 */
@Component
public class AuditQueryStatQueryService {

  /** filterable/sortable/searchable 白名单（契约同集声明，门禁三方对齐）。 */
  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("account", "resource", "day"),
      Set.of("id", "day", "count"),
      Set.of("account", "resource"));

  private static final Map<String, String> COLUMNS = Map.of(
      "id", "id",
      "account", "account",
      "resource", "resource",
      "day", "stat_day",
      "count", "query_count");

  private static final QueryColumn ID = new QueryColumn("id");
  private static final QueryColumn STAT_DAY = new QueryColumn("stat_day");
  private static final QueryColumn ACCOUNT = new QueryColumn("account");
  private static final QueryColumn MODULE = new QueryColumn("resource");

  private final AuditQueryStatRepository repository;

  public AuditQueryStatQueryService(AuditQueryStatRepository repository) {
    this.repository = repository;
  }

  /** AuditQueryStatView（契约同构）：day 是聚合日（UTC），count/totalMs 是当日累计。 */
  public record AuditQueryStatView(long id, String account, String resource, LocalDate day, long count, long totalMs) {

    static AuditQueryStatView of(AuditQueryStatPO po) {
      return new AuditQueryStatView(
          po.getId() == null ? 0 : po.getId(),
          po.getAccount(),
          po.getModule(),
          po.getStatDay(),
          po.getQueryCount() == null ? 0 : po.getQueryCount(),
          po.getTotalMs() == null ? 0 : po.getTotalMs());
    }
  }

  /** AuditQueryStatList 载荷（items + total）。 */
  public record AuditQueryStatList(List<AuditQueryStatView> items, long total) {}

  public AuditQueryStatList page(Map<String, String[]> params) {
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition keyword = keywordCondition(filters.q());
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> Optional.empty(), keyword);
    if (filters.sortKeys().isEmpty()) {
      query = query.orderBy(STAT_DAY.desc(), ID.desc());  // banned-words-ok：MyBatis-Flex 构造器方法名，非请求参数
    }
    List<AuditQueryStatView> items =
        repository.page(query, filters.offset(), filters.limit()).stream().map(AuditQueryStatView::of).toList();
    QueryWrapper countQuery =
        FilterPredicate.compile(filters.forCount(), COLUMNS::get, value -> Optional.empty(), keyword);
    return new AuditQueryStatList(items, repository.countByQuery(countQuery));
  }

  private QueryCondition keywordCondition(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    String like = LikePatterns.contains(q);
    return ACCOUNT.likeRaw(like).or(MODULE.likeRaw(like));
  }
}
