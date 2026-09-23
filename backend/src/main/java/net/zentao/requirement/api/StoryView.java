package net.zentao.requirement.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.zentao.requirement.domain.Story;

/** 需求视图（contract：StoryView；requirement 卡 §3 读侧全字段）。 */
public record StoryView(
    long id,
    long productId,
    long branchId,
    long categoryId,
    Long planId,
    Long parentId,
    String title,
    String keywords,
    @Schema(allowableValues = {"epic", "requirement", "story"}) String type,
    @Schema(allowableValues = {"active", "changed", "changing", "closed", "draft", "reviewing"}) String status,
    int priority,
    BigDecimal estimateHours,
    @Schema(allowableValues = {"bug", "customer", "manual", "market", "other"}) String source,
    String description,
    @Schema(allowableValues = {"developing", "released", "testing", "wait"}) String stage,
    String assignee,
    Instant assignedAt,
    List<String> reviewers,
    boolean needNotReview,
    List<String> notifyAccounts,
    List<Long> linkedStoryIds,
    Long duplicateOfId,
    int version,
    Map<String, Object> customFields,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    String closedBy,
    Instant closedAt,
    @Schema(allowableValues = {"done", "duplicate", "postponed", "rejected", "willnotfix"}) String closedReason,
    int lockVersion) {

  public static StoryView of(Story story) {
    return new StoryView(story.id(), story.productId(), story.branchId(), story.categoryId(), story.planId(),
        story.parentId(), story.title(), story.keywords(), story.type(), story.status(), story.priority(),
        story.estimateHours(), story.source(), story.description(), story.stage(), story.assignee(),
        story.assignedAt(), story.reviewers(), story.needNotReview(), story.notifyAccounts(), story.linkedStoryIds(),
        story.duplicateOfId(), story.version(), story.customFields(), story.createdBy(), story.createdAt(),
        story.updatedBy(), story.updatedAt(), story.closedBy(), story.closedAt(), story.closedReason(),
        story.lockVersion());
  }
}
