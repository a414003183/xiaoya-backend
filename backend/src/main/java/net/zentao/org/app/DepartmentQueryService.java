package net.zentao.org.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zentao.org.api.DepartmentApi;
import net.zentao.org.api.DepartmentNode;
import net.zentao.org.domain.Department;
import net.zentao.org.domain.DepartmentRepository;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 部门查询（org 卡 §3.2）：整棵嵌套树（{@link #tree()}）+ 平铺分页列表（{@link #page(Map)}）。
 * 列表行随父部门名一并返回（parentName 一次映射解析，不做逐行查询）。
 */
@Component
public class DepartmentQueryService implements DepartmentApi {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      java.util.Set.of("parentId", "grade"),
      java.util.Set.of("id", "name", "grade", "sort"),
      java.util.Set.of("name"));

  private static final Map<String, String> COLUMNS = Map.of(
      "id", "id",
      "name", "name",
      "parentId", "parent_id",
      "grade", "grade",
      "sort", "sort",
      "path", "path");

  private final DepartmentRepository repository;

  public DepartmentQueryService(DepartmentRepository repository) {
    this.repository = repository;
  }

  /** DepartmentList 载荷（contract：items + total；平铺，rows 的 children 恒空）。 */
  public record DepartmentList(List<DepartmentNode> items, long total) {}

  @Override
  public List<DepartmentNode> tree() {
    List<Department> all = repository.findAll();
    Map<Long, String> names = namesById(all);
    Map<Long, List<Department>> childrenOf = new HashMap<>();
    List<Department> roots = new ArrayList<>();
    for (Department department : all) {
      if (department.parentId() == null) {
        roots.add(department);
      } else {
        childrenOf.computeIfAbsent(department.parentId(), key -> new ArrayList<>()).add(department);
      }
    }
    Comparator<Department> bySort = Comparator.comparingInt(Department::sort).thenComparingLong(Department::id);
    roots.sort(bySort);
    List<DepartmentNode> nodes = new ArrayList<>();
    for (Department root : roots) {
      nodes.add(toNode(root, childrenOf, names, bySort));
    }
    return nodes;
  }

  @Override
  public Optional<DepartmentNode> findById(long departmentId) {
    List<Department> all = repository.findAll();
    return repository.findById(departmentId).map(department -> toNode(department, childrenOf(all), namesById(all),
        Comparator.comparingInt(Department::sort).thenComparingLong(Department::id)));
  }

  /** 平铺列表分页（org 卡 §3.2 filterable parentId/grade、sortable id/name/grade/sort、searchable name）。 */
  @Transactional(readOnly = true)
  public DepartmentList page(Map<String, String[]> params) {
    Filters filters = Filters.parse(params, REGISTRY);
    if (filters.sortKeys().isEmpty()) {
      // 缺省排序：同级排序权重升序，同权重按 id 升序（稳定、与树内顺序一致）
      filters = new Filters(filters.clauses(),
          List.of(new Filters.SortKey("sort", false), new Filters.SortKey("id", false)),
          filters.page(), filters.limit(), filters.q());
    }
    QueryCondition keyword = keywordCondition(filters.q());
    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> Optional.empty(), keyword);
    Map<Long, String> names = namesById(repository.findAll());
    List<DepartmentNode> items = repository.queryPage(query, filters.offset(), filters.limit()).stream()
        .map(department -> DepartmentNode.flat(department.id(), department.name(), department.parentId(),
            nameOf(names, department.parentId()), department.path(), department.grade(), department.sort(),
            department.manager()))
        .toList();
    Filters countFilters = new Filters(filters.clauses(), List.of(), 1, 1, filters.q());
    QueryWrapper countQuery = FilterPredicate.compile(countFilters, COLUMNS::get, value -> Optional.empty(), keyword);
    return new DepartmentList(items, repository.countByQuery(countQuery));
  }

  private QueryCondition keywordCondition(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    return new QueryColumn("name").like("%" + q + "%");
  }

  private Map<Long, List<Department>> childrenOf(List<Department> all) {
    Map<Long, List<Department>> childrenOf = new HashMap<>();
    for (Department department : all) {
      if (department.parentId() != null) {
        childrenOf.computeIfAbsent(department.parentId(), key -> new ArrayList<>()).add(department);
      }
    }
    return childrenOf;
  }

  private static Map<Long, String> namesById(List<Department> all) {
    Map<Long, String> names = new HashMap<>();
    for (Department department : all) {
      names.put(department.id(), department.name());
    }
    return names;
  }

  private static String nameOf(Map<Long, String> names, Long parentId) {
    return parentId == null ? null : names.get(parentId);
  }

  private DepartmentNode toNode(Department department, Map<Long, List<Department>> childrenOf,
      Map<Long, String> names, Comparator<Department> bySort) {
    List<Department> mine = childrenOf.get(department.id());
    mine = mine == null ? List.of() : mine.stream().sorted(bySort).toList();
    List<DepartmentNode> childNodes =
        mine.stream().map(child -> toNode(child, childrenOf, names, bySort)).toList();
    return new DepartmentNode(department.id(), department.name(), department.parentId(),
        nameOf(names, department.parentId()), department.path(), department.grade(), department.sort(),
        department.manager(), childNodes);
  }
}
