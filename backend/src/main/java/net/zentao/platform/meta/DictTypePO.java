package net.zentao.platform.meta;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

/** dict_type 表 PO（T16 P1-4；DB 字典只扩展代码注册的内置字典）。 */
@Table("dict_type")
public class DictTypePO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private String code;
  private String name;
  private String status;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
