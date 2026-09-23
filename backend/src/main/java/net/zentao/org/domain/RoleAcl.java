package net.zentao.org.domain;

import java.util.List;

/**
 * 角色数据权限追加可见集（platform 卡 §7.2；原 GroupAcl，T23 随实体改名）：
 * 各键为对象 id 数组，null 子键归一空表（持久化为 JSON 文本列，DataScope 按名解析
 * products/programs/projects/executions）。
 */
public record RoleAcl(List<Long> views, List<Long> products, List<Long> programs, List<Long> projects,
    List<Long> executions) {

  public static final RoleAcl EMPTY = new RoleAcl(null, null, null, null, null);

  public RoleAcl {
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
