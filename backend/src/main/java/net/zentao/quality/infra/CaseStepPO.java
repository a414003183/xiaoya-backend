package net.zentao.quality.infra;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

/** case_step 表 PO（quality 卡 §3.2 steps 子表）。 */
@Table("case_step")
public class CaseStepPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private Long caseId;
  private Integer sort;
  private String description;
  private String expects;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getCaseId() {
    return caseId;
  }

  public void setCaseId(Long caseId) {
    this.caseId = caseId;
  }

  public Integer getSort() {
    return sort;
  }

  public void setSort(Integer sort) {
    this.sort = sort;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getExpects() {
    return expects;
  }

  public void setExpects(String expects) {
    this.expects = expects;
  }
}
