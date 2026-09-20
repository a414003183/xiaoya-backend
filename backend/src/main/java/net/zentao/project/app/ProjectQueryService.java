package net.zentao.project.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.api.ExecutionApi;
import net.zentao.project.api.ProjectApi;
import net.zentao.project.api.ProjectView;
import net.zentao.project.domain.ProjectProductRepository;
import net.zentao.project.domain.ProjectRepository;
import org.springframework.stereotype.Component;

/** 三型列表与子资源列表（project 卡 §3.1 DSL 白名单 + §7 DataScope 注入可见 id 集，A6）。 */
@Component
public class ProjectQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      Set.of("type", "status", "model", "priority", "acl", "pm", "productId", "parentId", "beginDate", "endDate",
          "createdBy", "createdAt", "id"),
      Set.of("id", "name", "status", "priority", "beginDate", "endDate", "createdAt", "sort"),
      Set.of("name", "code"));

  private static final Map<String, String> COLUMNS = Map.ofEntries(
      Map.entry("type", "type"),
      Map.entry("status", "status"),
      Map.entry("model", "model"),
      Map.entry("priority", "priority"),
      Map.entry("acl", "acl"),
      Map.entry("pm", "pm"),
      Map.entry("parentId", "parent_id"),
      Map.entry("beginDate", "begin_date"),
      Map.entry("endDate", "end_date"),
      Map.entry("createdBy", "created_by"),
      Map.entry("createdAt", "created_at"),
      Map.entry("id", "id"),
      Map.entry("name", "name"),
      Map.entry("sort", "sort"));

  private final ProjectRepository repository;
  private final ProjectApi projectApi;
  private final ExecutionApi executionApi;
  private final ProjectProductRepository projectProductRepository;

  public ProjectQueryService(ProjectRepository repository, ProjectApi projectApi, ExecutionApi executionApi,
      ProjectProductRepository projectProductRepository) {
    this.repository = repository;
    this.projectApi = projectApi;
    this.executionApi = executionApi;
    this.projectProductRepository = projectProductRepository;
  }

  /** ProjectList 载荷（contract：items + total）。 */
  public record ProjectList(List<ProjectView> items, long total) {}

  /**
   * 子资源对象守卫（三型共用；不存在 → 40401，存在但不可见 → 40302）：
   * 成员/干系人/白名单/产品/需求关联各族端点取对象一律经此，避免各处理器自抄一份可见性判定。
   */
  public void requireVisible(SessionPrincipal actor, String objectType, long objectId) {
    if ("execution".equals(objectType)) {
      executionApi.requireExecution(actor, objectId);
    } else {
      projectApi.requireVisible(actor, objectId, objectType);
    }
  }

  /** 三型顶部列表；kind ∈ program|project|execution。 */
  public ProjectList page(SessionPrincipal principal, String kind, Map<String, String[]> params) {
    return query(principal, kind, 0, params);
  }

  /** 子资源列表（/programs/{id}/programs|projects、/projects/{id}/executions）。 */
  public ProjectList children(SessionPrincipal principal, String kind, long parentId, Map<String, String[]> params) {
    return query(principal, kind, parentId, params);
  }

  private ProjectList query(SessionPrincipal principal, String kind, long parentId, Map<String, String[]> params) {
    Filters parsed = Filters.parse(params, REGISTRY);
    // filters[productId] 无本表列（关联在 project_product，B-PRD-01）：服务端展开为 id IN，从 DSL 条款中剥离
    // （写法同 org @myDepartment 邻例）
    List<Filters.FilterClause> clauses = new ArrayList<>();
    List<Filters.FilterClause> productClauses = new ArrayList<>();
    for (Filters.FilterClause clause : parsed.clauses()) {
      if ("productId".equals(clause.field())) {
        productClauses.add(clause);
      } else {
        clauses.add(clause);
      }
    }
    Filters filters = new Filters(clauses, parsed.sortKeys(), parsed.page(), parsed.limit(), parsed.q());

    QueryCondition injected = new QueryColumn("deleted_at").isNull().and(typeCondition(kind));
    if (parentId != 0) {
      injected = injected.and(new QueryColumn("parent_id").eq(parentId));
    }
    for (Filters.FilterClause clause : productClauses) {
      injected = injected.and(productCondition(clause));
    }
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    if (kind.equals("execution")) {
      ExecutionApi.ExecutionScope scope = executionApi.executionScope(principal);
      if (!scope.visibleToAll()) {
        injected = injected.and(idIn(scope.executionIds()));
      }
    } else {
      ProjectApi.VisibleScope scope = projectApi.visibleScope(principal, kind);
      if (!scope.visibleToAll()) {
        injected = injected.and(idIn(scope.ids()));
      }
    }

    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> Optional.empty(), injected);
    List<ProjectView> items = repository.queryPage(query, filters.offset(), filters.limit()).stream()
        .map(ProjectView::of)
        .toList();
    Filters countFilters = new Filters(filters.clauses(), List.of(), 1, 1, filters.q());
    QueryWrapper countQuery = FilterPredicate.compile(countFilters, COLUMNS::get, value -> Optional.empty(), injected);
    return new ProjectList(items, repository.countByQuery(countQuery));
  }

  private static QueryCondition typeCondition(String kind) {
    return switch (kind) {
      case "program" -> new QueryColumn("type").eq("program");
      case "project" -> new QueryColumn("type").eq("project");
      default -> new QueryColumn("type").in(ProjectFields.EXECUTION_TYPES);
    };
  }

  /** filters[productId]=1,2：project_product 反查关联项目集 → id IN；无关联项目 → 恒假（同空可见集口径）。 */
  private QueryCondition productCondition(Filters.FilterClause clause) {
    List<Long> projectIds = new ArrayList<>();
    for (String value : clause.values()) {
      long productId;
      try {
        productId = Long.parseLong(value);
      } catch (NumberFormatException e) {
        throw ApiException.badRequest("无法解析的过滤值：productId=" + value);
      }
      projectIds.addAll(projectProductRepository.projectIdsOfProduct(productId));
    }
    return idIn(projectIds);
  }

  /** 可见集为空 → 恒假条件，避免 IN () 语法错误。 */
  private static QueryCondition idIn(java.util.Collection<Long> ids) {
    return new QueryColumn("id").in(ids.isEmpty() ? List.of(-1L) : List.copyOf(ids));
  }

  private QueryCondition keywordCondition(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    String like = "%" + q + "%";
    return new QueryColumn("name").like(like).or(new QueryColumn("code").like(like));
  }
}
