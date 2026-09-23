package net.zentao.platform.web;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.error.FieldReasons;
import net.zentao.platform.i18n.MessageResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常 → 错误信封映射（01 §2.4）。
 * i18n（T06/T22）：`ApiException` 带键的走**语言包**（`src/main/resources/lang/*.json`，
 * 前端文案文件的字节拷贝，键存在性由 `check-backend-lang-keys` 看护；`messages*.properties` 已随 T22 删除）
 * 按请求语言解析（Accept-Language，见 {@code net.zentao.platform.i18n.RequestLocaleResolver}）；
 * 本类自己的通用错误也走键——`code` 与 HTTP 状态不变，前端 `errorText(error, t)` 的按码本地化零改动。
 * 校验异常的字段级原因码取自 {@link net.zentao.platform.error.FieldReasons} 词表（T63）。
 */
@RestControllerAdvice
public class ApiExceptionMapper {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionMapper.class);

  private final MessageResolver messages;

  public ApiExceptionMapper(MessageResolver messages) {
    this.messages = messages;
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorEnvelope> onApiException(ApiException e) {
    // 响应文案随**请求语言**；审计/日志落库才用默认语言（MessageResolver#reasonOf）
    String message = e.messageKey() == null ? e.getMessage() : messages.forRequest(e.messageKey(), e.messageArgs());
    return build(e.errorCode(), message, e.fields());
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, MethodValidationException.class})
  public ResponseEntity<ErrorEnvelope> onValidation(Exception e) {
    return build(ErrorCode.VALIDATION_FAILED, text("error.validation.failed"), ValidationErrors.extract(e));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorEnvelope> onUnreadable(HttpMessageNotReadableException e) {
    return build(ErrorCode.BAD_REQUEST, text("error.body.unreadable"), Map.of());
  }

  @ExceptionHandler({
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.MissingPathVariableException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class
  })
  public ResponseEntity<ErrorEnvelope> onMissingParam(Exception e) {
    return build(ErrorCode.BAD_REQUEST, text("error.param.missing"), Map.of());
  }

  /**
   * 请求方法 / 媒体类型 / 路径不存在（06 A6-4：schemathesis 打靶暴露——原先都落 onUnknown 变 50001）。
   * 段位口径依 03 §4：400xx=参数/请求错误、404xx=不存在；不为 405 单独取号。
   */
  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorEnvelope> onMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException e) {
    return build(ErrorCode.BAD_REQUEST, text("error.method.unsupported"), Map.of());
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorEnvelope> onMediaTypeNotSupported(org.springframework.web.HttpMediaTypeNotSupportedException e) {
    return build(ErrorCode.BAD_REQUEST, text("error.mediaType.unsupported"), Map.of());
  }

  /**
   * 容器级上传闸门（T60 / SEC-09）：`spring.servlet.multipart.max-file-size`（50MB）超限时，
   * Spring 的多段解析抛出本异常——此前落 {@link #onUnknown} 变 50001，客户端拿到的是"服务内部错误"，
   * 而真相是"文件太大，换个小的"（可行动结论）。与业务侧的上传守卫同码同字段（42201 + `fields.file=tooLarge`）。
   *
   * <p>要让这个 422 真能送到客户端，Tomcat 还需能吞掉未读的请求体（`server.tomcat.max-swallow-size`，
   * 见 application.yml）——否则超限上传会被断连，客户端看到的是网络错误。
   */
  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorEnvelope> onUploadTooLarge(
      org.springframework.web.multipart.MaxUploadSizeExceededException e) {
    return build(ErrorCode.VALIDATION_FAILED, text("error.upload.tooLarge"), Map.of("file", "tooLarge"));
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  public ResponseEntity<ErrorEnvelope> onNoResource(org.springframework.web.servlet.resource.NoResourceFoundException e) {
    return build(ErrorCode.NOT_FOUND, text("error.resource.notFound"), Map.of());
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
      fields.putIfAbsent(name, FieldReasons.of(violation));
    }
    return build(ErrorCode.VALIDATION_FAILED, text("error.validation.params"), fields);
  }

  /** Tomcat 在解析畸形 query（无效 chunk）时抛的客户端错误，同为 40001 而非 50001。 */
  @ExceptionHandler(org.apache.tomcat.util.http.InvalidParameterException.class)
  public ResponseEntity<ErrorEnvelope> onInvalidQueryParameter(
      org.apache.tomcat.util.http.InvalidParameterException e) {
    return build(ErrorCode.BAD_REQUEST, text("error.param.invalid"), Map.of());
  }

  /**
   * 唯一约束冲突（T55 / AUDIT BE-02）：check-then-act 的写路径顺序上都有 exists 守卫（42201 字段级），
   * 只有并发窗口里「先查后插」的两个请求会一起到 DB——由唯一键兜底拦下，落这里。
   *
   * <p>分档：约束名含 `version`（序号类唯一键，如 `uk_doc_content_doc_version`）= 真并发冲突 → 40901，
   * 与 {@code PublishDocHandler}（并发插同版本快照）同一口径；其余 = 业务重复 → 42201。
   * `fields` 恒空：约束名到请求字段名不可靠（H2 消息里还拼着索引名 `…_INDEX_n`），宁可给通用文案也不猜字段；
   * 要字段级请在调用点保留守卫。
   *
   * <p>**只接 `DuplicateKeyException`**——NOT NULL / FK / 截断 / CHECK 等其余完整性错误是「缺守卫的 bug」，
   * 继续走 {@link #onUnknown} 的 50001 + error 日志，不美化成 4xx。
   */
  @ExceptionHandler(DuplicateKeyException.class)
  public ResponseEntity<ErrorEnvelope> onDuplicateKey(DuplicateKeyException e) {
    String constraint = DuplicateKey.constraintOf(e);
    boolean versioned = constraint != null && constraint.toLowerCase(Locale.ROOT).contains("version");
    if (constraint == null) {
      // 方言不在预期内（换驱动 / 改消息格式）：把原始异常记全，否则排查时只剩一个 null
      log.atWarn().setCause(e).log("唯一约束冲突（约束名未识别）traceId={}", MDC.get("traceId"));
    } else {
      // WARN 而非 error：被拦下的业务冲突，不是未处理异常
      log.atWarn().log("唯一约束冲突 constraint={} traceId={}", constraint, MDC.get("traceId"));
    }
    return versioned
        ? build(ErrorCode.LOCK_CONFLICT, text("error.lockConflict"), Map.of())
        : build(ErrorCode.VALIDATION_FAILED, text("error.duplicate"), Map.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorEnvelope> onUnknown(Exception e) {
    log.atError().setCause(e).log("未处理异常 traceId={}", MDC.get("traceId"));
    return build(ErrorCode.INTERNAL_ERROR, text("error.internal"), Map.of());
  }

  /** 键 → 当前请求语言文案；缺键回落键名本身（迁移期间不会因漏配文案而炸）。 */
  private String text(String key, Object... args) {
    return messages.forRequest(key, args);
  }

  private ResponseEntity<ErrorEnvelope> build(ErrorCode code, String message, Map<String, String> fields) {
    var body = ErrorEnvelope.of(code.code(), message, fields, MDC.get("traceId"));
    return ResponseEntity.status(HttpStatus.valueOf(code.httpStatus().value())).body(body);
  }

  /**
   * 从 Bean Validation 异常提取字段级错误（42201 的 fields）。
   * 原因值取自 {@link FieldReasons} 词表（约束元数据 → 原因码），不透传注解默认文案——
   * 前端按码本地化（`SERVER_FIELD_ERROR_KEYS`），文案进过信封就不可机判（BE-09）。
   */
  private static final class ValidationErrors {

    private ValidationErrors() {}

    static Map<String, String> extract(Exception e) {
      var fields = new java.util.LinkedHashMap<String, String>();
      if (e instanceof MethodArgumentNotValidException manv) {
        for (var fe : manv.getBindingResult().getFieldErrors()) {
          fields.putIfAbsent(fe.getField(), reasonOf(fe));
        }
        return fields;
      }
      if (e instanceof MethodValidationException mv) {
        for (var result : mv.getParameterValidationResults()) {
          String name = result.getMethodParameter().getParameterName();
          if (name == null) {
            continue;
          }
          String reason = result.getResolvableErrors().stream()
              .map(error -> reasonOf(result, error))
              .findFirst()
              .orElse(FieldReasons.INVALID);
          fields.putIfAbsent(name, reason);
        }
        return fields;
      }
      throw new IllegalStateException("非校验异常进入映射: " + e.getClass(), e);
    }

    /** 约束违例可解包就按元数据映射；解包不到（绑定/类型转换失败）按 codes 末位兜底。 */
    private static String reasonOf(org.springframework.validation.FieldError fe) {
      try {
        var violation = fe.unwrap(jakarta.validation.ConstraintViolation.class);
        if (violation != null) {
          return FieldReasons.of(violation);
        }
      } catch (RuntimeException ignored) {
        // 非 Bean Validation 来源 → codes 兜底
      }
      return reasonOfCodes(fe.getCodes());
    }

    private static String reasonOf(
        org.springframework.validation.method.ParameterValidationResult result,
        org.springframework.context.MessageSourceResolvable error) {
      try {
        var violation = result.unwrap(error, jakarta.validation.ConstraintViolation.class);
        if (violation != null) {
          return FieldReasons.of(violation);
        }
      } catch (RuntimeException ignored) {
        // 非 Bean Validation 来源 → codes 兜底
      }
      return reasonOfCodes(error.getCodes());
    }

    /** codes 末位 = 约束注解简单名（Spring DefaultMessageCodesResolver 约定），如 NotBlank / Size / typeMismatch。 */
    private static String reasonOfCodes(String[] codes) {
      return codes == null || codes.length == 0
          ? FieldReasons.INVALID
          : FieldReasons.ofConstraint(codes[codes.length - 1]);
    }
  }

  /**
   * 驱动消息里的唯一约束名，两种方言各一个样本（提取不到回落 null——只影响 40901 的分档）：
   *
   * <ul>
   *   <li>MySQL 8：{@code Duplicate entry 'admin' for key 'account.uq_account'}（带表名前缀）；</li>
   *   <li>H2：{@code Unique index or primary key violation: "public.uk_case_step INDEX
   *       public.uk_case_step_INDEX_2 ON public.case_step(…)"}——引号里第一段就是约束名，
   *       后面还跟着生成的索引名（故必须先截到空格）。</li>
   * </ul>
   */
  private static final class DuplicateKey {

    private static final Pattern MYSQL = Pattern.compile("for key '([^']+)'");
    private static final Pattern H2 = Pattern.compile("violation: \"([^\"]+)\"");

    private DuplicateKey() {}

    static String constraintOf(Throwable failure) {
      String message = NestedExceptionUtils.getMostSpecificCause(failure).getMessage();
      if (message == null) {
        return null;
      }
      for (Pattern pattern : List.of(MYSQL, H2)) {
        var matcher = pattern.matcher(message);
        if (!matcher.find()) {
          continue;
        }
        String quoted = matcher.group(1);
        int tail = quoted.indexOf(' ');
        String named = tail < 0 ? quoted : quoted.substring(0, tail);
        int dot = named.lastIndexOf('.');
        return dot < 0 ? named : named.substring(dot + 1);
      }
      return null;
    }
  }
}
