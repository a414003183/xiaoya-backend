package net.zentao.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Locale;
import net.zentao.platform.i18n.MessageResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

/**
 * T55 唯一约束冲突映射：并发窗口里的重复（顺序路径都有守卫，顺序可达的那条见 ApiExceptionMappingApiTest）
 * 按约束名分档——序号类唯一键 = 并发冲突 40901，其余 = 业务重复 42201。
 *
 * <p>同时锁死「不过度映射」：其余完整性错误（NOT NULL / FK / 截断）必须继续落到 50001 兜底，
 * 否则真 bug 会被伪装成用户输入错误。
 */
class DuplicateKeyMappingTest {

  /** 文案解析替身：恒回键名——断言里就能看出用的是哪个文案键（真解析归 LangPackMessageSource 用例）。 */
  private static final ApiExceptionMapper MAPPER = new ApiExceptionMapper(new MessageResolver(new MessageSource() {
    @Override
    public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
      return defaultMessage;
    }

    @Override
    public String getMessage(String code, Object[] args, Locale locale) {
      return code;
    }

    @Override
    public String getMessage(MessageSourceResolvable resolvable, Locale locale) {
      return resolvable.getDefaultMessage();
    }
  }));

  private static final ExceptionHandlerMethodResolver HANDLERS =
      new ExceptionHandlerMethodResolver(ApiExceptionMapper.class);

  /** MySQL 8：值在前（`'admin'` 不是约束名），约束名带表名前缀。 */
  private static DuplicateKeyException mysql(String constraint) {
    return new DuplicateKeyException("### Error updating database",
        new SQLException("Duplicate entry 'admin' for key '" + constraint + "'"));
  }

  /** H2：引号里第一段是约束名，后面还拼着生成的索引名。 */
  private static DuplicateKeyException h2(String constraint) {
    return new DuplicateKeyException("### Error updating database",
        new SQLException("Unique index or primary key violation: \"" + constraint + " INDEX public."
            + constraint + "_INDEX_2 ON public.account(account NULLS FIRST) "
            + "VALUES ( /* key:1 */ 'admin')\"; SQL statement: insert into account …"));
  }

  @Test
  @DisplayName("MySQL 式重复（uq_account）→ 42201（422），fields 空")
  void mysqlDuplicate() {
    ResponseEntity<ErrorEnvelope> response = MAPPER.onDuplicateKey(mysql("account.uq_account"));

    assertEquals(422, response.getStatusCode().value());
    assertEquals(42201, response.getBody().error().code());
    assertEquals("error.duplicate", response.getBody().error().message());
    assertNull(response.getBody().error().fields(), "不猜字段：约束名到请求字段名不可靠");
  }

  @Test
  @DisplayName("H2 式重复（约束名 + 生成索引名）→ 42201（422）")
  void h2Duplicate() {
    ResponseEntity<ErrorEnvelope> response = MAPPER.onDuplicateKey(h2("uk_case_step"));

    assertEquals(422, response.getStatusCode().value());
    assertEquals(42201, response.getBody().error().code());
  }

  @Test
  @DisplayName("版本号类唯一键 → 40901（409），与 PublishDocHandler 同口径")
  void versionedDuplicate() {
    ResponseEntity<ErrorEnvelope> mysqlResponse =
        MAPPER.onDuplicateKey(mysql("doc_content.uk_doc_content_doc_version"));
    ResponseEntity<ErrorEnvelope> h2Response = MAPPER.onDuplicateKey(h2("uk_doc_content_doc_version"));

    assertEquals(409, mysqlResponse.getStatusCode().value());
    assertEquals(40901, mysqlResponse.getBody().error().code());
    assertEquals("error.lockConflict", mysqlResponse.getBody().error().message());
    assertEquals(409, h2Response.getStatusCode().value());
    assertEquals(40901, h2Response.getBody().error().code());
  }

  @Test
  @DisplayName("消息里取不到约束名 → 42201（宁可当业务重复，也不猜成并发冲突）")
  void unnamedDuplicate() {
    ResponseEntity<ErrorEnvelope> response =
        MAPPER.onDuplicateKey(new DuplicateKeyException("duplicate key"));

    assertEquals(42201, response.getBody().error().code());
  }

  @Test
  @DisplayName("不过度映射：只有 DuplicateKeyException 命中新处理器，其余完整性错误仍 50001")
  void otherIntegrityErrorsStillInternal() {
    Method duplicate = HANDLERS.resolveMethodByExceptionType(DuplicateKeyException.class);
    Method notNull = HANDLERS.resolveMethodByExceptionType(DataIntegrityViolationException.class);
    DataIntegrityViolationException truncated = new DataIntegrityViolationException(
        "### Error updating database", new SQLException("Data too long for column 'TITLE'"));

    assertNotNull(duplicate);
    assertEquals("onDuplicateKey", duplicate.getName());
    assertNotNull(notNull);
    assertEquals("onUnknown", notNull.getName());
    assertEquals(50001, MAPPER.onUnknown(truncated).getBody().error().code());
  }
}
