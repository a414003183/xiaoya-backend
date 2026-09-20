package net.zentao.project.domain;

/**
 * 三级层级规则（project 卡 §2/§8，纯函数）：program→(program|0)、project→(program|0)、execution→project；
 * path 为逗号包裹物化路径 `,1,3,`，grade 从 1 起。
 */
public final class ProjectHierarchy {

  private ProjectHierarchy() {}

  /** 跨型挂载校验（§8：execution 挂 program 下 → 42201）。parent 为 null 表示顶级（parentId=0）。 */
  public static boolean allowsParent(String childType, Project parent) {
    if (parent == null) {
      return !isExecution(childType);
    }
    return switch (childType) {
      case "program", "project" -> parent.isProgram();
      default -> "project".equals(parent.type());
    };
  }

  /** 物化路径：顶级 `,id,`，子级 父 path 前缀 + `id,`。 */
  public static String pathOf(Project parent, long id) {
    return (parent == null ? "," : parent.path()) + id + ",";
  }

  public static int gradeOf(Project parent) {
    return parent == null ? 1 : parent.grade() + 1;
  }

  /** 父子/祖孙判定（path 前缀），用于防环与子树查询。 */
  public static boolean isDescendantOf(Project candidate, long ancestorId) {
    return candidate.path().contains("," + ancestorId + ",");
  }

  public static boolean isExecution(String type) {
    return !"program".equals(type) && !"project".equals(type);
  }
}
