package net.zentao.platform.i18n;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;

/**
 * 后端 i18n 装配（T06 起；T21 起文案真源与前端**同一份**）。
 *
 * <p>文案两级，见 {@link LangPackMessageSource}：语言包默认值（`classpath:lang/*.json`，前端语言包的
 * 字节副本）+ `lang_item` 覆盖层（多语言上传写入）。原 `messages_*.properties` 已并入语言包（T21）：
 * 前后端不再各存一套文案，Excel 导出/上传一次覆盖两侧。
 *
 * <p>缺失键由调用方就近回落（{@link net.zentao.platform.web.ApiExceptionMapper} 回落键名本身），
 * 故文案迁移可以逐域推进。
 */
@Configuration
public class I18nConfig {

  @Bean
  public LangPackMessageSource messageSource(LangCatalog catalog, LangOverrideQueryService overrides) {
    return new LangPackMessageSource(catalog, overrides);
  }

  @Bean
  public LocaleResolver localeResolver() {
    return new RequestLocaleResolver();
  }
}
