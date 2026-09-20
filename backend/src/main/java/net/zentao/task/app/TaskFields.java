package net.zentao.task.app;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;

/** 任务/工时字段校验（task 卡 §3/§3b 校验列）。 */
final class TaskFields {

  static final List<String> TYPES = List.of("design", "devel", "request", "test", "study", "discuss", "ui", "affair",
      "misc");
  static final BigDecimal HOURS_MAX = new BigDecimal("999.99");
  static final int TITLE_MAX = 255;

  private TaskFields() {}

  static String requireTitle(String title) {
    if (title == null || title.trim().isEmpty()) {
      throw ApiException.validation(Map.of("title", "required"));
    }
    if (title.trim().length() > TITLE_MAX) {
      throw ApiException.validation(Map.of("title", "maxLength"));
    }
    return title.trim();
  }

  static String type(String type) {
    String value = type == null ? "devel" : type;
    if (!TYPES.contains(value)) {
      throw ApiException.validation(Map.of("type", "invalid"));
    }
    return value;
  }

  static int priority(Integer priority) {
    int value = priority == null ? 3 : priority;
    if (value < 1 || value > 4) {
      throw ApiException.validation(Map.of("priority", "range"));
    }
    return value;
  }

  static void hours(String field, BigDecimal hours, boolean positive) {
    if (hours == null) {
      return;
    }
    if (hours.signum() < 0 || (positive && hours.signum() == 0) || hours.compareTo(HOURS_MAX) > 0) {
      throw ApiException.validation(Map.of(field, "range"));
    }
  }

  /** 登记粒度=天，不接受未来日期（§3b）。 */
  static void workDate(LocalDate workDate) {
    if (workDate != null && workDate.isAfter(LocalDate.now())) {
      throw ApiException.validation(Map.of("workDate", "future"));
    }
  }

  static void accounts(AccountApi accountApi, Map<String, String> roles, List<String> notifyAccounts) {
    List<String> referenced = roles.values().stream().filter(value -> value != null && !value.isBlank()).toList();
    if (!accountApi.missingAccounts(referenced).isEmpty()) {
      throw ApiException.validation(Map.of("assignee", "notFound"));
    }
    if (notifyAccounts != null && !accountApi.missingAccounts(List.copyOf(notifyAccounts)).isEmpty()) {
      throw ApiException.validation(Map.of("notifyAccounts", "notFound"));
    }
  }
}
