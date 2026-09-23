package net.zentao.org.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.zentao.org.api.AccountApi;
import net.zentao.org.app.CreateAccountHandler;
import net.zentao.org.domain.AccountRepository;
import net.zentao.platform.filters.LikePatterns;
import net.zentao.platform.session.AccountView;
import org.springframework.stereotype.Component;

/** 账号域对外接口实现（org 卡 §5；视图装配复用 CreateAccountHandler.toView）。 */
@Component
public class AccountApiImpl implements AccountApi {

  private final AccountRepository repository;

  public AccountApiImpl(AccountRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<AccountView> view(long accountId) {
    return repository.findActiveById(accountId)
        .map(account -> CreateAccountHandler.toView(account, repository.roleIdsOf(accountId)));
  }

  @Override
  public List<AccountView> viewsByIds(List<Long> accountIds) {
    return accountIds.stream().distinct().map(this::view).flatMap(Optional::stream).toList();
  }

  @Override
  public List<String> missingAccounts(List<String> accounts) {
    List<String> checked = accounts.stream()
        .filter(account -> account != null && !account.isBlank())
        .distinct()
        .toList();
    if (checked.isEmpty()) {
      return List.of();
    }
    List<Row> rows = Db.selectListByCondition("account",
        new QueryColumn("account").in(checked).and(new QueryColumn("deleted_at").isNull()));
    List<String> existing = rows.stream().map(row -> row.getString("account")).toList();
    List<String> missing = new ArrayList<>();
    for (String account : checked) {
      if (!existing.contains(account)) {
        missing.add(account);
      }
    }
    return missing;
  }

  @Override
  public List<String> enabledAccountsOf(Long departmentId) {
    List<Long> departmentIds = descendantDepartmentIds(departmentId);
    var condition = new QueryColumn("deleted_at").isNull().and(new QueryColumn("status").eq("active"));
    if (!departmentIds.isEmpty()) {
      condition = condition.and(new QueryColumn("department_id").in(new ArrayList<Object>(departmentIds)));
    }
    return Db.selectListByCondition("account", condition).stream()
        .map(row -> row.getString("account"))
        .filter(account -> account != null && !account.isBlank())
        .toList();
  }

  /** 部门 id 集：含自身与全部后代（按 path 前缀展开，org 卡 §3.2 邻接表 + 物化 path）。 */
  private List<Long> descendantDepartmentIds(Long departmentId) {
    if (departmentId == null) {
      return List.of();
    }
    List<Row> rootRows =
        Db.selectListByCondition("department", new QueryColumn("id").eq(departmentId));
    if (rootRows.isEmpty()) {
      return List.of(departmentId);
    }
    Row root = rootRows.getFirst();
    String path = root.getString("path");
    List<Row> rows = path == null || path.isBlank()
        ? List.of(root)
        : Db.selectListByCondition("department", new QueryColumn("path").likeLeft(path));
    List<Long> ids = new ArrayList<>();
    for (Row row : rows) {
      Object id = row.get("id");
      if (id instanceof Number number) {
        ids.add(number.longValue());
      }
    }
    if (!ids.contains(departmentId)) {
      ids.add(departmentId);
    }
    return ids;
  }
}
