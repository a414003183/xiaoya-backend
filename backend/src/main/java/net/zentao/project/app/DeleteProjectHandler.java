package net.zentao.project.app;

import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.project.domain.ProjectRepository;
import net.zentao.project.domain.ProjectStoryRepository;
import net.zentao.task.api.TaskApi;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 三型软删（project 卡 §5 DELETE 行，A-07）：program/project/execution 共用一表一守卫骨架，按型收口——
 * program：存在未删子 program/project（parent_id 指向）→ 42203；
 * project：存在未删执行或未删 project_story 关联行 → 42203；
 * execution：存在未删任务（TaskApi.hasActiveTasksByExecution）→ 42203。
 */
@Component
public class DeleteProjectHandler {

  private final ProjectRepository repository;
  private final ProjectStoryRepository projectStoryRepository;
  private final ProjectQueryService projectQueryService;
  private final TaskApi taskApi;

  public DeleteProjectHandler(ProjectRepository repository, ProjectStoryRepository projectStoryRepository,
      ProjectQueryService projectQueryService, TaskApi taskApi) {
    this.repository = repository;
    this.projectStoryRepository = projectStoryRepository;
    this.projectQueryService = projectQueryService;
    this.taskApi = taskApi;
  }

  /** type ∈ program|project|execution；不存在或型不符 → 40401，不可见 → 40302（与动作端点同闸门）。 */
  @Transactional
  public void handle(SessionPrincipal actor, long id, String type) {
    projectQueryService.requireVisible(actor, type, id);
    switch (type) {
      case "program" -> {
        if (repository.countActiveChildren(id) > 0) {
          throw ApiException.guardNotSatisfied("存在未删的子项目集或子项目，不能删除。");
        }
      }
      case "project" -> {
        if (repository.countActiveChildren(id) > 0) {
          throw ApiException.guardNotSatisfied("存在未删的执行，不能删除。");
        }
        if (projectStoryRepository.countByProject(id) > 0) {
          throw ApiException.guardNotSatisfied("存在未删的关联需求，不能删除。");
        }
      }
      default -> {
        if (taskApi.hasActiveTasksByExecution(id)) {
          throw ApiException.guardNotSatisfied("执行下存在未删任务，不能删除。");
        }
      }
    }
    repository.softDelete(id);
  }
}
