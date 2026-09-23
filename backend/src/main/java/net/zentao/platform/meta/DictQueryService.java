package net.zentao.platform.meta;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.filters.LikePatterns;
import org.springframework.stereotype.Component;

/** 字典类型与数据项的列表（T16 P1-4）。两个列表各一套白名单，字段名与契约同集。 */
@Component
public class DictQueryService {

  /** filterable/sortable/searchable 白名单（契约同集声明，门禁三方对齐）。 */
  private static final FieldRegistry TYPE_REGISTRY = FieldRegistry.allowing(
      Set.of("status"),
      Set.of("code", "name", "status"),
      Set.of("code", "name"));

  private static final FieldRegistry DATA_REGISTRY = FieldRegistry.allowing(
      Set.of("status"),
      Set.of("sortNo", "itemValue", "itemLabel", "status"),
      Set.of("itemValue", "itemLabel"));

  private static final Map<String, String> TYPE_COLUMNS = Map.of(
      "code", "code",
      "name", "name",
      "status", "status");

  private static final Map<String, String> DATA_COLUMNS = Map.of(
      "id", "id",
      "itemLabel", "item_label",
      "itemValue", "item_value",
      "sortNo", "sort_no",
      "status", "status");

  private static final QueryColumn TYPE_CODE = new QueryColumn("code");
  private static final QueryColumn TYPE_NAME = new QueryColumn("name");
  private static final QueryColumn DATA_TYPE_CODE = new QueryColumn("type_code");
  private static final QueryColumn DATA_SORT = new QueryColumn("sort_no");
  private static final QueryColumn DATA_ID = new QueryColumn("id");
  private static final QueryColumn DATA_LABEL = new QueryColumn("item_label");
  private static final QueryColumn DATA_VALUE = new QueryColumn("item_value");

  private final DictRepository repository;

  public DictQueryService(DictRepository repository) {
    this.repository = repository;
  }

  public record DictTypeView(String code, String name,
      @Schema(allowableValues = {"active", "disabled"}) String status) {
    static DictTypeView of(DictTypePO po) {
      return new DictTypeView(po.getCode(), po.getName(), po.getStatus());
    }
  }

  public record DictTypeList(List<DictTypeView> items, long total) {}

  public record DictDataView(long id, String typeCode, String itemLabel, String itemValue, int sortNo,
      @Schema(allowableValues = {"active", "disabled"}) String status) {
    static DictDataView of(DictDataPO po) {
      return new DictDataView(po.getId(), po.getTypeCode(), po.getItemLabel(), po.getItemValue(),
          po.getSortNo() == null ? 0 : po.getSortNo(), po.getStatus());
    }
  }

  public record DictDataList(List<DictDataView> items, long total) {}

  public DictTypeList pageTypes(Map<String, String[]> params) {
    Filters filters = Filters.parse(params, TYPE_REGISTRY);
    QueryCondition keyword = keyword(filters.q(), TYPE_CODE, TYPE_NAME);
    QueryWrapper query = FilterPredicate.compile(filters, TYPE_COLUMNS::get, value -> Optional.empty(), keyword);
    if (filters.sortKeys().isEmpty()) {
      query = query.orderBy(TYPE_CODE.asc()); // banned-words-ok：MyBatis-Flex 构造器方法名，非请求参数
    }
    List<DictTypeView> items = repository.pageTypes(query, filters.offset(), filters.limit()).stream()
        .map(DictTypeView::of)
        .toList();
    QueryWrapper countQuery =
        FilterPredicate.compile(filters.forCount(), TYPE_COLUMNS::get, value -> Optional.empty(), keyword);
    return new DictTypeList(items, repository.countTypes(countQuery));
  }

  /** 数据项列表：作用域恒为路径里的 type_code（同类内排序按 sort_no 升序）。 */
  public DictDataList pageData(String typeCode, Map<String, String[]> params) {
    Filters filters = Filters.parse(params, DATA_REGISTRY);
    QueryCondition scope = DATA_TYPE_CODE.eq(typeCode);
    QueryCondition keyword = keyword(filters.q(), DATA_LABEL, DATA_VALUE);
    QueryCondition where = keyword == null ? scope : scope.and(keyword);
    QueryWrapper query = FilterPredicate.compile(filters, DATA_COLUMNS::get, value -> Optional.empty(), where);
    if (filters.sortKeys().isEmpty()) {
      query = query.orderBy(DATA_SORT.asc(), DATA_ID.asc()); // banned-words-ok：MyBatis-Flex 构造器方法名，非请求参数
    }
    List<DictDataView> items = repository.pageData(query, filters.offset(), filters.limit()).stream()
        .map(DictDataView::of)
        .toList();
    QueryWrapper countQuery = FilterPredicate.compile(filters.forCount(), DATA_COLUMNS::get, value -> Optional.empty(), where);
    return new DictDataList(items, repository.countData(countQuery));
  }

  private static QueryCondition keyword(String q, QueryColumn... columns) {
    if (q == null || q.isBlank()) {
      return null;
    }
    String like = LikePatterns.contains(q);
    QueryCondition condition = null;
    for (QueryColumn column : columns) {
      QueryCondition single = column.likeRaw(like);
      condition = condition == null ? single : condition.or(single);
    }
    return condition;
  }
}
