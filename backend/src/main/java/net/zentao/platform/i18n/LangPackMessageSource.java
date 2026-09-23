package net.zentao.platform.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;

/**
 * 文案解析（T21）：**语言包 + 覆盖层**两级，与前端同源同规则。
 *
 * <ul>
 *   <li>默认值：{@link LangCatalog} 读的 `classpath:lang/*.json`——前端 `packages/i18n/src/locales/` 的字节副本
 *       （`check-lang-catalog` 门禁看护），故前后端是同**一份**文案真源，不是两套；</li>
 *   <li>覆盖值：`lang_item`（多语言上传写入）——Excel 一上传，错误信封 / 审计原因里的文案立即跟着变。</li>
 * </ul>
 *
 * <p>缓存：每语言一份覆盖表（覆盖行是管理面小表，通常几十到几百行）。上传成功后由
 * {@code LangImportService} 调 {@link #invalidate()} 清缓存。
 * ponytail: 缓存是进程内的（本项目单实例部署）；将来多实例要换共享缓存或版本号广播。
 */
public class LangPackMessageSource implements MessageSource {

  private static final String LANG_ZH_CN = "zh-cn";
  private static final String LANG_EN = "en";

  private final LangCatalog catalog;
  private final LangOverrideQueryService overrides;
  private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

  public LangPackMessageSource(LangCatalog catalog, LangOverrideQueryService overrides) {
    this.catalog = catalog;
    this.overrides = overrides;
  }

  /** 语言包或覆盖层变更后清缓存（多语言上传成功后调用）。 */
  public void invalidate() {
    cache.clear();
  }

  @Override
  public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
    String pattern = pattern(code, locale);
    return pattern == null ? defaultMessage : format(pattern, args, locale);
  }

  @Override
  public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
    String pattern = pattern(code, locale);
    if (pattern == null) {
      throw new NoSuchMessageException(code, locale);
    }
    return format(pattern, args, locale);
  }

  @Override
  public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
    for (String code : resolvable.getCodes() == null ? new String[0] : resolvable.getCodes()) {
      String pattern = pattern(code, locale);
      if (pattern != null) {
        return format(pattern, resolvable.getArguments(), locale);
      }
    }
    if (resolvable.getDefaultMessage() != null) {
      return format(resolvable.getDefaultMessage(), resolvable.getArguments(), locale);
    }
    throw new NoSuchMessageException(resolvable.getCodes() == null || resolvable.getCodes().length == 0
        ? "" : resolvable.getCodes()[0], locale);
  }

  /** 语言码：界面只支持 zh-CN / en（RequestLocaleResolver 已收口），其余一律按 en。 */
  static String langCode(Locale locale) {
    return locale != null && "zh".equals(locale.getLanguage()) ? LANG_ZH_CN : LANG_EN;
  }

  private String pattern(String code, Locale locale) {
    if (code == null || code.isEmpty()) {
      return null;
    }
    String lang = langCode(locale);
    String override = overridesFor(lang).get(code);
    if (override != null && !override.isEmpty()) {
      return override;
    }
    return catalog.defaults(lang).get(code);
  }

  private Map<String, String> overridesFor(String lang) {
    return cache.computeIfAbsent(lang, overrides::byKey);
  }

  /** 无参数不走 MessageFormat（与旧 ResourceBundleMessageSource 同口径：单引号等原样保留）。 */
  private static String format(String pattern, Object[] args, Locale locale) {
    if (args == null || args.length == 0) {
      return pattern;
    }
    return new MessageFormat(pattern, locale).format(args);
  }
}
