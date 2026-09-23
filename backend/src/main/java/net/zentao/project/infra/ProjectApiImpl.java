package net.zentao.project.infra;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.rbac.DataScope;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.api.ExecutionApi;
import net.zentao.project.api.ProjectApi;
import net.zentao.project.api.ProjectView;
import net.zentao.project.domain.Project;
import net.zentao.project.domain.ProjectRepository;
import net.zentao.project.domain.ProjectVisibility;
import net.zentao.project.domain.StakeholderRepository;
import net.zentao.project.domain.TeamMemberRepository;
import org.springframework.stereotype.Component;

/**
 * project 域跨域读接口实现（project 卡 §7 + platform 卡 §7.2）：
 * 可见集 = 三型自身 ACL 规则（含 program/execution 继承）∪ 白名单 ∪ 团队成员/干系人 ∪ 所属各组 acl 追加；
 * 超管不受限。
 *
 * <p>一个实现类同时暴露 {@link ProjectApi} 与 {@link ExecutionApi}：两者共用同一套可见性计算，
 * 拆两处只会让「执行随项目可见」这条继承关系出现第二份。
 *
 * ponytail: 全量小集合内存判定（每次判定 2 条成员/干系人查询）；升级路径 = 可见集下推为 SQL IN 或维护闭包表。
 */
@Component
public class ProjectApiImpl implements ProjectApi, ExecutionApi {

  private final ProjectRepository repository;
  private final DataScope dataScope;
  private final TeamMemberRepository teamMemberRepository;
  private final StakeholderRepository stakeholderRepository;

  public ProjectApiImpl(ProjectRepository repository, DataScope dataScope, TeamMemberRepository teamMemberRepository,
      StakeholderRepository stakeholderRepository) {
    this.repository = repository;
    this.dataScope = dataScope;
    this.teamMemberRepository = teamMemberRepository;
    this.stakeholderRepository = stakeholderRepository;
  }

  @Override
  public VisibleScope visibleScope(SessionPrincipal principal, String type) {
    if (dataScope.isSuperAdmin(principal)) {
      return new VisibleScope(true, Set.of());
    }
    ProjectVisibility.Viewer viewer = viewer(principal);
    List<Project> all = repository.findAllActive();
    Set<Long> ids = new LinkedHashSet<>();
    for (Project project : all) {
      // "*" 为通配（executionScope 需要三型全集后再按 isExecution 收窄）
      if (("*".equals(type) || type.equals(project.type()))
          && ProjectVisibility.isVisible(project, all, viewer)) {
        ids.add(project.id());
      }
    }
    return new VisibleScope(false, ids);
  }

  @Override
  public Optional<ProjectView> findById(long id) {
    return repository.findActiveById(id).map(ProjectView::of);
  }

  @Override
  public boolean canAccess(SessionPrincipal principal, long id) {
    return repository.findActiveById(id)
        .map(project -> ProjectVisibility.isVisible(project, repository.findAllActive(), viewer(principal)))
        .orElse(false);
  }

  @Override
  public ProjectView requireVisible(SessionPrincipal principal, long id, String type) {
    Project project = repository.findActiveById(id).orElseThrow(() -> ApiException.notFound(label(type)));
    if (!type.equals(project.type()) && !("*".equals(type))) {
      throw ApiException.notFound(label(type));
    }
    if (!ProjectVisibility.isVisible(project, repository.findAllActive(), viewer(principal))) {
      throw ApiException.keyed(ErrorCode.DATA_FORBIDDEN, "project.guard.forbiddenTyped", label(type));
    }
    return ProjectView.of(project);
  }

  @Override
  public ExecutionScope executionScope(SessionPrincipal principal) {
    VisibleScope scope = visibleScope(principal, "*");
    Set<Long> executionIds = new LinkedHashSet<>();
    for (Project project : repository.findAllActive()) {
      if (project.isExecution() && scope.ids().contains(project.id())) {
        executionIds.add(project.id());
      }
    }
    return new ExecutionScope(scope.visibleToAll(), executionIds);
  }

  @Override
  public Optional<ProjectView> findExecution(long executionId) {
    return repository.findActiveById(executionId)
        .filter(Project::isExecution)
        .map(ProjectView::of);
  }

  @Override
  public Optional<String> status(long executionId) {
    return repository.findActiveById(executionId).map(Project::status);
  }

  @Override
  public ProjectView requireExecution(SessionPrincipal principal, long executionId) {
    Project execution = require(principal, executionId);
    if (!execution.isExecution()) {
      throw ApiException.notFound("entity.execution");
    }
    return ProjectView.of(execution);
  }

  @Override
  public ProjectView requireWritable(SessionPrincipal principal, long executionId) {
    Project execution = require(principal, executionId);
    if (!execution.isExecution()) {
      throw ApiException.notFound("entity.execution");
    }
    if ("closed".equals(execution.status())) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "execution.guard.closedReadOnly");
    }
    return ProjectView.of(execution);
  }

  private Project require(SessionPrincipal principal, long id) {
    Project project = repository.findActiveById(id).orElseThrow(() -> ApiException.notFound("entity.project"));
    if (!ProjectVisibility.isVisible(project, repository.findAllActive(), viewer(principal))) {
      throw ApiException.keyed(ErrorCode.DATA_FORBIDDEN, "project.guard.forbidden");
    }
    return project;
  }

  private static String label(String type) {
    return switch (type) {
      case "program" -> "entity.program";
      case "execution" -> "entity.execution";
      default -> "entity.project";
    };
  }

  private ProjectVisibility.Viewer viewer(SessionPrincipal principal) {
    DataScope.Acl acl = dataScope.aclUnion(principal);
    ProjectVisibility.AclSets groupAcl = new ProjectVisibility.AclSets(Set.copyOf(acl.programs()),
        Set.copyOf(acl.projects()), Set.copyOf(acl.executions()));
    return new ProjectVisibility.Viewer(principal.account(), false, groupAcl.merge(visibilityAdditions(principal)));
  }

  /**
   * §7 追加集：团队成员（project/execution）与干系人（program/project）与白名单同权——
   * 并入追加集即复用同一套判定（见 {@link ProjectVisibility.AclSets}）。
   */
  private ProjectVisibility.AclSets visibilityAdditions(SessionPrincipal principal) {
    String account = principal.account();
    if (account == null) {
      return ProjectVisibility.AclSets.EMPTY;
    }
    Map<String, Set<Long>> team = teamMemberRepository.objectsOf(account);
    Map<String, Set<Long>> stakeholders = stakeholderRepository.objectsOf(account);
    Set<Long> projects = new LinkedHashSet<>(team.getOrDefault("project", Set.of()));
    projects.addAll(stakeholders.getOrDefault("project", Set.of()));
    return new ProjectVisibility.AclSets(stakeholders.getOrDefault("program", Set.of()), Set.copyOf(projects),
        team.getOrDefault("execution", Set.of()));
  }
}
