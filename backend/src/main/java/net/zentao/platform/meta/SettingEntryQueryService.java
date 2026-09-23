package net.zentao.platform.meta;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
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
 * 参数管理列表（T15 P1-3）：只管**系统行**（owner=system、section=''）——个人偏好行（notify.*、地盘布局）
 * 是账号自己的，不进这个页面；作用域条件服务端恒注入（与 /my/* 的 role=@me 同法，客户端改不了）。
 *
 * <p>value 原样出 JSON 文本（不解析）：读取方按 JSON 解析，页面编辑的也是这段文本，往返无损。
 */
@Component
public class SettingEntryQueryService {

  /** filterable/sortable/searchable 白名单（契约同集声明，门禁三方对齐）。 */
  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("domain"),
      Set.of("domain", "itemKey"),
      Set.of("itemKey"));

  private static final Map<String, String> COLUMNS = Map.of(
      "domain", "domain",
      "itemKey", "item_key");

  private static final QueryColumn OWNER = new QueryColumn("owner");
  private static final QueryColumn SECTION = new QueryColumn("section");
  private static final QueryColumn DOMAIN = new QueryColumn("domain");
  private static final QueryColumn ITEM_KEY = new QueryColumn("item_key");

  private final SettingRepository repository;

  public SettingEntryQueryService(SettingRepository repository) {
    this.repository = repository;
  }

  /** 参数行（platform 卡 §3.7；key 是扁平寻址串 `<domain>.<key>`，与 GET /settings 的 keys 同形）。 */
  public record SettingEntryView(String key, String domain, String itemKey, String value) {

    static SettingEntryView of(SettingPO po) {
      return new SettingEntryView(flat(po.getDomain(), po.getItemKey()), po.getDomain(), po.getItemKey(),
          po.getItemValue());
    }

    public static String flat(String domain, String itemKey) {
      return domain + "." + itemKey;
    }
  }

  /** SettingEntryList 载荷（items + total）。 */
  public record SettingEntryList(List<SettingEntryView> items, long total) {}

  public SettingEntryList page(Map<String, String[]> params) {
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition scope = systemScope(keywordCondition(filters.q()));
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> Optional.empty(), scope);
    if (filters.sortKeys().isEmpty()) {
      // 缺省按「域 → 键」字典序：参数页是查表用的，同域参数挨在一起最好找
      query = query.orderBy(DOMAIN.asc(), ITEM_KEY.asc()); // banned-words-ok：MyBatis-Flex 构造器方法名，非请求参数
    }
    List<SettingEntryView> items = repository.page(query, filters.offset(), filters.limit()).stream()
        .map(SettingEntryView::of)
        .toList();
    // count 与 page 同条件（去 order/limit）
    QueryWrapper countQuery = FilterPredicate.compile(filters.forCount(), COLUMNS::get, value -> Optional.empty(), scope);
    return new SettingEntryList(items, repository.countByQuery(countQuery));
  }

  /** 系统行作用域 + 可选关键词（q 只打键名：值里搜出来的行对管理员没有意义）。 */
  static QueryCondition systemScope(QueryCondition keyword) {
    QueryCondition scope = OWNER.eq(SettingRepository.SYSTEM_OWNER).and(SECTION.eq(""));
    return keyword == null ? scope : scope.and(keyword);
  }

  private static QueryCondition keywordCondition(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    return ITEM_KEY.likeRaw(LikePatterns.contains(q)).or(DOMAIN.likeRaw(LikePatterns.contains(q)));
  }
}
