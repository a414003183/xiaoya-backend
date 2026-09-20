package net.zentao.platform.meta;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

/** lang_item 表 PO（platform 卡 §3.8）：仅存覆盖层。 */
@Table("lang_item")
public class LangItemPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private String lang;
  private String domain;
  private String section;
  private String itemKey;
  private String itemValue;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getLang() {
    return lang;
  }

  public void setLang(String lang) {
    this.lang = lang;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  public String getSection() {
    return section;
  }

  public void setSection(String section) {
    this.section = section;
  }

  public String getItemKey() {
    return itemKey;
  }

  public void setItemKey(String itemKey) {
    this.itemKey = itemKey;
  }

  public String getItemValue() {
    return itemValue;
  }

  public void setItemValue(String itemValue) {
    this.itemValue = itemValue;
  }
}
