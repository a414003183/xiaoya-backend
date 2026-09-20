package net.zentao.requirement.app;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zentao.org.api.AccountApi;
import net.zentao.platform.error.ApiException;
import net.zentao.requirement.domain.Story;
import net.zentao.requirement.domain.StoryRepository;

/** 需求字段校验（requirement 卡 §3 校验/取值列；create 与 update 同源，null = 不改不校验）。 */
final class StoryFields {

  static final Set<String> TYPES = Set.of("story", "epic", "requirement");
  static final Set<String> SOURCES = Set.of("manual", "customer", "market", "bug", "other");
  static final Set<String> CLOSE_REASONS = Set.of("done", "duplicate", "rejected", "willnotfix", "postponed");
  static final int REVIEWERS_MAX = 20;
  static final BigDecimal ESTIMATE_MAX = new BigDecimal("999.99");

  private StoryFields() {}

  static void validate(String title, String keywords, String type, Integer priority, BigDecimal estimateHours,
      String source, List<String> reviewers, String assignee, List<String> notifyAccounts, AccountApi accountApi) {
    Map<String, String> errors = new LinkedHashMap<>();
    if (title != null) {
      String trimmed = title.trim();
      if (trimmed.isEmpty()) {
        errors.put("title", "required");
      } else if (trimmed.length() > 255) {
        errors.put("title", "maxLength");
      }
    }
    if (keywords != null && keywords.length() > 255) {
      errors.put("keywords", "maxLength");
    }
    if (type != null && !TYPES.contains(type)) {
      errors.put("type", "invalid");
    }
    if (priority != null && (priority < 1 || priority > 4)) {
      errors.put("priority", "invalidRange");
    }
    if (estimateHours != null
        && (estimateHours.signum() < 0 || estimateHours.compareTo(ESTIMATE_MAX) > 0)) {
      errors.put("estimateHours", "invalidRange");
    }
    if (source != null && !SOURCES.contains(source)) {
      errors.put("source", "invalid");
    }
    if (reviewers != null && reviewers.size() > REVIEWERS_MAX) {
      errors.put("reviewers", "tooMany");
    }
    List<String> referenced = new ArrayList<>();
    referenced.add(assignee);
    if (reviewers != null) {
      referenced.addAll(reviewers);
    }
    if (notifyAccounts != null) {
      referenced.addAll(notifyAccounts);
    }
    List<String> missing = accountApi.missingAccounts(referenced);
    if (!missing.isEmpty()) {
      if (assignee != null && missing.contains(assignee)) {
        errors.put("assignee", "notFound");
      }
      if (reviewers != null && reviewers.stream().anyMatch(missing::contains)) {
        errors.put("reviewers", "notFound");
      }
      if (notifyAccounts != null && notifyAccounts.stream().anyMatch(missing::contains)) {
        errors.put("notifyAccounts", "notFound");
      }
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
  }

  /** 评审人列表校验（submit-review 请求体）：≤20 且账号存在。 */
  static void validateReviewers(List<String> reviewers, AccountApi accountApi) {
    if (reviewers == null) {
      return;
    }
    if (reviewers.size() > REVIEWERS_MAX) {
      throw ApiException.validation(Map.of("reviewers", "tooMany"));
    }
    if (!accountApi.missingAccounts(reviewers).isEmpty()) {
      throw ApiException.validation(Map.of("reviewers", "notFound"));
    }
  }

  /** 指派账号校验（assign 请求体）。 */
  static void validateAssignee(String assignee, AccountApi accountApi) {
    if (assignee == null || assignee.isBlank()) {
      throw ApiException.validation(Map.of("assignee", "required"));
    }
    if (!accountApi.missingAccounts(List.of(assignee)).isEmpty()) {
      throw ApiException.validation(Map.of("assignee", "notFound"));
    }
  }

  /** close 请求体：closedReason 必填且在枚举内；duplicate 时 duplicateOfId 必填且同产品。 */
  static void validateClose(String closedReason, Long duplicateOfId, long productId, StoryRepository repository) {
    if (closedReason == null || !CLOSE_REASONS.contains(closedReason)) {
      throw ApiException.validation(Map.of("closedReason", "invalid"));
    }
    if ("duplicate".equals(closedReason)) {
      if (duplicateOfId == null || duplicateOfId == 0) {
        throw ApiException.validation(Map.of("duplicateOfId", "required"));
      }
      Story target = repository.findActiveById(duplicateOfId).orElse(null);
      if (target == null || target.productId() != productId) {
        throw ApiException.validation(Map.of("duplicateOfId", "notFound"));
      }
    }
  }

  /** parentId（epic 才可作父）与 linkedStoryIds（同产品需求）的引用校验。 */
  static void validateReferences(long productId, Long parentId, List<Long> linkedStoryIds,
      StoryRepository repository) {
    Map<String, String> errors = new LinkedHashMap<>();
    if (parentId != null && parentId != 0) {
      Story parent = repository.findActiveById(parentId).orElse(null);
      if (parent == null) {
        errors.put("parentId", "notFound");
      } else if (parent.productId() != productId) {
        errors.put("parentId", "crossProduct");
      } else if (!"epic".equals(parent.type())) {
        errors.put("parentId", "notEpic");
      }
    }
    if (linkedStoryIds != null && !linkedStoryIds.isEmpty()) {
      List<Story> found = repository.findActiveByIds(linkedStoryIds);
      boolean allInProduct = found.size() == linkedStoryIds.stream().distinct().count()
          && found.stream().allMatch(story -> story.productId() == productId);
      if (!allInProduct) {
        errors.put("linkedStoryIds", "crossProduct");
      }
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
  }

  /**
   * 改父守卫（B-REQ-02，requirement 卡 §5 补口）：指向自身或沿 parent 链上溯遇自身（成环）→ 42203；
   * 目标不存在/跨产品/非 epic → 42201（与创建口径一致）；null = 不修改，0 = 清空为独立需求。
   */
  static void validateParentChange(Story story, Long parentId, StoryRepository repository) {
    if (parentId == null || parentId == 0) {
      return;
    }
    if (parentId == story.id()) {
      throw ApiException.guardNotSatisfied("父需求不能指向自身。");
    }
    StoryFields.validateReferences(story.productId(), parentId, List.of(), repository);
    // 成环检测：从目标父沿链上溯，遇自身即环（epic 链短，逐级查库即可）
    Long cursor = repository.findActiveById(parentId).map(Story::parentId).orElse(null);
    Set<Long> visited = new HashSet<>();
    while (cursor != null && cursor != 0) {
      if (cursor == story.id()) {
        throw ApiException.guardNotSatisfied("父需求不能形成环。");
      }
      if (!visited.add(cursor)) {
        return; // 脏数据自环保护：链上既有环与本次修改无关
      }
      cursor = repository.findActiveById(cursor).map(Story::parentId).orElse(null);
    }
  }
}
