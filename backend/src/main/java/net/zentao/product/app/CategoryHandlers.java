package net.zentao.product.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.error.ErrorCode;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.CategoryView;
import net.zentao.product.api.ProductApi;
import net.zentao.product.domain.BranchRepository;
import net.zentao.product.domain.Category;
import net.zentao.product.domain.CategoryRepository;
import net.zentao.product.domain.CategoryTree;
import net.zentao.product.domain.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分类命令（product 卡 §3.3/§5）：创建/编辑（parentId 跨产品、跨 type、成环 → 42203）、
 * DELETE 级联软删子树。type/branchId 创建后不可改。
 */
@Component
public class CategoryHandlers {

  private static final Set<String> TYPES = Set.of("story", "bug", "case");

  private final CategoryRepository repository;
  private final BranchRepository branchRepository;
  private final ProductRepository productRepository;
  private final ProductApi productApi;
  private final AccountApi accountApi;
  private final ActivityRecorder activityRecorder;

  public CategoryHandlers(CategoryRepository repository, BranchRepository branchRepository,
      ProductRepository productRepository, ProductApi productApi, AccountApi accountApi,
      ActivityRecorder activityRecorder) {
    this.repository = repository;
    this.branchRepository = branchRepository;
    this.productRepository = productRepository;
    this.productApi = productApi;
    this.accountApi = accountApi;
    this.activityRecorder = activityRecorder;
  }

  public record CategoryCreateRequest(Long branchId, Long parentId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"bug", "case", "story"}) String type,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name, String owner, Integer sort) {}

  public record CategoryUpdateRequest(String name, String owner, Integer sort, Long parentId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  @Transactional
  public CategoryView create(SessionPrincipal actor, long productId, CategoryCreateRequest command) {
    ProductGuard.requireVisible(productRepository, productApi, actor, productId);
    Map<String, String> errors = new java.util.LinkedHashMap<>();
    if (command.type() == null || !TYPES.contains(command.type())) {
      errors.put("type", "invalid");
    }
    if (command.name() == null || command.name().trim().isEmpty()) {
      errors.put("name", "required");
    } else if (command.name().trim().length() > 60) {
      errors.put("name", "maxLength");
    }
    if (command.owner() != null && !accountApi.missingAccounts(List.of(command.owner())).isEmpty()) {
      errors.put("owner", "notFound");
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    long parentId = command.parentId() == null ? 0 : command.parentId();
    long branchId = command.branchId() == null ? 0 : command.branchId();
    requireSameTree(productId, command.type(), parentId, 0);
    if (branchId != 0 && branchRepository.findActiveById(branchId).filter(b -> b.productId() == productId).isEmpty()) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "category.guard.branchMismatch");
    }
    Instant now = Instant.now();
    Category category = repository.insert(new Category(0, productId, branchId, parentId, command.type(),
        command.name().trim(), command.owner(), command.sort() == null ? 0 : command.sort(), actor.account(), now,
        null, null, 0));
    activityRecorder.record(actor.account(), "category", category.id(), "created", null, null);
    return CategoryView.of(category);
  }

  @Transactional
  public CategoryView update(SessionPrincipal actor, long categoryId, CategoryUpdateRequest command) {
    Category category = require(actor, categoryId);
    if (command.lockVersion() == null || command.lockVersion() != category.lockVersion()) {
      throw ApiException.lockConflict();
    }
    Map<String, String> errors = new java.util.LinkedHashMap<>();
    if (command.name() != null) {
      if (command.name().trim().isEmpty()) {
        errors.put("name", "required");
      } else if (command.name().trim().length() > 60) {
        errors.put("name", "maxLength");
      }
    }
    if (command.owner() != null && !accountApi.missingAccounts(List.of(command.owner())).isEmpty()) {
      errors.put("owner", "notFound");
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    if (command.parentId() != null) {
      long parentId = command.parentId();
      if (parentId == categoryId) {
        throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "category.guard.selfParent");
      }
      requireSameTree(category.productId(), category.type(), parentId, categoryId);
    }
    category.update(command.name() == null ? null : command.name().trim(), command.owner(), command.sort(),
        command.parentId());
    category.markUpdatedBy(actor.account());
    return CategoryView.of(save(category));
  }

  /** DELETE：级联软删子树（§3.3）。 */
  @Transactional
  public void delete(SessionPrincipal actor, long categoryId) {
    Category category = require(actor, categoryId);
    List<Category> tree = repository.findByProductAndType(category.productId(), category.type());
    List<Long> ids = CategoryTree.selfAndDescendants(tree, categoryId).stream().map(Category::id).toList();
    repository.softDeleteAll(ids);
  }

  private Category require(SessionPrincipal actor, long categoryId) {
    Category category = repository.findActiveById(categoryId).orElseThrow(() -> ApiException.notFound("entity.category"));
    ProductGuard.requireVisible(productRepository, productApi, actor, category.productId());
    return category;
  }

  private Category save(Category category) {
    return repository.update(category).orElseThrow(() -> ApiException.lockConflict());
  }

  /** parentId 必须为空或「同产品同 type」节点（跨产品/跨 type → 42203）；编辑时禁成环。 */
  private void requireSameTree(long productId, String type, long parentId, long selfId) {
    if (parentId == 0) {
      return;
    }
    Category parent = repository.findActiveById(parentId)
        .orElseThrow(() -> ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "category.guard.parentMissing"));
    if (parent.productId() != productId || !type.equals(parent.type())) {
      throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "category.guard.parentMismatch");
    }
    if (selfId != 0) {
      List<Category> tree = repository.findByProductAndType(productId, type);
      boolean descendant = CategoryTree.selfAndDescendants(tree, selfId).stream()
          .anyMatch(node -> node.id() == parentId);
      if (descendant) {
        throw ApiException.keyed(ErrorCode.GUARD_NOT_SATISFIED, "category.guard.parentIsDescendant");
      }
    }
  }
}
