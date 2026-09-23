package net.zentao.org.app;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.org.api.AccountApi;
import net.zentao.org.api.PersonnelMemberList;
import net.zentao.org.api.PersonnelMemberView;
import net.zentao.org.api.PersonnelWorkloadList;
import net.zentao.org.api.PersonnelWorkloadView;
import net.zentao.quality.api.BugApi;
import net.zentao.task.api.TaskApi;
import net.zentao.org.domain.Account;
import net.zentao.org.domain.AccountRepository;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 人员管理聚合（org 卡 §5 Personnel 节，无表只读）：成员在办计数与工作量统计，
 * 跨域经 TaskApi/BugApi（A2），无行级 ACL（personnel-view 功能码 + 超管即可见）。
 */
@Component
public class PersonnelQueryService {

  /** ponytail: 全公司成员量有界，内存过滤/分页；升级路径 = 部门子查询下推 SQL。 */
  private static final int DEFAULT_LIMIT = 50;
  private static final int MAX_LIMIT = 200;

  private final AccountRepository accountRepository;
  private final AccountApi accountApi;
  private final TaskApi taskApi;
  private final BugApi bugApi;

  public PersonnelQueryService(AccountRepository accountRepository, AccountApi accountApi, TaskApi taskApi,
      BugApi bugApi) {
    this.accountRepository = accountRepository;
    this.accountApi = accountApi;
    this.taskApi = taskApi;
    this.bugApi = bugApi;
  }

  /** 成员列表：部门过滤（含后代）后在办计数。 */
  public PersonnelMemberList members(Map<String, String[]> params) {
    List<Account> accounts = filteredAccounts(params);
    List<String> names = accounts.stream().map(Account::account).toList();
    Map<String, Long> openTasks = taskApi.openTaskCounts(names);
    Map<String, Long> unresolvedBugs = bugApi.unresolvedCounts(names);

    List<PersonnelMemberView> items = accounts.stream()
        .map(account -> new PersonnelMemberView(account.account(), account.realName(), account.departmentId(),
            accountRepository.roleIdsOf(account.id()), openTasks.getOrDefault(account.account(), 0L),
            unresolvedBugs.getOrDefault(account.account(), 0L)))
        .toList();
    return new PersonnelMemberList(page(items, params), items.size());
  }

  /** 工作量统计：filters[date] 必填区间 + 部门过滤（含后代），按人聚合。 */
  public PersonnelWorkloadList workload(Map<String, String[]> params) {
    LocalDate[] range = requiredRange(params);
    List<Account> accounts = filteredAccounts(params);
    List<String> names = accounts.stream().map(Account::account).toList();
    Map<String, TaskApi.AssigneeWorkload> byAccount = new LinkedHashMap<>();
    taskApi.workload(range[0], range[1], names)
        .forEach(row -> byAccount.put(row.account(), row));

    List<PersonnelWorkloadView> all = accounts.stream()
        .map(account -> {
          TaskApi.AssigneeWorkload row = byAccount.get(account.account());
          BigDecimal consumed = row == null || row.consumedHours() == null ? BigDecimal.ZERO : row.consumedHours();
          long finished = row == null ? 0L : row.finishedTaskCount();
          return new PersonnelWorkloadView(account.account(), account.realName(), account.departmentId(), consumed,
              finished);
        })
        .sorted(workloadOrder(params))
        .toList();
    return new PersonnelWorkloadList(page(all, params), all.size());
  }

  /** 启用账号（停用/软删不出现）+ 部门后代展开 + account/q 过滤。 */
  private List<Account> filteredAccounts(Map<String, String[]> params) {
    Set<String> scoped = null;
    String departmentId = first(params, "filters[departmentId]");
    if (departmentId != null && !departmentId.isBlank()) {
      scoped = Set.copyOf(accountApi.enabledAccountsOf(parseId(departmentId)));
    }
    String accountFilter = first(params, "filters[account]");
    String keyword = first(params, "q");
    Set<String> scope = scoped;
    return accountRepository.findAllVisible().stream()
        .filter(account -> "active".equals(account.status()))
        .filter(account -> scope == null || scope.contains(account.account()))
        .filter(account -> accountFilter == null || accountFilter.isBlank()
            || accountFilter.equals(account.account()))
        .filter(account -> keyword == null || keyword.isBlank() || matches(account, keyword))
        .sorted(Comparator.comparing(Account::account))
        .toList();
  }

  private static boolean matches(Account account, String keyword) {
    String lower = keyword.toLowerCase();
    return account.account().toLowerCase().contains(lower)
        || (account.realName() != null && account.realName().toLowerCase().contains(lower));
  }

  private static Comparator<PersonnelWorkloadView> workloadOrder(Map<String, String[]> params) {
    String sort = first(params, "sort");
    if ("consumedHours".equals(sort) || "finishedTaskCount".equals(sort) || "account".equals(sort)) {
      Comparator<PersonnelWorkloadView> ascending = switch (sort) {
        case "consumedHours" -> Comparator.comparing(PersonnelWorkloadView::consumedHours);
        case "finishedTaskCount" -> Comparator.comparingLong(PersonnelWorkloadView::finishedTaskCount);
        default -> Comparator.comparing(PersonnelWorkloadView::account);
      };
      return ascending;
    }
    return Comparator.comparing(PersonnelWorkloadView::consumedHours).reversed()
        .thenComparing(PersonnelWorkloadView::account);
  }

  /** filters[date]=a..b 必填；缺失或起止倒置 → 40001（org 卡 §5 Personnel：工作量空区间 → 40001）。 */
  private static LocalDate[] requiredRange(Map<String, String[]> params) {
    String value = first(params, "filters[date]");
    if (value == null || !value.contains("..")) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "personnel.workload.dateRequired");
    }
    int separator = value.indexOf("..");
    String from = value.substring(0, separator);
    String to = value.substring(separator + 2);
    if (from.isBlank() || to.isBlank()) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "personnel.workload.dateEndsEmpty");
    }
    try {
      LocalDate start = LocalDate.parse(from);
      LocalDate end = LocalDate.parse(to);
      if (end.isBefore(start)) {
        throw ApiException.keyed(ErrorCode.BAD_REQUEST, "personnel.workload.dateReversed");
      }
      return new LocalDate[] {start, end};
    } catch (java.time.format.DateTimeParseException e) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "personnel.date.format");
    }
  }

  private static <T> List<T> page(List<T> items, Map<String, String[]> params) {
    int page = intParam(params, "page", 1);
    // A-04：format=csv 时放宽到 5000（与平台 Filters 同口径）
    int cap = "csv".equals(first(params, "format")) ? net.zentao.platform.filters.Filters.CSV_MAX_LIMIT : MAX_LIMIT;
    int limit = Math.min(intParam(params, "limit", DEFAULT_LIMIT), cap);
    int offset = (page - 1) * limit;
    if (offset >= items.size()) {
      return List.of();
    }
    return items.subList(offset, Math.min(offset + limit, items.size()));
  }

  private static int intParam(Map<String, String[]> params, String name, int fallback) {
    String value = first(params, name);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Math.max(Integer.parseInt(value), 1);
    } catch (NumberFormatException e) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "personnel.param.integer", name);
    }
  }

  private static long parseId(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw ApiException.keyed(ErrorCode.BAD_REQUEST, "personnel.department.idInteger");
    }
  }

  private static String first(Map<String, String[]> params, String name) {
    String[] values = params.get(name);
    return values == null || values.length == 0 ? null : values[values.length - 1];
  }
}
