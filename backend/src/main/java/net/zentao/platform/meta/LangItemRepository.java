package net.zentao.platform.meta;

import com.mybatisflex.core.query.QueryColumn;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** lang_item 表读写（platform 卡 §3.8）：覆盖层 upsert 同键不增行；DELETE 即恢复内建默认。 */
@Component
public class LangItemRepository {

  private static final QueryColumn LANG = new QueryColumn("lang");
  private static final QueryColumn DOMAIN = new QueryColumn("domain");
  private static final QueryColumn SECTION = new QueryColumn("section");
  private static final QueryColumn ITEM_KEY = new QueryColumn("item_key");

  private final LangItemMapper mapper;

  public LangItemRepository(LangItemMapper mapper) {
    this.mapper = mapper;
  }

  public Map<String, String> overrides(String lang, String domain, String section) {
    Map<String, String> items = new LinkedHashMap<>();
    for (LangItemPO po : mapper.selectListByCondition(
        LANG.eq(lang).and(DOMAIN.eq(domain)).and(SECTION.eq(section)))) {
      items.put(po.getItemKey(), po.getItemValue());
    }
    return items;
  }

  /** 该语言的全部覆盖行（不分页、无域过滤）：/lang-items/overrides 与语言包导出共用。 */
  public List<LangItemPO> rows(String lang) {
    return mapper.selectListByCondition(LANG.eq(lang));
  }

  public void upsert(String lang, String domain, String section, Map<String, String> items) {
    for (Map.Entry<String, String> entry : items.entrySet()) {
      LangItemPO existing = mapper.selectOneByCondition(LANG.eq(lang)
          .and(DOMAIN.eq(domain))
          .and(SECTION.eq(section))
          .and(ITEM_KEY.eq(entry.getKey())));
      if (existing == null) {
        LangItemPO po = new LangItemPO();
        po.setLang(lang);
        po.setDomain(domain);
        po.setSection(section);
        po.setItemKey(entry.getKey());
        po.setItemValue(entry.getValue());
        mapper.insert(po);
        continue;
      }
      existing.setItemValue(entry.getValue());
      mapper.update(existing);
    }
  }

  public void delete(String lang, String domain, String section) {
    mapper.deleteByCondition(LANG.eq(lang).and(DOMAIN.eq(domain)).and(SECTION.eq(section)));
  }

  public List<LangItemPO> all() {
    return mapper.selectAll();
  }
}
