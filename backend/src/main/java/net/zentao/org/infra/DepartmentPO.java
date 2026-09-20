package net.zentao.org.infra;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

/** department 表 PO（org 卡 §3.2；真实删除，无 deleted_at）。 */
@Table("department")
public class DepartmentPO {

  @Id(keyType = KeyType.Auto)
  private Long id;

  private String name;
  private Long parentId;
  private String path;
  private Integer grade;
  private Integer sort;
  private String manager;

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

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public Integer getGrade() {
    return grade;
  }

  public void setGrade(Integer grade) {
    this.grade = grade;
  }

  public Integer getSort() {
    return sort;
  }

  public void setSort(Integer sort) {
    this.sort = sort;
  }

  public String getManager() {
    return manager;
  }

  public void setManager(String manager) {
    this.manager = manager;
  }

  public Integer getLockVersion() {
    return lockVersion;
  }

  public void setLockVersion(Integer lockVersion) {
    this.lockVersion = lockVersion;
  }
}
