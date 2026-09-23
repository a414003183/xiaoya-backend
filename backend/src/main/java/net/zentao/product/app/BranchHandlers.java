package net.zentao.product.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.BranchView;
import net.zentao.product.api.ProductApi;
import net.zentao.product.domain.Branch;
import net.zentao.product.domain.BranchRepository;
import net.zentao.product.domain.Product;
import net.zentao.product.domain.ProductRepository;
import net.zentao.requirement.api.StoryApi;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分支命令（product 卡 §3.2/§4.2/§5）：创建（type=normal 产品拒挂分支、同产品重名）、
 * 编辑、close/activate（走 workflow/branch.yml）、set-default（排他）。
 */
@Component
public class BranchHandlers {

  private final BranchRepository repository;
  private final ProductRepository productRepository;
  private final ProductApi productApi;
  private final WorkflowEngine engine;
  private final ActivityRecorder activityRecorder;
  private final StoryApi storyApi;

  public BranchHandlers(BranchRepository repository, ProductRepository productRepository, ProductApi productApi,
      WorkflowEngine engine, ActivityRecorder activityRecorder, StoryApi storyApi) {
    this.repository = repository;
    this.productRepository = productRepository;
    this.productApi = productApi;
    this.engine = engine;
    this.activityRecorder = activityRecorder;
    this.storyApi = storyApi;
  }

  public record BranchCreateRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      String description, Integer sort) {}

  public record BranchUpdateRequest(String name, @jakarta.validation.constraints.Size(max = 255) String description,
      Integer sort,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  @Transactional
  public BranchView create(SessionPrincipal actor, long productId, BranchCreateRequest command) {
    Product product = ProductGuard.requireVisible(productRepository, productApi, actor, productId);
    if ("normal".equals(product.type())) {
      throw ApiException.validation(Map.of("productId", "branchNotAllowed"));
    }
    validateName(command.name());
    if (repository.existsByNameInProduct(productId, command.name().trim(), 0)) {
      throw ApiException.validation(Map.of("name", "duplicate"));
    }
    Instant now = Instant.now();
    boolean firstBranch = repository.findByProductId(productId).isEmpty();
    Branch branch = repository.insert(new Branch(0, productId, command.name().trim(), firstBranch, "active",
        command.description(), command.sort() == null ? 0 : command.sort(), actor.account(), now, null, null, null, 0));
    activityRecorder.record(actor.account(), "branch", branch.id(), "created", null, null);
    return BranchView.of(branch);
  }

  @Transactional
  public BranchView update(SessionPrincipal actor, long branchId, BranchUpdateRequest command) {
    Branch branch = require(actor, branchId);
    if (command.lockVersion() == null || command.lockVersion() != branch.lockVersion()) {
      throw ApiException.lockConflict();
    }
    if (command.name() != null) {
      validateName(command.name());
      if (repository.existsByNameInProduct(branch.productId(), command.name().trim(), branchId)) {
        throw ApiException.validation(Map.of("name", "duplicate"));
      }
    }
    branch.update(command.name() == null ? null : command.name().trim(), command.description(), command.sort());
    branch.markUpdatedBy(actor.account());
    return BranchView.of(save(branch));
  }

  @Transactional
  public BranchView close(SessionPrincipal actor, long branchId, String comment) {
    return fire(actor, branchId, "close", comment);
  }

  @Transactional
  public BranchView activate(SessionPrincipal actor, long branchId, String comment) {
    return fire(actor, branchId, "activate", comment);
  }

  /** set-default（§4.2）：排他置位 + workflow 出 isDefault 字段联动。 */
  @Transactional
  public BranchView setDefault(SessionPrincipal actor, long branchId) {
    Branch branch = require(actor, branchId);
    engine.fire(new WorkflowTargets.BranchTarget(branch, actor.account()), "set-default", null);
    branch.markUpdatedBy(actor.account());
    Branch saved = save(branch);
    repository.clearDefaultExcept(branch.productId(), branchId);
    return BranchView.of(saved);
  }

  /** DELETE（§5，A-07）：软删；该分支存在未删需求 → 42203（经 StoryApi）。 */
  @Transactional
  public void delete(SessionPrincipal actor, long branchId) {
    require(actor, branchId);
    if (storyApi.hasActiveStoriesByBranch(branchId)) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "branch.guard.hasStories");
    }
    repository.softDelete(branchId);
  }

  private BranchView fire(SessionPrincipal actor, long branchId, String action, String comment) {
    Branch branch = require(actor, branchId);
    engine.fire(new WorkflowTargets.BranchTarget(branch, actor.account()), action, comment);
    branch.markUpdatedBy(actor.account());
    return BranchView.of(save(branch));
  }

  private Branch require(SessionPrincipal actor, long branchId) {
    Branch branch = repository.findActiveById(branchId).orElseThrow(() -> ApiException.notFound("entity.branch"));
    ProductGuard.requireVisible(productRepository, productApi, actor, branch.productId());
    return branch;
  }

  private Branch save(Branch branch) {
    return repository.update(branch).orElseThrow(() -> ApiException.lockConflict());
  }

  /** name 语义是「trim 后必填/不超 255」且 create/update 共用（update null=不改）——跨字段口径，不注解化。 */
  private static void validateName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw ApiException.validation(Map.of("name", "required"));
    }
    if (name.trim().length() > 255) {
      throw ApiException.validation(Map.of("name", "maxLength"));
    }
  }
}
