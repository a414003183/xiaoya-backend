package net.zentao.platform.i18n;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zentao.platform.meta.LangItemPO;
import net.zentao.platform.meta.LangItemRepository;
import org.springframework.stereotype.Component;

/**
 * 文案覆盖层全量读取（platform 卡 §3.12）：把 lang_item 的存储拆分还原成全点分键。
 *
 * <p>两个消费方：前端运行时合并（GET /lang-items/overrides，按当前语言一次拉全量）与语言包导出
 * （导出值 = 覆盖优先、内建默认为底）。键映射规则见 {@link LangCatalog#parts}。
 */
@Component
public class LangOverrideQueryService {

  private final LangItemRepository langItems;

  public LangOverrideQueryService(LangItemRepository langItems) {
    this.langItems = langItems;
  }

  /** LangOverrideList 载荷（该语言覆盖层全量，不分页）。 */
  public record LangOverrideList(List<LangOverrideView> items) {}

  /** LangOverrideView（contract）：key 为全点分键（与语言包键同形）。 */
  public record LangOverrideView(String lang, String domain, String section, String key, String value) {}

  /** 全点分键 → 覆盖值（导出按需查默认值）。 */
  public Map<String, String> byKey(String lang) {
    Map<String, String> overrides = new LinkedHashMap<>();
    for (LangItemPO row : langItems.rows(lang)) {
      overrides.put(LangCatalog.fullKey(row.getDomain(), row.getSection(), row.getItemKey()), row.getItemValue());
    }
    return overrides;
  }

  public LangOverrideList overrides(String lang) {
    List<LangOverrideView> items = new ArrayList<>();
    for (LangItemPO row : langItems.rows(lang)) {
      items.add(new LangOverrideView(
          row.getLang(),
          row.getDomain(),
          row.getSection(),
          LangCatalog.fullKey(row.getDomain(), row.getSection(), row.getItemKey()),
          row.getItemValue()));
    }
    return new LangOverrideList(items);
  }
}
