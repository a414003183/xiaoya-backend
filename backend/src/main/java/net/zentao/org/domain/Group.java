package net.zentao.org.domain;

/** 权限组聚合根（org 卡 §3.3；真实删除级联清 user_group/group_priv；acl 为数据权限追加可见集）。 */
public class Group {

  private final long id;
  private String name;
  private String description;
  private GroupAcl acl;
  private String createdBy;
  private int lockVersion;

  public Group(long id, String name, String description, GroupAcl acl, String createdBy, int lockVersion) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.acl = acl == null ? GroupAcl.EMPTY : acl;
    this.createdBy = createdBy;
    this.lockVersion = lockVersion;
  }

  public void rename(String newName) {
    this.name = newName;
  }

  public void changeDescription(String newDescription) {
    this.description = newDescription;
  }

  public void changeAcl(GroupAcl newAcl) {
    this.acl = newAcl == null ? GroupAcl.EMPTY : newAcl;
  }

  public long id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public GroupAcl acl() {
    return acl;
  }

  public String createdBy() {
    return createdBy;
  }

  public int lockVersion() {
    return lockVersion;
  }
}
