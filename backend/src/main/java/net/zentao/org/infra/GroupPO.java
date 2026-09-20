package net.zentao.org.infra;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

/** auth_group 表 PO（org 卡 §3.3；真实删除）。 */
@Table("auth_group")
public class GroupPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private String name;
  private String description;
  private String acl;
  private String createdBy;

  @Column(version = true)
  private Integer lockVersion;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getAcl() {
    return acl;
  }

  public void setAcl(String acl) {
    this.acl = acl;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public Integer getLockVersion() {
    return lockVersion;
  }

  public void setLockVersion(Integer lockVersion) {
    this.lockVersion = lockVersion;
  }
}
