package net.zentao.platform.meta;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 文案覆盖保存/恢复默认（platform 卡 §5，权限码 lang-manage 在端点注解判定）。 */
@Component
public class SaveLangItemHandler {

  private final LangItemRepository repository;

  public SaveLangItemHandler(LangItemRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void save(String lang, String domain, String section, Map<String, String> items) {
    repository.upsert(lang, domain, section, items);
  }

  @Transactional
  public Map<String, String> restoreDefaults(String lang, String domain, String section) {
    repository.delete(lang, domain, section);
    return Map.of();
  }
}
