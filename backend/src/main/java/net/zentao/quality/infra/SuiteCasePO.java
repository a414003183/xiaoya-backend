package net.zentao.quality.infra;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

/** suite_case 表 PO（quality 卡 §3.3 关联表）。 */
@Table("suite_case")
public class SuiteCasePO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private Long suiteId;
  private Long caseId;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getSuiteId() {
    return suiteId;
  }

  public void setSuiteId(Long suiteId) {
    this.suiteId = suiteId;
  }

  public Long getCaseId() {
    return caseId;
  }

  public void setCaseId(Long caseId) {
    this.caseId = caseId;
  }
}
