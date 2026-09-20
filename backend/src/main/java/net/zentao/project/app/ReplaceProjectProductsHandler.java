package net.zentao.project.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.product.api.ProductList;
import net.zentao.project.domain.Project;
import net.zentao.project.domain.ProjectProductRepository;
import net.zentao.project.domain.ProjectRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目↔产品关联（project 卡 §2/§5 products 族三行）：项目关联产品读/全量替换，项目集只读聚合其下项目的产品并集。
 * 读侧 DataScope 由产品域按可见集收窄；写侧只校验产品存在（关联本身不要求可见）。
 */
@Component
public class ReplaceProjectProductsHandler {

  private final ProjectProductRepository repository;
  private final ProjectRepository projectRepository;
  private final ProductApi productApi;
  private final ProjectQueryService projectQueryService;

  public ReplaceProjectProductsHandler(ProjectProductRepository repository, ProjectRepository projectRepository,
      ProductApi productApi, ProjectQueryService projectQueryService) {
    this.repository = repository;
    this.projectRepository = projectRepository;
    this.productApi = productApi;
    this.projectQueryService = projectQueryService;
  }

  public record ProjectProductRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> productIds) {}

  public ProductList listProject(SessionPrincipal actor, long projectId, Map<String, String[]> params) {
    projectQueryService.requireVisible(actor, "project", projectId);
    return productApi.pageByIds(repository.productIds(projectId), actor, params);
  }

  /** 项目集关联产品 = 其下项目（type=project）的 project_product 并集。 */
  public ProductList listProgram(SessionPrincipal actor, long programId, Map<String, String[]> params) {
    projectQueryService.requireVisible(actor, "program", programId);
    List<Long> projectIds = projectRepository.findAllActive().stream()
        .filter(project -> "project".equals(project.type()) && project.parentId() == programId)
        .map(Project::id)
        .toList();
    return productApi.pageByIds(repository.productIdsOf(projectIds), actor, params);
  }

  /** 全量替换（diff 落 project_product；空数组 → 42201，项目必须关联产品）。 */
  @Transactional
  public ProjectProductRequest replace(SessionPrincipal actor, long projectId, ProjectProductRequest command) {
    projectQueryService.requireVisible(actor, "project", projectId);
    List<Long> productIds = command == null ? null : command.productIds();
    if (productIds == null || productIds.isEmpty()) {
      throw ApiException.validation(Map.of("productIds", "required"));
    }
    List<Long> target = productIds.stream().distinct().toList();
    if (target.stream().anyMatch(productId -> productApi.findById(productId).isEmpty())) {
      throw ApiException.validation(Map.of("productIds", "notFound"));
    }
    repository.replace(projectId, target);
    return new ProjectProductRequest(repository.productIds(projectId));
  }
}
