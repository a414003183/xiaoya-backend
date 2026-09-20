package net.zentao.platform.error;

import org.springframework.http.HttpStatus;

/** 全局错误码（03-contract.md §4：5 位码，段位即 HTTP 映射）。 */
public enum ErrorCode {
  BAD_REQUEST(40001, HttpStatus.BAD_REQUEST),
  UNAUTHENTICATED(40101, HttpStatus.UNAUTHORIZED),
  FORBIDDEN(40301, HttpStatus.FORBIDDEN),
  DATA_FORBIDDEN(40302, HttpStatus.FORBIDDEN),
  NOT_FOUND(40401, HttpStatus.NOT_FOUND),
  LOCK_CONFLICT(40901, HttpStatus.CONFLICT),
  VALIDATION_FAILED(42201, HttpStatus.UNPROCESSABLE_ENTITY),
  STATE_ACTION_NOT_ALLOWED(42202, HttpStatus.UNPROCESSABLE_ENTITY),
  GUARD_NOT_SATISFIED(42203, HttpStatus.UNPROCESSABLE_ENTITY),
  RATE_LIMITED(42901, HttpStatus.TOO_MANY_REQUESTS),
  INTERNAL_ERROR(50001, HttpStatus.INTERNAL_SERVER_ERROR);

  private final int code;
  private final HttpStatus httpStatus;

  ErrorCode(int code, HttpStatus httpStatus) {
    this.code = code;
    this.httpStatus = httpStatus;
  }

  public int code() {
    return code;
  }

  public HttpStatus httpStatus() {
    return httpStatus;
  }
}
