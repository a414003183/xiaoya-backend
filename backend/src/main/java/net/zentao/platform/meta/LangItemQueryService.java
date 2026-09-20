package net.zentao.platform.meta;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 文案读取（platform 卡 §3.8）：内建默认 + 覆盖合并（overridden 标记）。 */
@Component
public class LangItemQueryService {

  private final LangItemRepository repository;

  public LangItemQueryService(LangItemRepository repository) {
    this.repository = repository;
  }

  public MergedLangItems merged(String lang, String domain, String section) {
    Map<String, String> overrides = repository.overrides(lang, domain, section);
    // ponytail: 内建默认值 P1 置空（语言包资产真源在前端 packages/i18n）；P2 起随各域补服务端默认表后在此合并。
    return new MergedLangItems(new LinkedHashMap<>(overrides), !overrides.isEmpty());
  }

  /** GET /lang-items/{domain}/{field} 载荷（LangItemView）。 */
  public record MergedLangItems(Map<String, String> items, boolean overridden) {}
}
