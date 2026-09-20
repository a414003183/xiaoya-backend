package net.zentao.platform.langimport;

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
import org.springframework.stereotype.Component;

/** 语言包上传记录查询（platform 卡 §3.12；列表 DSL 见 03 §3）。 */
@Component
public class LangImportQueryService {

  /** filterable/sortable/searchable 白名单（platform 卡 §3.12；契约同集声明，门禁三方对齐）。 */
  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("lang", "status"),
      Set.of("id", "createdAt"),
      Set.of("fileName"));

  private static final Map<String, String> COLUMNS = Map.of(
      "lang", "lang",
      "status", "status",
      "fileName", "file_name",
      "id", "id",
      "createdAt", "created_at");

  private static final QueryColumn CREATED_AT = new QueryColumn("created_at");
  private static final QueryColumn ID = new QueryColumn("id");
  private static final QueryColumn FILE_NAME = new QueryColumn("file_name");

  private final LangImportRepository repository;

  public LangImportQueryService(LangImportRepository repository) {
    this.repository = repository;
  }

  /** LangImportList 载荷（items + total）。 */
  public record LangImportList(List<LangImportView> items, long total) {}

  public LangImportList page(Map<String, String[]> params) {
    Filters filters = Filters.parse(params, REGISTRY);
    QueryCondition injected = filters.q() == null ? null : FILE_NAME.like("%" + filters.q() + "%");
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> Optional.empty(), injected);
    if (filters.sortKeys().isEmpty()) {
      query = query.orderBy(CREATED_AT.desc(), ID.desc());  // banned-words-ok：MyBatis-Flex 构造器方法名，非请求参数
    }
    List<LangImportView> items =
        repository.page(query, filters.offset(), filters.limit()).stream().map(LangImportView::of).toList();
    // count 与 page 同条件（去 order/limit）
    Filters countFilters = new Filters(filters.clauses(), List.of(), 1, 1, filters.q());
    QueryWrapper countQuery =
        FilterPredicate.compile(countFilters, COLUMNS::get, value -> Optional.empty(), injected);
    return new LangImportList(items, repository.countByQuery(countQuery));
  }
}
