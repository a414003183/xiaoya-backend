package net.zentao.product.app;

import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.ProductApi;
import net.zentao.product.domain.BranchRepository;
import net.zentao.product.domain.BuildRepository;
import net.zentao.product.domain.PlanRepository;
import net.zentao.product.domain.ProductRepository;
import net.zentao.product.domain.ReleaseRepository;
import net.zentao.requirement.api.StoryApi;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 删除产品（product 卡 §5 DELETE，A-07 落地）：软删。
 * 守卫：存在未删 story/branch/plan/release/build 下挂对象任一 → 42203（story 经 StoryApi，其余同域直查）。
 */
@Component
public class DeleteProductHandler {

  private final ProductRepository repository;
  private final ProductApi productApi;
  private final StoryApi storyApi;
  private final BranchRepository branchRepository;
  private final PlanRepository planRepository;
  private final ReleaseRepository releaseRepository;
  private final BuildRepository buildRepository;

  public DeleteProductHandler(ProductRepository repository, ProductApi productApi, StoryApi storyApi,
      BranchRepository branchRepository, PlanRepository planRepository, ReleaseRepository releaseRepository,
      BuildRepository buildRepository) {
    this.repository = repository;
    this.productApi = productApi;
    this.storyApi = storyApi;
    this.branchRepository = branchRepository;
    this.planRepository = planRepository;
    this.releaseRepository = releaseRepository;
    this.buildRepository = buildRepository;
  }

  @Transactional
  public void handle(SessionPrincipal actor, long productId) {
    ProductGuard.requireVisible(repository, productApi, actor, productId);
    if (storyApi.hasActiveStoriesByProduct(productId)) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "product.guard.hasStories");
    }
    if (!branchRepository.findByProductId(productId).isEmpty()) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "product.guard.hasBranches");
    }
    if (planRepository.existsActiveByProduct(productId)) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "product.guard.hasPlans");
    }
    if (releaseRepository.existsActiveByProduct(productId)) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "product.guard.hasReleases");
    }
    if (buildRepository.existsActiveByProduct(productId)) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "product.guard.hasBuilds");
    }
    repository.softDelete(productId);
  }
}
