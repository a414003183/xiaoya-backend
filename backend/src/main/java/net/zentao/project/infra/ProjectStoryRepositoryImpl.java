package net.zentao.project.infra;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.zentao.project.domain.ProjectStoryRepository;
import org.springframework.stereotype.Component;

/** 项目/执行↔需求关联仓储实现（幂等：先查已关联 id，再补插缺失行）。 */
@Component
public class ProjectStoryRepositoryImpl implements ProjectStoryRepository {

  private final ProjectStoryMapper mapper;

  public ProjectStoryRepositoryImpl(ProjectStoryMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<Long> storyIds(long projectId) {
    return mapper.selectListByCondition(new QueryColumn("project_id").eq(projectId)).stream()
        .map(ProjectStoryPO::getStoryId)
        .toList();
  }

  @Override
  public void link(long projectId, List<Long> storyIds, long productId) {
    Set<Long> target = new LinkedHashSet<>(storyIds);
    if (target.isEmpty()) {
      return;
    }
    Set<Long> linked = new LinkedHashSet<>(mapper.selectListByCondition(
        new QueryColumn("project_id").eq(projectId).and(new QueryColumn("story_id").in(List.copyOf(target))))
        .stream().map(ProjectStoryPO::getStoryId).toList());
    for (Long storyId : target) {
      if (linked.contains(storyId)) {
        continue;
      }
      ProjectStoryPO po = new ProjectStoryPO();
      po.setProjectId(projectId);
      po.setStoryId(storyId);
      po.setProductId(productId);
      po.setSort(0);
      mapper.insert(po);
    }
  }

  @Override
  public void unlink(long projectId, long storyId) {
    Db.deleteByCondition("project_story",
        new QueryColumn("project_id").eq(projectId).and(new QueryColumn("story_id").eq(storyId)));
  }

  @Override
  public long countByProject(long projectId) {
    return mapper.selectCountByQuery(QueryWrapper.create().where(new QueryColumn("project_id").eq(projectId)));
  }
}
