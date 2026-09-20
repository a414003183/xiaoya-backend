package net.zentao.project.app;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.api.ProjectApi;
import net.zentao.project.api.ProjectView;
import net.zentao.project.domain.ProjectStoryRepository;
import net.zentao.requirement.api.StoryApi;
import net.zentao.requirement.api.StoryList;
import org.springframework.stereotype.Component;

/**
 * 项目/执行维度关联需求（project 卡 §5 stories 行的 GET）：project_story 反查 id 集 → requirement 域分页。
 * DataScope 双闸：本域先判对象可见（40401/40302），需求域再按产品可见集收窄。
 *
 * <p>执行的需求集 = 执行自身关联 ∪ 所属项目的关联：契约只开放项目级关联写端点
 * （POST /projects/{projectId}/stories，执行/项目两级共用同一张 project_story），执行随所属项目承接，
 * 故执行列表/看板与项目关联保持同源（执行侧行若存在一并计入，去重）。
 */
@Component
public class ProjectStoryQueryService {

  private final ProjectStoryRepository repository;
  private final StoryApi storyApi;
  private final ProjectApi projectApi;
  private final ProjectQueryService projectQueryService;

  public ProjectStoryQueryService(ProjectStoryRepository repository, StoryApi storyApi, ProjectApi projectApi,
      ProjectQueryService projectQueryService) {
    this.repository = repository;
    this.storyApi = storyApi;
    this.projectApi = projectApi;
    this.projectQueryService = projectQueryService;
  }

  public StoryList page(SessionPrincipal actor, String objectType, long objectId, Map<String, String[]> params) {
    projectQueryService.requireVisible(actor, objectType, objectId);
    return storyApi.pageByIds(storyIds(objectType, objectId), actor, params);
  }

  /** 关联需求 id 集（看板读与拖拽守卫共用，保证两处口径一致）。 */
  public List<Long> storyIds(String objectType, long objectId) {
    Set<Long> ids = new LinkedHashSet<>(repository.storyIds(objectId));
    if ("execution".equals(objectType)) {
      projectApi.findById(objectId).map(ProjectView::parentId).ifPresent(projectId ->
          ids.addAll(repository.storyIds(projectId)));
    }
    return List.copyOf(ids);
  }
}
