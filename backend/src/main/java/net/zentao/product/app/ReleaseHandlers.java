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
import net.zentao.platform.notification.NotificationRecorder;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.workflow.WorkflowEngine;
import net.zentao.product.api.ProductApi;
import net.zentao.product.api.ReleaseView;
import net.zentao.product.domain.BuildRepository;
import net.zentao.product.domain.ProductRepository;
import net.zentao.product.domain.Release;
import net.zentao.product.domain.ReleaseRepository;
import net.zentao.quality.api.BugApi;
import net.zentao.requirement.api.StoryApi;
import net.zentao.requirement.api.StoryView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发布命令（product 卡 §3.5/§4.4/§5）：创建（storyIds 需求 stage→released + 需求侧 linked2release 动态流
 * + 通知 notifyAccounts）、编辑、terminate（走 workflow/release.yml，通知 notifyAccounts）。
 */
@Component
public class ReleaseHandlers {

  private final ReleaseRepository repository;
  private final BuildRepository buildRepository;
  private final ProductRepository productRepository;
  private final ProductApi productApi;
  private final AccountApi accountApi;
  private final StoryApi storyApi;
  private final BugApi bugApi;
  private final WorkflowEngine engine;
  private final ActivityRecorder activityRecorder;
  private final NotificationRecorder notificationRecorder;

  public ReleaseHandlers(ReleaseRepository repository, BuildRepository buildRepository,
      ProductRepository productRepository, ProductApi productApi, AccountApi accountApi, StoryApi storyApi,
      BugApi bugApi, WorkflowEngine engine, ActivityRecorder activityRecorder,
      NotificationRecorder notificationRecorder) {
    this.repository = repository;
    this.buildRepository = buildRepository;
    this.productRepository = productRepository;
    this.productApi = productApi;
    this.accountApi = accountApi;
    this.storyApi = storyApi;
    this.bugApi = bugApi;
    this.engine = engine;
    this.activityRecorder = activityRecorder;
    this.notificationRecorder = notificationRecorder;
  }

  public record ReleaseCreateRequest(Long branchId, Long buildId, Long projectId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate releaseDate,
      Instant publishedAt, Boolean isMilestone, List<Long> storyIds, List<Long> bugIds, List<String> notifyAccounts,
      String description) {}

  public record ReleaseUpdateRequest(String name, Long branchId, Long buildId, Long projectId, LocalDate releaseDate,
      Instant publishedAt, Boolean isMilestone, List<String> notifyAccounts, String description,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lockVersion) {}

  @Transactional
  public ReleaseView create(SessionPrincipal actor, long productId, ReleaseCreateRequest command) {
    ProductGuard.requireVisible(productRepository, productApi, actor, productId);
    validateName(command.name());
    if (command.releaseDate() == null) {
      throw ApiException.validation(Map.of("releaseDate", "required"));
    }
    requireBuild(productId, command.buildId());
    List<Long> storyIds = distinct(command.storyIds());
    requireStoriesInProduct(productId, storyIds);
    requireBugsInProduct(productId, distinct(command.bugIds()));
    requireAccounts(command.notifyAccounts());

    Instant now = Instant.now();
    Release release = repository.insert(new Release(0, productId,
        command.branchId() == null ? 0 : command.branchId(), command.buildId(),
        command.projectId() == null ? 0 : command.projectId(), command.name().trim(), "normal",
        command.releaseDate(), command.publishedAt() == null ? now : command.publishedAt(),
        command.isMilestone() != null && command.isMilestone(), storyIds, distinct(command.bugIds()),
        command.notifyAccounts(), command.description(), actor.account(), now, null, null, 0));

    Long activityId = activityRecorder.record(actor.account(), "release", release.id(), "created", null, null);
    // §4.4：关联需求 stage → released，需求侧落 linked2release 动态流
    if (!storyIds.isEmpty()) {
      storyApi.markReleased(storyIds, actor.account());
    }
    if (!release.notifyAccounts().isEmpty()) {
      notificationRecorder.record(release.notifyAccounts(), "release-created", "release", release.id(), activityId,
          release.name(), null, actor.account());
    }
    return ReleaseView.of(release);
  }

  @Transactional
  public ReleaseView update(SessionPrincipal actor, long releaseId, ReleaseUpdateRequest command) {
    Release release = require(actor, releaseId);
    if (command.lockVersion() == null || command.lockVersion() != release.lockVersion()) {
      throw ApiException.lockConflict("数据已被他人修改，请刷新后重试。");
    }
    if (command.name() != null) {
      validateName(command.name());
    }
    requireBuild(release.productId(), command.buildId());
    requireAccounts(command.notifyAccounts());
    release.update(command.name() == null ? null : command.name().trim(), command.branchId(), command.buildId(),
        command.projectId(), command.releaseDate(), command.publishedAt(), command.isMilestone(),
        command.notifyAccounts(), command.description());
    release.markUpdatedBy(actor.account());
    return ReleaseView.of(save(release));
  }

  /** DELETE（§5，A-07）：叶子对象直接软删，关联数组随之失效。 */
  @Transactional
  public void delete(SessionPrincipal actor, long releaseId) {
    require(actor, releaseId);
    repository.softDelete(releaseId);
  }

  @Transactional
  public ReleaseView terminate(SessionPrincipal actor, long releaseId, String comment) {
    Release release = require(actor, releaseId);
    engine.fire(new WorkflowTargets.ReleaseTarget(release, actor.account()), "terminate", comment);
    release.markUpdatedBy(actor.account());
    return ReleaseView.of(save(release));
  }

  Release require(SessionPrincipal actor, long releaseId) {
    Release release = repository.findActiveById(releaseId).orElseThrow(() -> ApiException.notFound("发布"));
    ProductGuard.requireVisible(productRepository, productApi, actor, release.productId());
    return release;
  }

  Release save(Release release) {
    return repository.update(release).orElseThrow(() -> ApiException.lockConflict("数据已被他人修改，请刷新后重试。"));
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

  void requireBugsInProduct(long productId, List<Long> bugIds) {
    bugApi.requireInProduct(productId, bugIds);
  }

  private void requireBuild(long productId, Long buildId) {
    if (buildId == null || buildId == 0) {
      return;
    }
    if (buildRepository.findActiveById(buildId).filter(build -> build.productId() == productId).isEmpty()) {
      throw ApiException.validation(Map.of("buildId", "notFound"));
    }
  }

  private void requireAccounts(List<String> accounts) {
    if (accounts == null || accounts.isEmpty()) {
      return;
    }
    if (!accountApi.missingAccounts(accounts).isEmpty()) {
      throw ApiException.validation(Map.of("notifyAccounts", "notFound"));
    }
  }

  private static void validateName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw ApiException.validation(Map.of("name", "required"));
    }
    if (name.trim().length() > 90) {
      throw ApiException.validation(Map.of("name", "maxLength"));
    }
  }

  private static List<Long> distinct(List<Long> ids) {
    return ids == null ? List.of() : new ArrayList<>(new java.util.LinkedHashSet<>(ids));
  }
}
