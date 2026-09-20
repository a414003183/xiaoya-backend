package net.zentao.platform.error;

import java.util.Map;

/** 全系统唯一业务异常；由 ApiExceptionMapper 统一映射为错误信封（03 §2/§4）。 */
public final class ApiException extends RuntimeException {

  private final ErrorCode errorCode;
  private final Map<String, String> fields;

  private ApiException(ErrorCode errorCode, String message, Map<String, String> fields) {
    super(message);
    this.errorCode = errorCode;
    this.fields = fields;
  }

  public static ApiException badRequest(String message) {
    return new ApiException(ErrorCode.BAD_REQUEST, message, Map.of());
  }

  public static ApiException unauthenticated(String message) {
    return new ApiException(ErrorCode.UNAUTHENTICATED, message, Map.of());
  }

  public static ApiException forbidden(String message) {
    return new ApiException(ErrorCode.FORBIDDEN, message, Map.of());
  }

  public static ApiException dataForbidden(String message) {
    return new ApiException(ErrorCode.DATA_FORBIDDEN, message, Map.of());
  }

  public static ApiException notFound(String what) {
    return new ApiException(ErrorCode.NOT_FOUND, what + "不存在。", Map.of());
  }

  public static ApiException lockConflict(String message) {
    return new ApiException(ErrorCode.LOCK_CONFLICT, message, Map.of());
  }

  public static ApiException validation(Map<String, String> fields) {
    return new ApiException(ErrorCode.VALIDATION_FAILED, "字段校验失败。", fields);
  }

  public static ApiException stateActionNotAllowed(String message) {
    return new ApiException(ErrorCode.STATE_ACTION_NOT_ALLOWED, message, Map.of());
  }

  public static ApiException guardNotSatisfied(String message) {
    return new ApiException(ErrorCode.GUARD_NOT_SATISFIED, message, Map.of());
  }

  public static ApiException rateLimited(String message) {
    return new ApiException(ErrorCode.RATE_LIMITED, message, Map.of());
  }

  public static ApiException internal(String message) {
    return new ApiException(ErrorCode.INTERNAL_ERROR, message, Map.of());
  }

  public ErrorCode errorCode() {
    return errorCode;
  }

  public Map<String, String> fields() {
    return fields;
  }
}
