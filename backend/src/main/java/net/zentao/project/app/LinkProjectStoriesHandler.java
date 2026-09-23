package net.zentao.project.app;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.platform.error.ApiException;
import net.zentao.platform.i18n.MessageResolver;
import net.zentao.platform.session.SessionPrincipal;
import net.zentao.platform.web.BatchActionResult;
import net.zentao.project.api.ProjectApi;
import net.zentao.project.api.ProjectView;
import net.zentao.project.domain.ProjectProductRepository;
import net.zentao.project.domain.ProjectStoryRepository;
import net.zentao.requirement.api.StoryApi;
import net.zentao.requirement.api.StoryView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目/执行关联需求（project 卡 §2/§5 stories 行，旧「项目-需求关联表」语义）：幂等关联 + 逐项结果（部分成功）。
 * 校验口径：需求须存在于「本项目（执行取所属项目）关联产品」之一——按 id 取需求视图后比对 productId，
 * 缺失或产品不符 → 该行 error=42201；重复关联（含重复 id）不增行。
 */
@Component
public class LinkProjectStoriesHandler {

  private final ProjectStoryRepository repository;
  private final ProjectProductRepository projectProductRepository;
  private final StoryApi storyApi;
  private final ProjectApi projectApi;
  private final ProjectQueryService projectQueryService;

  private final MessageResolver messages;

  public LinkProjectStoriesHandler(ProjectStoryRepository repository,
      ProjectProductRepository projectProductRepository, StoryApi storyApi, ProjectApi projectApi,
      ProjectQueryService projectQueryService,
      MessageResolver messages) {
    this.repository = repository;
    this.projectProductRepository = projectProductRepository;
    this.storyApi = storyApi;
    this.projectApi = projectApi;
    this.projectQueryService = projectQueryService;
    this.messages = messages;
  }

  public record StoryLinkRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> storyIds) {}

  @Transactional
  public BatchActionResult handle(SessionPrincipal actor, String objectType, long objectId,
      StoryLinkRequest command) {
    projectQueryService.requireVisible(actor, objectType, objectId);
    List<Long> storyIds = command == null ? null : command.storyIds();
    if (storyIds == null) {
      throw ApiException.validation(Map.of("storyIds", "required"));
    }
    List<Long> distinct = storyIds.stream().distinct().toList();
    if (distinct.isEmpty()) {
      return new BatchActionResult(List.of());
    }
    Set<Long> allowedProducts = Set.copyOf(
        projectProductRepository.productIdsOf(projectIdsOf(objectType, objectId)));
    Map<Long, StoryView> stories = new LinkedHashMap<>();
    for (StoryView story : storyApi.findByIds(distinct)) {
      stories.put(story.id(), story);
    }
    List<BatchActionResult.Item> results = new ArrayList<>(distinct.size());
    Map<Long, List<Long>> byProduct = new LinkedHashMap<>();
    for (Long storyId : distinct) {
      StoryView story = stories.get(storyId);
      if (story == null || !allowedProducts.contains(story.productId())) {
        results.add(BatchActionResult.failed(storyId, "42201:" + messages.forRequest("project.link.storyNotInProduct")));
        continue;
      }
      byProduct.computeIfAbsent(story.productId(), productId -> new ArrayList<>()).add(storyId);
      results.add(BatchActionResult.ok(storyId));
    }
    byProduct.forEach((productId, ids) -> repository.link(objectId, ids, productId));
    return new BatchActionResult(results);
  }

  /**
   * 解除关联（B-PRJ-06）：删 project_story 行，幂等（行不存在也 200 data:null）。
   * 执行侧只删执行自身行（project_id=执行 id），项目级行不受影响。
   */
  @Transactional
  public void unlink(SessionPrincipal actor, String objectType, long objectId, long storyId) {
    projectQueryService.requireVisible(actor, objectType, objectId);
    repository.unlink(objectId, storyId);
  }

  /** 关联校验的产品来源：执行取其所属项目，项目取自身。 */
  private List<Long> projectIdsOf(String objectType, long objectId) {
    if (!"execution".equals(objectType)) {
      return List.of(objectId);
    }
    return List.of(projectApi.findById(objectId).map(ProjectView::parentId).orElse(0L));
  }
}
