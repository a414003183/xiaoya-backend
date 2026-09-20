package net.zentao.org.domain;

import java.util.List;

/**
 * 组数据权限追加可见集（platform 卡 §7.2；A-08 补口 2026-09-19）：五键各为对象 id 数组，
 * null 子键归一空表（持久化为 JSON 文本列，DataScope 按名解析 products/programs/projects/executions）。
 */
public record GroupAcl(List<Long> views, List<Long> products, List<Long> programs, List<Long> projects,
    List<Long> executions) {

  public static final GroupAcl EMPTY = new GroupAcl(null, null, null, null, null);

  public GroupAcl {
    views = normalize(views);
    products = normalize(products);
    programs = normalize(programs);
    projects = normalize(projects);
    executions = normalize(executions);
  }

  private static List<Long> normalize(List<Long> ids) {
    return ids == null ? List.of() : List.copyOf(ids);
  }
}
