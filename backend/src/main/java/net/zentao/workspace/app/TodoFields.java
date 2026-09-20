package net.zentao.workspace.app;

import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.time.LocalTime;
import net.zentao.platform.error.ApiException;
import net.zentao.workspace.domain.Todo;

/** 待办字段校验（workspace 卡 §3.1 + §8 守卫行）：一处收口，创建/更新/批量共用。 */
final class TodoFields {

  static final Set<String> TYPES =
      Set.of("custom", "bug", "task", "story", "epic", "requirement", "testRun");

  private TodoFields() {}

  /** 创建校验：title 必填 1–150；type 枚举；priority 1–4；endTime 晚于 beginTime；type≠custom 需 objectId。 */
  static void validateCreate(String title, String type, long objectId, Integer priority, String beginTime,
      String endTime) {
    requireTitle(title);
    if (type != null) {
      validateType(type);
    }
    requireObjectLink(type == null ? "custom" : type, objectId);
    validatePriority(priority);
    validateTimes(beginTime, endTime);
  }

  /** 更新校验：仅对传值字段校验（null = 不修改，03 §1）。 */
  static void validateUpdate(String title, String type, Long objectId, Integer priority, String beginTime,
      String endTime, Todo current) {
    if (title != null) {
      requireTitle(title);
    }
    if (type != null) {
      validateType(type);
      long effectiveId = objectId == null ? current.objectId() : objectId;
      requireObjectLink(type, effectiveId);
    } else if (objectId != null) {
      requireObjectLink(current.type(), objectId);
    }
    if (priority != null) {
      validatePriority(priority);
    }
    if (beginTime != null || endTime != null) {
      validateTimes(beginTime == null ? current.beginTime() : beginTime,
          endTime == null ? current.endTime() : endTime);
    }
  }

  static void requireTitle(String title) {
    if (title == null || title.trim().isEmpty()) {
      throw ApiException.validation(Map.of("title", "required"));
    }
    if (title.trim().length() > 150) {
      throw ApiException.validation(Map.of("title", "maxLength"));
    }
  }

  static void validateType(String type) {
    if (!TYPES.contains(type)) {
      throw ApiException.validation(Map.of("type", "invalid"));
    }
  }

  static void validatePriority(Integer priority) {
    if (priority != null && (priority < 1 || priority > 4)) {
      throw ApiException.validation(Map.of("priority", "range"));
    }
  }

  /** type≠custom 时 objectId 必填（0 视为缺失）。 */
  static void requireObjectLink(String type, long objectId) {
    if (!"custom".equals(type) && objectId <= 0) {
      throw ApiException.validation(Map.of("objectId", "required"));
    }
  }

  /** HH:mm 形态 + endTime 必须晚于 beginTime（§8 守卫行，42201 带 fields）。 */
  static void validateTimes(String beginTime, String endTime) {
    LocalTime begin = parse(beginTime, "beginTime");
    LocalTime end = parse(endTime, "endTime");
    if (begin != null && end != null && !end.isAfter(begin)) {
      throw ApiException.validation(Map.of("endTime", "afterBegin"));
    }
  }

  private static LocalTime parse(String value, String field) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalTime.parse(value);
    } catch (DateTimeParseException e) {
      throw ApiException.validation(Map.of(field, "format"));
    }
  }
}
