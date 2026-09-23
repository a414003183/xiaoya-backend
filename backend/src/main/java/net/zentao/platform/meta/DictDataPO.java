package net.zentao.platform.meta;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

/** dict_data 表 PO（T16 P1-4；item_label 是给用户看的字面文案，item_value 是存储值）。 */
@Table("dict_data")
public class DictDataPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private String typeCode;
  private String itemLabel;
  private String itemValue;
  private Integer sortNo;
  private String status;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTypeCode() {
    return typeCode;
  }

  public void setTypeCode(String typeCode) {
    this.typeCode = typeCode;
  }

  public String getItemLabel() {
    return itemLabel;
  }

  public void setItemLabel(String itemLabel) {
    this.itemLabel = itemLabel;
  }

  public String getItemValue() {
    return itemValue;
  }

  public void setItemValue(String itemValue) {
    this.itemValue = itemValue;
  }

  public Integer getSortNo() {
    return sortNo;
  }

  public void setSortNo(Integer sortNo) {
    this.sortNo = sortNo;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
