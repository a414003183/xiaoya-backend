package net.zentao.org.app;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.util.ArrayList;
import java.util.List;
import net.zentao.org.domain.Account;
import net.zentao.org.domain.AccountRepository;
import net.zentao.org.domain.DepartmentRepository;
import net.zentao.platform.filters.FieldRegistry;
import net.zentao.platform.filters.FilterPredicate;
import net.zentao.platform.filters.Filters;
import net.zentao.platform.session.AccountView;
import net.zentao.platform.session.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * 账号列表查询（org 卡 §3.1 DSL 白名单 + §7 @myDepartment 子树展开）。
 * 软删账号不出现；停用账号照常展示（历史数据保留）。
 */
@Component
public class AccountQueryService {

  private static final FieldRegistry REGISTRY = FieldRegistry.allowing(
      java.util.Set.of("status", "role", "gender", "departmentId", "createdBy", "createdAt", "id"),
      java.util.Set.of("id", "account", "realName", "status", "createdAt", "lastActiveAt"),
      java.util.Set.of("account", "realName", "nickname", "email"));

  private static final java.util.Map<String, String> COLUMNS = java.util.Map.ofEntries(
      java.util.Map.entry("status", "status"),
      java.util.Map.entry("role", "role"),
      java.util.Map.entry("gender", "gender"),
      java.util.Map.entry("departmentId", "department_id"),
      java.util.Map.entry("createdBy", "created_by"),
      java.util.Map.entry("createdAt", "created_at"),
      java.util.Map.entry("id", "id"),
      java.util.Map.entry("account", "account"),
      java.util.Map.entry("realName", "real_name"),
      java.util.Map.entry("lastActiveAt", "last_active_at"));

  private final AccountRepository repository;
  private final DepartmentRepository departmentRepository;

  public AccountQueryService(AccountRepository repository, DepartmentRepository departmentRepository) {
    this.repository = repository;
    this.departmentRepository = departmentRepository;
  }

  /** AccountList 载荷（contract：items + total）。 */
  public record AccountList(List<AccountView> items, long total) {}

  public AccountList page(SessionPrincipal principal, java.util.Map<String, String[]> params) {
    Filters parsed = Filters.parse(params, REGISTRY);
    // @myDepartment 特殊量在服务端展开为 departmentId IN（本部门+后代），从 DSL 条款中剥离
    List<Filters.FilterClause> clauses = new ArrayList<>();
    boolean hasMyDepartment = false;
    for (Filters.FilterClause clause : parsed.clauses()) {
      if ("departmentId".equals(clause.field())
          && clause.values().stream().anyMatch("@myDepartment"::equals)) {
        hasMyDepartment = true;
        continue;
      }
      clauses.add(clause);
    }
    Filters filters = new Filters(clauses, parsed.sortKeys(), parsed.page(), parsed.limit(), parsed.q());

    QueryCondition injected = new QueryColumn("deleted_at").isNull();
    QueryCondition keyword = keywordCondition(filters.q());
    if (keyword != null) {
      injected = injected.and(keyword);
    }
    if (hasMyDepartment) {
      injected = injected.and(myDepartmentCondition(principal));
    }

    QueryWrapper query = FilterPredicate.compile(filters, COLUMNS::get, value -> java.util.Optional.empty(), injected);
    List<AccountView> items = repository.queryPage(query, filters.offset(), filters.limit()).stream()
        .map(account -> CreateAccountHandler.toView(account, repository.groupIdsOf(account.id())))
        .toList();
    Filters countFilters = new Filters(filters.clauses(), List.of(), 1, 1, filters.q());
    QueryWrapper countQuery = FilterPredicate.compile(countFilters, COLUMNS::get, value -> java.util.Optional.empty(), injected);
    return new AccountList(items, repository.countByQuery(countQuery));
  }

  private QueryCondition keywordCondition(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    String like = "%" + q + "%";
    QueryCondition condition = null;
    for (String column : List.of("account", "real_name", "nickname", "email")) {
      QueryCondition part = new QueryColumn(column).like(like);
      condition = condition == null ? part : condition.or(part);
    }
    return condition;
  }

  /** filters[departmentId]=@myDepartment：当前账号部门 + 全部后代（path 前缀）展开为 IN；无部门 → 空集。 */
  private QueryCondition myDepartmentCondition(SessionPrincipal principal) {
    Account me = repository.findActiveById(principal.accountId()).orElse(null);
    List<Long> ids = new ArrayList<>();
    if (me != null && me.departmentId() != null) {
      String myPath = departmentRepository.findById(me.departmentId())
          .map(department -> department.path())
          .orElse("," + me.departmentId() + ",");
      ids.add(me.departmentId());
      for (Row row : Db.selectListByCondition("department",
          new QueryColumn("path").like(myPath + "%").and(new QueryColumn("id").ne(me.departmentId())))) {
        ids.add(row.getLong("id"));
      }
    }
    return new QueryColumn("department_id").in(ids.isEmpty() ? List.of(-1L) : ids);
  }
}
