package net.zentao.platform.error;

import java.util.Map;

/**
 * 全系统唯一业务异常；由 ApiExceptionMapper 统一映射为错误信封（03 §2/§4）。
 *
 * <p>文案（T06/T22/T63）：只走 i18n 键（{@link #keyed}）——键在**语言包**（`src/main/resources/lang/*.json`，
 * 前端 `zh-CN.json`/`en.json` 的字节拷贝，`check-lang-catalog --fix` 同步）里按请求语言解析，
 * 键存在性由 `check-backend-lang-keys` 门禁看护；`messages*.properties` 已随 T22 删除。
 * 传裸字符串的工厂已随 T63 删除（`check-keyed-exceptions` 门禁看护不再回流）。
 * 前端界面文案按 `code` 本地化（`errorText(error, t)`），故两套口径并存不冲突。
 */
public final class ApiException extends RuntimeException {

  private final ErrorCode errorCode;
  private final Map<String, String> fields;
  /** i18n 消息键；非空即由 ApiExceptionMapper 按请求语言解析（null = message 原样透出）。 */
  private final String messageKey;
  private final Object[] messageArgs;

  private ApiException(
      ErrorCode errorCode, String message, String messageKey, Object[] messageArgs, Map<String, String> fields) {
    super(message);
    this.errorCode = errorCode;
    this.messageKey = messageKey;
    this.messageArgs = messageArgs;
    this.fields = fields;
  }

  /**
   * 按 i18n 键构造：键见语言包 `lang/zh-CN.json` / `lang/en.json`（与前端文案文件字节同步）。
   * 键在两套文案里都缺失时回落键名本身（不炸，见 ApiExceptionMapper）。
   */
  public static ApiException keyed(ErrorCode errorCode, String messageKey, Object... messageArgs) {
    return new ApiException(errorCode, messageKey, messageKey, messageArgs, Map.of());
  }

  /** 字段级校验失败（42201）带 i18n 键：`fields` 是「字段 → 原因码」，原因文案由前端按码本地化（词表见 {@link FieldReasons}）。 */
  public static ApiException keyed(
      ErrorCode errorCode, Map<String, String> fields, String messageKey, Object... messageArgs) {
    return new ApiException(errorCode, messageKey, messageKey, messageArgs, fields);
  }

  /** 「{0}不存在。」——唯一的 notFound 出口，全仓 78 处调用点由此一处受益。 */
  public static ApiException notFound(String what) {
    return keyed(ErrorCode.NOT_FOUND, "error.notFound", what);
  }

  /** 乐观锁冲突的通用句（「数据已被他人修改，请刷新。」）走 i18n。 */
  public static ApiException lockConflict() {
    return keyed(ErrorCode.LOCK_CONFLICT, "error.lockConflict");
  }

  /** 「字段校验失败。」——唯一出口，全仓 225 处调用点由此一处受益。 */
  public static ApiException validation(Map<String, String> fields) {
    return keyed(ErrorCode.VALIDATION_FAILED, fields, "error.validation.failed");
  }

  public ErrorCode errorCode() {
    return errorCode;
  }

  public Map<String, String> fields() {
    return fields;
  }

  public String messageKey() {
    return messageKey;
  }

  public Object[] messageArgs() {
    return messageArgs;
  }
}
