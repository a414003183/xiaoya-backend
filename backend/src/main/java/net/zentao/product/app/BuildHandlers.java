package net.zentao.product.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.activity.ActivityRecorder;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.product.api.BuildView;
import net.zentao.product.api.ProductApi;
import net.zentao.product.domain.Build;
import net.zentao.product.domain.BuildRepository;
import net.zentao.product.domain.ProductRepository;
import net.zentao.product.domain.ReleaseRepository;
import net.zentao.quality.api.BugApi;
import net.zentao.requirement.api.StoryApi;
import net.zentao.requirement.api.StoryView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 构建命令（product 卡 §3.6/§4.5/§5）：创建（executionId 可缺省 = 产品级）、编辑、
 * 删除（软删；被任一发布 buildId 引用 → 42203）。
 */
@Component
public class BuildHandlers {

  private final BuildRepository repository;
  private final ReleaseRepository releaseRepository;
  private final ProductRepository productRepository;
  private final ProductApi productApi;
  private final AccountApi accountApi;
  private final StoryApi storyApi;
  private final BugApi bugApi;
  private final ActivityRecorder activityRecorder;

  public BuildHandlers(BuildRepository repository, ReleaseRepository releaseRepository,
      ProductRepository productRepository, ProductApi productApi, AccountApi accountApi, StoryApi storyApi,
      BugApi bugApi, ActivityRecorder activityRecorder) {
    this.repository = repository;
    this.releaseRepository = releaseRepository;
    this.productRepository = productRepository;
    this.productApi = productApi;
    this.accountApi = accountApi;
    this.storyApi = storyApi;
    this.bugApi = bugApi;
    this.activityRecorder = activityRecorder;
  }

  public record BuildCreateRequest(Long branchId, Long executionId, Long projectId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name, String scmPath,
      String filePath, LocalDate buildDate, String builder, List<Long> storyIds, List<Long> bugIds,
      String description) {}

  public record BuildUpdateRequest(String name, Long branchId, String scmPath, String filePath, LocalDate buildDate,
      String builder, Long projectId, String description,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  @Transactional
  public BuildView create(SessionPrincipal actor, long productId, BuildCreateRequest command) {
    ProductGuard.requireVisible(productRepository, productApi, actor, productId);
    validateName(command.name());
    requirePaths(command.scmPath(), command.filePath());
    List<Long> storyIds = distinct(command.storyIds());
    requireStoriesInProduct(productId, storyIds);
    List<Long> bugIds = distinct(command.bugIds());
    bugApi.requireInProduct(productId, bugIds);
    String builder = command.builder() == null || command.builder().isBlank() ? actor.account() : command.builder();
    if (!accountApi.missingAccounts(List.of(builder)).isEmpty()) {
      throw ApiException.validation(Map.of("builder", "notFound"));
    }
    Instant now = Instant.now();
    Build build = repository.insert(new Build(0, productId, command.branchId() == null ? 0 : command.branchId(),
        command.executionId() == null ? 0 : command.executionId(),
        command.projectId() == null ? 0 : command.projectId(), command.name().trim(), command.scmPath(),
        command.filePath(), command.buildDate() == null ? LocalDate.now() : command.buildDate(), builder, storyIds,
        bugIds, command.description(), actor.account(), now, null, null, 0));
    activityRecorder.record(actor.account(), "build", build.id(), "created", null, null);
    return BuildView.of(build);
  }

  @Transactional
  public BuildView update(SessionPrincipal actor, long buildId, BuildUpdateRequest command) {
    Build build = require(actor, buildId);
    if (command.lockVersion() == null || command.lockVersion() != build.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新后重试。");
    }
    if (command.name() != null) {
      validateName(command.name());
    }
    requirePaths(command.scmPath(), command.filePath());
    if (command.builder() != null && !accountApi.missingAccounts(List.of(command.builder())).isEmpty()) {
      throw ApiException.validation(Map.of("builder", "notFound"));
    }
    build.update(command.name() == null ? null : command.name().trim(), command.branchId(), command.scmPath(),
        command.filePath(), command.buildDate(), command.builder(), command.projectId(), command.description());
    build.markUpdatedBy(actor.account());
    return BuildView.of(save(build));
  }

  /** DELETE（§4.5）：软删；被任一发布引用 → 42203。 */
  @Transactional
  public void delete(SessionPrincipal actor, long buildId) {
    require(actor, buildId);
    if (releaseRepository.existsByBuildId(buildId)) {
      throw ApiException.guardNotSatisfied("构建已被发布引用，不能删除。");
    }
    repository.softDelete(buildId);
  }

  Build require(SessionPrincipal actor, long buildId) {
    Build build = repository.findActiveById(buildId).orElseThrow(() -> ApiException.notFound("构建"));
    ProductGuard.requireVisible(productRepository, productApi, actor, build.productId());
    return build;
  }

  Build save(Build build) {
    return repository.update(build).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
  }

  void requireStoriesInProduct(long productId, List<Long> storyIds) {
    if (storyIds.isEmpty()) {
      return;
    }
    List<StoryView> found = storyApi.findByIds(productId, storyIds);
    if (found.size() != storyIds.size()) {
      throw ApiException.guardNotSatisfied("需求不存在或不属于该产品。");
    }
  }

  private static void validateName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw ApiException.validation(Map.of("name", "required"));
    }
    if (name.trim().length() > 150) {
      throw ApiException.validation(Map.of("name", "maxLength"));
    }
  }

  private static void requirePaths(String scmPath, String filePath) {
    if (scmPath != null && scmPath.length() > 255) {
      throw ApiException.validation(Map.of("scmPath", "maxLength"));
    }
    if (filePath != null && filePath.length() > 255) {
      throw ApiException.validation(Map.of("filePath", "maxLength"));
    }
  }

  private static List<Long> distinct(List<Long> ids) {
    return ids == null ? List.of() : new ArrayList<>(new java.util.LinkedHashSet<>(ids));
  }
}
