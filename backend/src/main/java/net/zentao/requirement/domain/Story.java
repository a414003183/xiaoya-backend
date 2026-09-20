package net.zentao.requirement.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 需求聚合根（requirement 卡 §3；纯 Java，A1）：单表三型（story/epic/requirement），version 恒 1。 */
public class Story {

  private final long id;
  private long productId;
  private long branchId;
  private long categoryId;
  private Long planId;
  private Long parentId;
  private String title;
  private String keywords;
  private String type;
  private String status;
  private int priority;
  private BigDecimal estimateHours;
  private String source;
  private String description;
  private String stage;
  private String assignee;
  private Instant assignedAt;
  private List<String> reviewers;
  private boolean needNotReview;
  private List<String> notifyAccounts;
  private List<Long> linkedStoryIds;
  private Long duplicateOfId;
  private int version;
  private Map<String, Object> customFields;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private String closedBy;
  private Instant closedAt;
  private String closedReason;
  private int lockVersion;

  public Story(long id, long productId, long branchId, long categoryId, Long planId, Long parentId, String title,
      String keywords, String type, String status, int priority, BigDecimal estimateHours, String source,
      String description, String stage, String assignee, Instant assignedAt, List<String> reviewers,
      boolean needNotReview, List<String> notifyAccounts, List<Long> linkedStoryIds, Long duplicateOfId, int version,
      Map<String, Object> customFields, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt,
      String closedBy, Instant closedAt, String closedReason, int lockVersion) {
    this.id = id;
    this.productId = productId;
    this.branchId = branchId;
    this.categoryId = categoryId;
    this.planId = planId;
    this.parentId = parentId;
    this.title = title;
    this.keywords = keywords;
    this.type = type;
    this.status = status;
    this.priority = priority;
    this.estimateHours = estimateHours;
    this.source = source;
    this.description = description;
    this.stage = stage;
    this.assignee = assignee;
    this.assignedAt = assignedAt;
    this.reviewers = reviewers == null ? List.of() : List.copyOf(reviewers);
    this.needNotReview = needNotReview;
    this.notifyAccounts = notifyAccounts == null ? List.of() : List.copyOf(notifyAccounts);
    this.linkedStoryIds = linkedStoryIds == null ? List.of() : List.copyOf(linkedStoryIds);
    this.duplicateOfId = duplicateOfId;
    this.version = version;
    this.customFields = customFields == null ? Map.of() : Map.copyOf(customFields);
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.closedBy = closedBy;
    this.closedAt = closedAt;
    this.closedReason = closedReason;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（requirement 卡 §5，含 B-REQ-02 parentId）；null 不改，0 = 清空。 */
  public void update(String title, String keywords, Integer priority, BigDecimal estimateHours, Long categoryId,
      Long planId, Long parentId, String description, List<String> notifyAccounts, List<Long> linkedStoryIds) {
    if (title != null) {
      this.title = title;
    }
    if (keywords != null) {
      this.keywords = keywords;
    }
    if (priority != null) {
      this.priority = priority;
    }
    if (estimateHours != null) {
      this.estimateHours = estimateHours;
    }
    if (categoryId != null) {
      this.categoryId = categoryId;
    }
    if (planId != null) {
      this.planId = planId == 0 ? null : planId;
    }
    if (parentId != null) {
      this.parentId = parentId == 0 ? null : parentId;
    }
    if (description != null) {
      this.description = description;
    }
    if (notifyAccounts != null) {
      this.notifyAccounts = List.copyOf(notifyAccounts);
    }
    if (linkedStoryIds != null) {
      this.linkedStoryIds = List.copyOf(linkedStoryIds);
    }
  }

  /** submit-review 请求体的评审人（处理器在 fire 前落对象，供守卫与通知副作用读取）。 */
  public void reviewBy(List<String> reviewers) {
    if (reviewers != null) {
      this.reviewers = List.copyOf(reviewers);
    }
  }

  public void assignTo(String assignee) {
    this.assignee = assignee;
  }

  /** close 请求体（处理器落库后由 YAML 守卫判定；closedAt/closedBy 由 fieldSet 落）。 */
  public void markClosedReason(String closedReason, Long duplicateOfId) {
    this.closedReason = closedReason;
    this.duplicateOfId = duplicateOfId;
  }

  /** 计划关联/解除（product §4.3 link/unlink 经 StoryApi 落到需求侧）。 */
  public void linkPlan(Long planId) {
    this.planId = planId;
  }

  /**
   * 任务侧联动（task 卡 §4 需求联动）：stage 由关联任务进展重算。
   * released 为终态不被覆盖；其余按事实取 latest：任一任务进行中 → developing，全部完成 → testing，否则 wait。
   */
  public void advanceStage(boolean anyDoing, boolean allDone) {
    if ("released".equals(stage)) {
      return;
    }
    if (anyDoing) {
      this.stage = "developing";
    } else if (allDone) {
      this.stage = "testing";
    } else {
      this.stage = "wait";
    }
  }

  /** 发布创建副作用（product §4.4）：stage → released。 */
  public void markReleased() {
    this.stage = "released";
  }

  public void applyStatus(String status) {
    this.status = status;
  }

  /** fieldSet 副作用入口（story.yml：closedAt/closedBy/closedReason/assignedAt）。 */
  public void setField(String field, Object value) {
    switch (field) {
      case "closedAt" -> this.closedAt = toInstant(value);
      case "closedBy" -> this.closedBy = value == null ? null : String.valueOf(value);
      case "closedReason" -> this.closedReason = value == null ? null : String.valueOf(value);
      case "assignedAt" -> this.assignedAt = toInstant(value);
      case "duplicateOfId" -> this.duplicateOfId = value == null ? null : Long.valueOf(String.valueOf(value));
      default -> throw new IllegalArgumentException("需求未声明的 fieldSet 字段：" + field);
    }
  }

  private static Instant toInstant(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Instant instant) {
      return instant;
    }
    return Instant.parse(String.valueOf(value));
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  public long id() {
    return id;
  }

  public long productId() {
    return productId;
  }

  public long branchId() {
    return branchId;
  }

  public long categoryId() {
    return categoryId;
  }

  public Long planId() {
    return planId;
  }

  public Long parentId() {
    return parentId;
  }

  public String title() {
    return title;
  }

  public String keywords() {
    return keywords;
  }

  public String type() {
    return type;
  }

  public String status() {
    return status;
  }

  public int priority() {
    return priority;
  }

  public BigDecimal estimateHours() {
    return estimateHours;
  }

  public String source() {
    return source;
  }

  public String description() {
    return description;
  }

  public String stage() {
    return stage;
  }

  public String assignee() {
    return assignee;
  }

  public Instant assignedAt() {
    return assignedAt;
  }

  public List<String> reviewers() {
    return reviewers;
  }

  public boolean needNotReview() {
    return needNotReview;
  }

  public List<String> notifyAccounts() {
    return notifyAccounts;
  }

  public List<Long> linkedStoryIds() {
    return linkedStoryIds;
  }

  public Long duplicateOfId() {
    return duplicateOfId;
  }

  public int version() {
    return version;
  }

  public Map<String, Object> customFields() {
    return customFields;
  }

  public String createdBy() {
    return createdBy;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public String updatedBy() {
    return updatedBy;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public String closedBy() {
    return closedBy;
  }

  public Instant closedAt() {
    return closedAt;
  }

  public String closedReason() {
    return closedReason;
  }

  public int lockVersion() {
    return lockVersion;
  }
}
