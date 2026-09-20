package net.zentao.platform.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** 错误信封（03 §2/§4）：{"error":{"code","message","fields"?,"traceId"}}。 */
public record ErrorEnvelope(Error error) {

  public record Error(
      int code,
      String message,
      @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> fields,
      String traceId) {}

  public static ErrorEnvelope of(int code, String message, Map<String, String> fields, String traceId) {
    return new ErrorEnvelope(new Error(code, message, fields == null || fields.isEmpty() ? null : fields, traceId));
  }
}
