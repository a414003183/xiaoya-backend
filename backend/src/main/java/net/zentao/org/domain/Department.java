package net.zentao.org.domain;

/**
 * 部门聚合根（org 卡 §3.2；纯 Java，A1）。
 * path 形如 ",1,3,8,"（逗号包裹 id 链）；grade 根=1。
 */
public class Department {

  private final long id;
  private String name;
  private Long parentId;
  private String path;
  private int grade;
  private int sort;
  private String manager;
  private int lockVersion;

  public Department(long id, String name, Long parentId, String path, int grade, int sort, String manager, int lockVersion) {
    this.id = id;
    this.name = name;
    this.parentId = parentId;
    this.path = path;
    this.grade = grade;
    this.sort = sort;
    this.manager = manager;
    this.lockVersion = lockVersion;
  }

  public static Department create(String name, Long parentId, String parentPath, int parentGrade, int sort, String manager) {
    return new Department(0, name, parentId, (parentPath == null ? "," : parentPath), parentGrade + 1, sort, manager, 0);
  }

  /** 是否本部门或其后代（path 包含本 id 段即后代）。 */
  public boolean isSelfOrDescendant(long candidateId) {
    return path.contains("," + candidateId + ",");
  }

  public void rename(String newName) {
    this.name = newName;
  }

  public void changeSort(int newSort) {
    this.sort = newSort;
  }

  public void changeManager(String newManager) {
    this.manager = newManager;
  }

  /** 移动到新父节点下（调用方已完成环检测），返回级联重算所需信息。 */
  public void moveTo(long newParentId, String newParentPath, int newParentGrade) {
    this.parentId = newParentId;
    this.path = newParentPath + id + ",";
    this.grade = newParentGrade + 1;
  }

  /** 后代节点级联重算（路径与层级由调用方按前缀规则给出）。 */
  public void relocate(String newPath, int newGrade) {
    this.path = newPath;
    this.grade = newGrade;
  }

  public long id() {
    return id;
  }

  public String name() {
    return name;
  }

  public Long parentId() {
    return parentId;
  }

  public String path() {
    return path;
  }

  public int grade() {
    return grade;
  }

  public int sort() {
    return sort;
  }

  public String manager() {
    return manager;
  }

  public int lockVersion() {
    return lockVersion;
  }
}
