package net.zentao.platform.web;

import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 全局异常 → 错误信封映射（01 §2.4）。i18n 文案（Accept-Language）P1 落地，P0 为中文字面量。 */
@RestControllerAdvice
public class ApiExceptionMapper {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionMapper.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorEnvelope> onApiException(ApiException e) {
    return build(e.errorCode(), e.getMessage(), e.fields());
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, MethodValidationException.class})
  public ResponseEntity<ErrorEnvelope> onValidation(Exception e) {
    return build(ErrorCode.VALIDATION_FAILED, "字段校验失败。", ValidationErrors.extract(e));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorEnvelope> onUnreadable(HttpMessageNotReadableException e) {
    return build(ErrorCode.BAD_REQUEST, "请求体格式错误。", Map.of());
  }

  @ExceptionHandler({
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.MissingPathVariableException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class
  })
  public ResponseEntity<ErrorEnvelope> onMissingParam(Exception e) {
    return build(ErrorCode.BAD_REQUEST, "缺少必填参数。", Map.of());
  }

  /**
   * 请求方法 / 媒体类型 / 路径不存在（06 A6-4：schemathesis 打靶暴露——原先都落 onUnknown 变 50001）。
   * 段位口径依 03 §4：400xx=参数/请求错误、404xx=不存在；不为 405 单独取号。
   */
  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorEnvelope> onMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException e) {
    return build(ErrorCode.BAD_REQUEST, "请求方法不被支持。", Map.of());
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorEnvelope> onMediaTypeNotSupported(org.springframework.web.HttpMediaTypeNotSupportedException e) {
    return build(ErrorCode.BAD_REQUEST, "请求媒体类型不被支持。", Map.of());
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  public ResponseEntity<ErrorEnvelope> onNoResource(org.springframework.web.servlet.resource.NoResourceFoundException e) {
    return build(ErrorCode.NOT_FOUND, "资源不存在。", Map.of());
  }

  /**
   * 方法参数上的 Bean Validation（如 list 端点的 @Max(200)）原先落 onUnknown 变 50001——06 A6-4 打靶暴露。
   * 归 42201（字段校验失败）并下传 fields，与请求体校验同口径（03 §4：42201 带 fields）。
   */
  @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
  public ResponseEntity<ErrorEnvelope> onConstraintViolation(jakarta.validation.ConstraintViolationException e) {
    var fields = new java.util.LinkedHashMap<String, String>();
    for (var violation : e.getConstraintViolations()) {
      String path = violation.getPropertyPath().toString();
      String name = path.substring(path.lastIndexOf('.') + 1);
      fields.putIfAbsent(name, "invalid");
    }
    return build(ErrorCode.VALIDATION_FAILED, "参数校验失败。", fields);
  }

  /** Tomcat 在解析畸形 query（无效 chunk）时抛的客户端错误，同为 40001 而非 50001。 */
  @ExceptionHandler(org.apache.tomcat.util.http.InvalidParameterException.class)
  public ResponseEntity<ErrorEnvelope> onInvalidQueryParameter(
      org.apache.tomcat.util.http.InvalidParameterException e) {
    return build(ErrorCode.BAD_REQUEST, "请求参数格式非法。", Map.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorEnvelope> onUnknown(Exception e) {
    log.atError().setCause(e).log("未处理异常 traceId={}", MDC.get("traceId"));
    return build(ErrorCode.INTERNAL_ERROR, "服务内部错误。", Map.of());
  }

  private ResponseEntity<ErrorEnvelope> build(ErrorCode code, String message, Map<String, String> fields) {
    var body = ErrorEnvelope.of(code.code(), message, fields, MDC.get("traceId"));
    return ResponseEntity.status(HttpStatus.valueOf(code.httpStatus().value())).body(body);
  }

  /** 从 Bean Validation 异常提取字段级错误（42201 的 fields）。 */
  private static final class ValidationErrors {

    private ValidationErrors() {}

    static Map<String, String> extract(Exception e) {
      var fields = new java.util.LinkedHashMap<String, String>();
      if (e instanceof MethodArgumentNotValidException manv) {
        for (var fe : manv.getBindingResult().getFieldErrors()) {
          fields.putIfAbsent(fe.getField(), messageOf(fe.getDefaultMessage()));
        }
        return fields;
      }
      if (e instanceof MethodValidationException mv) {
        for (var result : mv.getParameterValidationResults()) {
          String name = result.getMethodParameter().getParameterName();
          if (name == null) {
            continue;
          }
          String message = result.getResolvableErrors().stream()
              .map(org.springframework.context.MessageSourceResolvable::getDefaultMessage)
              .filter(java.util.Objects::nonNull)
              .findFirst()
              .orElse(null);
          fields.putIfAbsent(name, messageOf(message));
        }
        return fields;
      }
      throw new IllegalStateException("非校验异常进入映射: " + e.getClass(), e);
    }

    private static String messageOf(String message) {
      return message == null ? "invalid" : message;
    }
  }
}
