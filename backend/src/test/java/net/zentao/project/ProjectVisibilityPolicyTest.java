package net.zentao.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.project.domain.Project;
import net.zentao.project.domain.ProjectVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T-1 可见集矩阵（project 卡 §7，纯函数，无 Spring）。 */
class ProjectVisibilityPolicyTest {

  private static Project project(long id, String type, long parentId, String acl, String pm, String createdBy) {
    return new Project(id, type, parentId, "," + id + ",", 1, "P" + id, null, "scrum", "wait", 1, null, null, null,
        null, null, 0, BigDecimal.ZERO, "CNY", null, pm, null, null, null, 0, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, false, acl, List.of(), 0, Map.of(), createdBy, Instant.now(), null, null, null, null, 0);
  }

  private static Project withWhitelist(Project project, List<String> whitelist) {
    return new Project(project.id(), project.type(), project.parentId(), project.path(), project.grade(),
        project.name(), project.code(), project.model(), project.status(), project.priority(), project.beginDate(),
        project.endDate(), project.firstEndDate(), project.realBeganDate(), project.realEndDate(), project.days(),
        project.budget(), project.budgetUnit(), project.description(), project.pm(), project.po(), project.qd(),
        project.rd(), project.progress(), project.estimateHours(), project.consumedHours(), project.leftHours(),
        project.isMilestone(), project.acl(), whitelist, project.sort(), project.customFields(), project.createdBy(),
        project.createdAt(), project.updatedBy(), project.updatedAt(), project.closedBy(), project.closedAt(),
        project.lockVersion());
  }

  private static ProjectVisibility.Viewer viewer(String account) {
    return new ProjectVisibility.Viewer(account, false, ProjectVisibility.AclSets.EMPTY);
  }

  @Test
  @DisplayName("open 项目全员可见；private 仅 pm/po/qd/rd/createdBy/白名单可见")
  void openAndPrivate() {
    List<Project> all = List.of(
        project(1, "project", 0, "open", null, "boss"),
        withWhitelist(project(2, "project", 0, "private", "pm1", "boss"), List.of("wl1")),
        project(3, "project", 0, "private", "pm1", "boss"));
    assertEquals(Set.of(1L), ProjectVisibility.visibleIds(all, viewer("outsider")),
        "外人只见 open");
    assertEquals(Set.of(1L, 2L, 3L), ProjectVisibility.visibleIds(all, viewer("pm1")), "pm 可见自己的 private 项目");
    assertEquals(Set.of(1L, 2L), ProjectVisibility.visibleIds(all, viewer("wl1")), "白名单成员可见");
    assertTrue(ProjectVisibility
        .visibleIds(all, new ProjectVisibility.Viewer("any", true, ProjectVisibility.AclSets.EMPTY))
        .containsAll(Set.of(1L, 2L, 3L)), "超管全见");
  }

  @Test
  @DisplayName("acl=program 继承项目集可见性；执行随所属项目可见")
  void programAndExecutionInheritance() {
    Project program = project(10, "program", 0, "open", null, "boss");
    Project project = project(11, "project", 10, "program", null, "boss");
    Project execution = project(12, "sprint", 11, null, null, "boss");
    Project privateProgram = project(20, "program", 0, "private", null, "boss");
    Project inheritedPrivate = project(21, "project", 20, "program", null, "boss");
    Project hiddenExecution = project(22, "sprint", 21, null, null, "boss");
    List<Project> all = List.of(program, project, execution, privateProgram, inheritedPrivate, hiddenExecution);

    Set<Long> outsider = ProjectVisibility.visibleIds(all, viewer("outsider"));
    assertEquals(Set.of(10L, 11L, 12L), outsider, "项目集 open → 其下 project(program)/execution 可见；private 项目集下不可见");

    Set<Long> boss = ProjectVisibility.visibleIds(all, viewer("boss"));
    assertTrue(boss.containsAll(Set.of(10L, 11L, 12L, 21L, 22L)), "项目集 createdBy 可见整棵子树");
  }

  @Test
  @DisplayName("组 ACL 追加集使项目/执行可见（platform §7.2 并集）")
  void groupAclUnion() {
    Project project = project(31, "project", 0, "private", null, "boss");
    Project execution = project(32, "sprint", 31, null, null, "boss");
    List<Project> all = List.of(project, execution);
    ProjectVisibility.Viewer viewer = new ProjectVisibility.Viewer("member", false,
        new ProjectVisibility.AclSets(Set.of(), Set.of(31L), Set.of()));
    assertEquals(Set.of(31L, 32L), ProjectVisibility.visibleIds(all, viewer), "组 ACL 项目可见 → 其执行同见");
    assertFalse(ProjectVisibility.visibleIds(all, viewer("stranger")).contains(31L));
  }
}
