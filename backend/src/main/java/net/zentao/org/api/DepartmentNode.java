package net.zentao.org.api;

import java.util.List;

/** 部门节点（org 卡 §3.2 / contract DepartmentNode；path/grade 服务端维护只读；平铺列表端点复用本类型，children 恒空）。 */
public record DepartmentNode(
    long id,
    String name,
    Long parentId,
    String parentName,
    String path,
    int grade,
    int sort,
    String manager,
    List<DepartmentNode> children) {

  public DepartmentNode {
    children = children == null ? List.of() : List.copyOf(children);
  }

  /** 平铺列表行（无子节点）：children 缺省空数组。 */
  public static DepartmentNode flat(long id, String name, Long parentId, String parentName, String path, int grade,
      int sort, String manager) {
    return new DepartmentNode(id, name, parentId, parentName, path, grade, sort, manager, List.of());
  }
}
