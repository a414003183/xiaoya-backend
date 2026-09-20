package net.zentao.project.infra;

import com.mybatisflex.core.query.QueryColumn;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.zentao.project.domain.ProjectProductRepository;
import org.springframework.stereotype.Component;

/** 项目↔产品关联仓储实现（project_product diff 全量替换）。 */
@Component
public class ProjectProductRepositoryImpl implements ProjectProductRepository {

  private final ProjectProductMapper mapper;

  public ProjectProductRepositoryImpl(ProjectProductMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<Long> productIds(long projectId) {
    return mapper.selectListByCondition(new QueryColumn("project_id").eq(projectId)).stream()
        .map(ProjectProductPO::getProductId)
        .toList();
  }

  @Override
  public List<Long> productIdsOf(List<Long> projectIds) {
    if (projectIds.isEmpty()) {
      return List.of();
    }
    return mapper.selectListByCondition(new QueryColumn("project_id").in(projectIds)).stream()
        .map(ProjectProductPO::getProductId)
        .distinct()
        .toList();
  }

  @Override
  public List<Long> projectIdsOfProduct(long productId) {
    return mapper.selectListByCondition(new QueryColumn("product_id").eq(productId)).stream()
        .map(ProjectProductPO::getProjectId)
        .distinct()
        .toList();
  }

  @Override
  public void replace(long projectId, List<Long> productIds) {
    Set<Long> target = new LinkedHashSet<>(productIds);
    List<ProjectProductPO> existing = mapper.selectListByCondition(new QueryColumn("project_id").eq(projectId));
    for (ProjectProductPO po : existing) {
      if (!target.contains(po.getProductId())) {
        mapper.deleteById(po.getId());
      }
    }
    Set<Long> kept = existing.stream().map(ProjectProductPO::getProductId)
        .collect(java.util.stream.Collectors.toSet());
    for (Long productId : target) {
      if (!kept.contains(productId)) {
        ProjectProductPO po = new ProjectProductPO();
        po.setProjectId(projectId);
        po.setProductId(productId);
        mapper.insert(po);
      }
    }
  }
}
