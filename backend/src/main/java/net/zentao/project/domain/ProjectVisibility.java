package net.zentao.project.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 三型可见性判定（project 卡 §7，纯函数）：
 * program open=全员 / private=pm·createdBy·白名单·干系人；project open=全员 /
 * private=pm·po·qd·rd·createdBy·白名单·团队成员·干系人 / program=继承上级项目集；
 * execution 继承所属项目 + 自身白名单·团队成员；组 ACL/成员/干系人追加集见 {@link Viewer#groupAcl()}。
 *
 * ponytail: 全量小集合内存判定（可接受）；升级路径 = 递归下推为 SQL 的 IN 集合或维护闭包表。
 */
public final class ProjectVisibility {

  private ProjectVisibility() {}

  /** 对象白名单/组 ACL/团队成员/干系人的按型追加 id 集（组 ACL 来自 platform DataScope.aclUnion）。 */
  public record AclSets(Set<Long> programs, Set<Long> projects, Set<Long> executions) {

    public static final AclSets EMPTY = new AclSets(Set.of(), Set.of(), Set.of());

    /** 并集合并（任一来源命中即可见）。 */
    public AclSets merge(AclSets other) {
      return new AclSets(union(programs, other.programs), union(projects, other.projects),
          union(executions, other.executions));
    }

    private static Set<Long> union(Set<Long> left, Set<Long> right) {
      Set<Long> merged = new LinkedHashSet<>(left);
      merged.addAll(right);
      return Set.copyOf(merged);
    }
  }

  /**
   * 可见者上下文：账号 + 超管 + 追加集（对象白名单在聚合上；团队成员/干系人集由 ProjectApiImpl 装配
   * Viewer 时并入 {@link #groupAcl}——§7 两类对象的可见者与白名单同权）。
   */
  public record Viewer(String account, boolean superAdmin, AclSets groupAcl) {}

  /** 可见 id 集（含 program 继承与 execution 继承的递归展开）。 */
  public static Set<Long> visibleIds(List<Project> all, Viewer viewer) {
    Map<Long, Project> byId = new java.util.HashMap<>();
    for (Project project : all) {
      byId.put(project.id(), project);
    }
    Map<Long, Boolean> memo = new java.util.HashMap<>();
    Set<Long> visible = new LinkedHashSet<>();
    for (Project project : all) {
      if (isVisible(project, byId, viewer, memo)) {
        visible.add(project.id());
      }
    }
    return visible;
  }

  public static boolean isVisible(Project target, List<Project> all, Viewer viewer) {
    Map<Long, Project> byId = new java.util.HashMap<>();
    for (Project project : all) {
      byId.put(project.id(), project);
    }
    return isVisible(target, byId, viewer, new java.util.HashMap<>());
  }

  private static boolean isVisible(Project target, Map<Long, Project> byId, Viewer viewer, Map<Long, Boolean> memo) {
    Boolean cached = memo.get(target.id());
    if (cached != null) {
      return cached;
    }
    // 先占位 false：path 成环的脏数据不会无限递归
    memo.put(target.id(), false);
    boolean visible = decide(target, byId, viewer, memo);
    memo.put(target.id(), visible);
    return visible;
  }

  private static boolean decide(Project target, Map<Long, Project> byId, Viewer viewer, Map<Long, Boolean> memo) {
    String account = viewer.account();
    if (viewer.superAdmin()) {
      return true;
    }
    if (account == null) {
      return false;
    }
    boolean roleMatch = account.equals(target.pm())
        || account.equals(target.po())
        || account.equals(target.qd())
        || account.equals(target.rd())
        || account.equals(target.createdBy());
    boolean whitelisted = target.whitelist().contains(account);
    if (target.isProgram()) {
      return "open".equals(target.acl())
          || roleMatch
          || whitelisted
          || viewer.groupAcl().programs().contains(target.id());
    }
    Project parent = byId.get(target.parentId());
    if (target.isExecution()) {
      return whitelisted
          || viewer.groupAcl().executions().contains(target.id())
          || (parent != null && isVisible(parent, byId, viewer, memo));
    }
    return "open".equals(target.acl())
        || roleMatch
        || whitelisted
        || viewer.groupAcl().projects().contains(target.id())
        || ("program".equals(target.acl()) && parent != null && isVisible(parent, byId, viewer, memo));
  }
}
