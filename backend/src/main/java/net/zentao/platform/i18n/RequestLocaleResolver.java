package net.zentao.platform.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * 请求语言解析（T06 / 用户事项 5）：**Accept-Language 优先，`?lang=` 参数可单请求覆盖**。
 *
 * <p>为什么还要参数覆盖：浏览器发的 Accept-Language 是操作系统语言，不是界面里选的语言——排查问题、
 * curl 复现、以及将来前端把界面语言显式传给后端时，都需要一个不改请求头的开关。
 *
 * <p>注意：`lang` 在 {@code /lang-items/*} 上是**业务参数**（取哪一语言的覆盖行）。那边传 `lang=en`
 * 会连带把该请求的错误文案切成英文；影响只限这几个端点，且前端按错误码本地化文案
 * （`errorText(error, t)`），故接受这一重叠（真要分离时改用 `X-Zentao-Lang` 之类的专用头）。
 */
public class RequestLocaleResolver implements LocaleResolver {

  /** 支持的界面语言；不在表内一律回落第一个（= 默认 zh-CN）。 */
  private static final List<Locale> SUPPORTED = List.of(Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH);

  private final AcceptHeaderLocaleResolver delegate = new AcceptHeaderLocaleResolver();

  public RequestLocaleResolver() {
    delegate.setDefaultLocale(SUPPORTED.getFirst());
    delegate.setSupportedLocales(SUPPORTED);
  }

  @Override
  public Locale resolveLocale(HttpServletRequest request) {
    String override = langOverride(request);
    if (override != null && !override.isBlank()) {
      Locale parsed = Locale.forLanguageTag(override.replace('_', '-'));
      for (Locale supported : SUPPORTED) {
        if (supported.getLanguage().equals(parsed.getLanguage())) {
          return supported;
        }
      }
    }
    return delegate.resolveLocale(request);
  }

  /**
   * 读 `?lang=` 的防御式包装：畸形 query（`?=null` 之类）在**首次**取参数时抛 Tomcat
   * {@code InvalidParameterException}——它是请求错误，本该由 {@code ApiExceptionMapper#onInvalidQueryParameter}
   * 映射成 40001。
   *
   * <p>为什么会落到这里：任何异常信封都要先解析请求语言（{@code MessageResolver#forRequest}），于是**为了本地化
   * 一个错误文案**而把参数解析先触发了一遍，那个 InvalidParameterException 就逃出 @ExceptionHandler，
   * 结果 40001/40101/40301 全变成裸 50001（T50 实证：拦截器的默认态在 handler 之前构造 40101 信封时撞上）。
   * 语言是可选的展示项，读不到就回落 Accept-Language，绝不因为一个可选参数把错误面炸掉。
   */
  private static String langOverride(HttpServletRequest request) {
    try {
      return request.getParameter("lang");
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  /** 语言只随请求走（Accept-Language / `?lang=`），不在会话或 Cookie 里落任何状态。 */
  @Override
  public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
    delegate.setLocale(request, response, locale);
  }
}
