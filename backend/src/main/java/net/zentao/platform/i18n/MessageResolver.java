package net.zentao.platform.i18n;

import java.util.Locale;
import net.zentao.platform.error.ApiException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * i18n 文案解析（T06）：把「按请求语言」与「按默认语言」两种口径收在一处。
 *
 * <p>两种口径各有用处——API 错误信封随请求语言（{@link #forRequest}），
 * 审计/日志等落库文案随**默认语言**（{@link #plain}，同一个失败在库里不该因请求语言不同而两样）。
 * 参数里是消息键的（如 `error.notFound` 的 `entity.account`）一并解析，缺键一律回落原文。
 */
@Component
public class MessageResolver {

  private final MessageSource messageSource;

  public MessageResolver(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /** 请求语言（Accept-Language / `?lang=`，由 RequestLocaleResolver 落进 LocaleContextHolder）下的文案。 */
  public String forRequest(String key, Object[] args) {
    return resolve(key, args, LocaleContextHolder.getLocale());
  }

  /** 无参键的简写（`forRequest(key, null)`）。 */
  public String forRequest(String key) {
    return forRequest(key, null);
  }

  /** 默认语言（zh-CN）文案：审计、日志等与请求语言无关的场景。 */
  public String plain(String key, Object[] args) {
    return resolve(key, args, Locale.SIMPLIFIED_CHINESE);
  }

  /**
   * 显式语言文案：**过滤器阶段**用——Servlet 过滤器早于 DispatcherServlet，`LocaleContextHolder`
   * 还没落值，此时 {@link #forRequest} 只会拿到 JVM 默认语言（见 {@code SurfaceGuardFilter}）。
   */
  public String forLocale(Locale locale, String key, Object[] args) {
    return resolve(key, args, locale);
  }

  /**
   * 异常的人类可读原因（默认语言）：带 i18n 键的按键解析，传裸串的开发兜底句原样返回。
   * 供审计「登录失败原因」这类落库文案使用——落库要人能读，不能存键。
   */
  public String reasonOf(ApiException e) {
    return e.messageKey() == null ? e.getMessage() : plain(e.messageKey(), e.messageArgs());
  }

  /**
   * 异常的人类可读原因（**请求语言**）：逐项结果（批量动作的行级 error）等即时响应用它——
   * `e.getMessage()` 在带键异常上是**键名**（设计如此：解析归本类），直接拼进响应会把键名显示给用户。
   */
  public String forRequest(ApiException e) {
    return e.messageKey() == null ? e.getMessage() : forRequest(e.messageKey(), e.messageArgs());
  }

  private String resolve(String key, Object[] args, Locale locale) {
    return messageSource.getMessage(key, localizedArgs(args, locale), key, locale);
  }

  /** 参数先按消息键查一次：查得到即换成该语言的名词，查不到（id、计数、未建键的词）原样带回。 */
  private Object[] localizedArgs(Object[] args, Locale locale) {
    if (args == null || args.length == 0) {
      return args;
    }
    Object[] localized = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      if (args[i] == null) {
        localized[i] = null;
        continue;
      }
      String raw = String.valueOf(args[i]);
      localized[i] = messageSource.getMessage(raw, null, raw, locale);
    }
    return localized;
  }
}
