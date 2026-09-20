package net.zentao.project.app;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;

/** 项目字段校验（project 卡 §3.1 校验列；三型共用，类型专属规则在各自处理器）。 */
final class ProjectFields {

  static final List<String> TYPES = List.of("program", "project", "sprint", "stage", "kanban");
  static final List<String> MODELS = List.of("scrum", "waterfall", "kanban");
  static final List<String> BUDGET_UNITS = List.of("CNY", "USD");
  static final List<String> EXECUTION_TYPES = List.of("sprint", "stage", "kanban");
  static final int DAYS_MAX = 3650;

  private ProjectFields() {}

  static String requireName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw ApiException.validation(Map.of("name", "required"));
    }
    if (name.trim().length() > 90) {
      throw ApiException.validation(Map.of("name", "maxLength"));
    }
    return name.trim();
  }

  static void validateCode(String code) {
    if (code != null && code.length() > 45) {
      throw ApiException.validation(Map.of("code", "maxLength"));
    }
  }

  /** beginDate ≤ endDate（project 卡 §3.1）。 */
  static void validateDates(LocalDate beginDate, LocalDate endDate) {
    if (beginDate != null && endDate != null && beginDate.isAfter(endDate)) {
      throw ApiException.validation(Map.of("endDate", "beforeBegin"));
    }
  }

  static void validateDays(Integer days) {
    if (days != null && (days < 0 || days > DAYS_MAX)) {
      throw ApiException.validation(Map.of("days", "range"));
    }
  }

  static void validateBudget(BigDecimal budget) {
    if (budget != null && budget.signum() < 0) {
      throw ApiException.validation(Map.of("budget", "min"));
    }
  }

  static void validatePriority(Integer priority) {
    if (priority != null && (priority < 1 || priority > 4)) {
      throw ApiException.validation(Map.of("priority", "range"));
    }
  }

  static void validateModel(String model) {
    if (model != null && !MODELS.contains(model)) {
      throw ApiException.validation(Map.of("model", "invalid"));
    }
  }

  /** acl 取值：program 型不可用 program（§3.1 备注）。 */
  static String validateAcl(String type, String acl) {
    String value = acl == null ? "open" : acl;
    if (!List.of("open", "private", "program").contains(value)) {
      throw ApiException.validation(Map.of("acl", "invalid"));
    }
    if ("program".equals(value) && !"project".equals(type)) {
      throw ApiException.validation(Map.of("acl", "invalid"));
    }
    return value;
  }

  static void validateBudgetUnit(String budgetUnit) {
    if (budgetUnit != null && !BUDGET_UNITS.contains(budgetUnit)) {
      throw ApiException.validation(Map.of("budgetUnit", "invalid"));
    }
  }

  /** pm/po/qd/rd 与白名单账号必须存在（org AccountApi 唯一校验口）。 */
  static void validateAccounts(AccountApi accountApi, Map<String, String> roleAccounts, List<String> whitelist) {
    List<String> referenced = new ArrayList<>();
    roleAccounts.forEach((field, account) -> {
      if (account != null && !account.isBlank()) {
        referenced.add(account);
      }
    });
    if (!accountApi.missingAccounts(referenced).isEmpty()) {
      throw ApiException.validation(Map.of("accounts", "notFound"));
    }
    if (whitelist != null && !accountApi.missingAccounts(List.copyOf(whitelist)).isEmpty()) {
      throw ApiException.validation(Map.of("whitelist", "notFound"));
    }
  }
}
