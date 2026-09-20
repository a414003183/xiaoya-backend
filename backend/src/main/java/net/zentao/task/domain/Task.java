package net.zentao.task.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 任务聚合根（task 卡 §3；纯 Java，A1）。
 * 三件套口径见 {@link TaskHoursPolicy}：consumedHours 不开放直改，只能由动作/工时回写。
 */
public class Task {

  private final long id;
  private long executionId;
  private long projectId;
  private long storyId;
  private long parentId;
  private long categoryId;
  private String title;
  private String type;
  private String status;
  private int priority;
  private BigDecimal estimateHours;
  private BigDecimal consumedHours;
  private BigDecimal leftHours;
  private LocalDate estStartedDate;
  private LocalDate deadline;
  private String assignee;
  private Instant assignedAt;
  private Instant startedAt;
  private Instant activatedAt;
  private String finishedBy;
  private Instant finishedAt;
  private String canceledBy;
  private Instant canceledAt;
  private String closedBy;
  private Instant closedAt;
  private String closedReason;
  private String keywords;
  private String description;
  private boolean isParent;
  private List<String> notifyAccounts;
  private Map<String, Object> customFields;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private int lockVersion;

  public Task(long id, long executionId, long projectId, long storyId, long parentId, long categoryId, String title,
      String type, String status, int priority, BigDecimal estimateHours, BigDecimal consumedHours,
      BigDecimal leftHours, LocalDate estStartedDate, LocalDate deadline, String assignee, Instant assignedAt,
      Instant startedAt, Instant activatedAt, String finishedBy, Instant finishedAt, String canceledBy,
      Instant canceledAt, String closedBy, Instant closedAt, String closedReason, String keywords, String description,
      boolean isParent, List<String> notifyAccounts, Map<String, Object> customFields, String createdBy,
      Instant createdAt, String updatedBy, Instant updatedAt, int lockVersion) {
    this.id = id;
    this.executionId = executionId;
    this.projectId = projectId;
    this.storyId = storyId;
    this.parentId = parentId;
    this.categoryId = categoryId;
    this.title = title;
    this.type = type;
    this.status = status;
    this.priority = priority;
    this.estimateHours = estimateHours;
    this.consumedHours = consumedHours == null ? BigDecimal.ZERO : consumedHours;
    this.leftHours = leftHours;
    this.estStartedDate = estStartedDate;
    this.deadline = deadline;
    this.assignee = assignee;
    this.assignedAt = assignedAt;
    this.startedAt = startedAt;
    this.activatedAt = activatedAt;
    this.finishedBy = finishedBy;
    this.finishedAt = finishedAt;
    this.canceledBy = canceledBy;
    this.canceledAt = canceledAt;
    this.closedBy = closedBy;
    this.closedAt = closedAt;
    this.closedReason = closedReason;
    this.keywords = keywords;
    this.description = description;
    this.isParent = isParent;
    this.notifyAccounts = notifyAccounts == null ? List.of() : List.copyOf(notifyAccounts);
    this.customFields = customFields == null ? Map.of() : Map.copyOf(customFields);
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（§5：title/type/priority/categoryId/storyId/estimateHours/estStartedDate/deadline/keywords/description/notifyAccounts）。 */
  public void update(String title, String type, Integer priority, Long categoryId, Long storyId,
      BigDecimal estimateHours, LocalDate estStartedDate, LocalDate deadline, String keywords, String description,
      List<String> notifyAccounts) {
    if (title != null) {
      this.title = title;
    }
    if (type != null) {
      this.type = type;
    }
    if (priority != null) {
      this.priority = priority;
    }
    if (categoryId != null) {
      this.categoryId = categoryId;
    }
    if (storyId != null) {
      this.storyId = storyId;
    }
    if (estimateHours != null) {
      this.estimateHours = estimateHours;
    }
    if (estStartedDate != null) {
      this.estStartedDate = estStartedDate;
    }
    if (deadline != null) {
      this.deadline = deadline;
    }
    if (keywords != null) {
      this.keywords = blankToNull(keywords);
    }
    if (description != null) {
      this.description = blankToNull(description);
    }
    if (notifyAccounts != null) {
      this.notifyAccounts = List.copyOf(notifyAccounts);
    }
  }

  /** 状态机落状态（workflow/task.yml → WorkflowEngine.applyStatus）。 */
  public void applyStatus(String status) {
    this.status = status;
  }

  /** 有未删子任务即 true；系统维护，不开放写（§3）。 */
  public void setParentFlag(boolean isParent) {
    this.isParent = isParent;
  }

  /** 需求关联联动（finish/activate 后可改派 storyId 之外不改）。 */
  public void linkStory(long storyId) {
    this.storyId = storyId;
  }

  /** fieldSet 副作用入口（workflow/task.yml）：@now/@actor/@null 由 EffectExecutor 解析后传入。 */
  public void setField(String field, Object value) {
    switch (field) {
      case "assignee" -> this.assignee = value == null ? null : String.valueOf(value);
      case "assignedAt" -> this.assignedAt = toInstant(value);
      case "startedAt" -> this.startedAt = toInstant(value);
      case "activatedAt" -> this.activatedAt = toInstant(value);
      case "finishedBy" -> this.finishedBy = value == null ? null : String.valueOf(value);
      case "finishedAt" -> this.finishedAt = toInstant(value);
      case "canceledBy" -> this.canceledBy = value == null ? null : String.valueOf(value);
      case "canceledAt" -> this.canceledAt = toInstant(value);
      case "closedBy" -> this.closedBy = value == null ? null : String.valueOf(value);
      case "closedAt" -> this.closedAt = toInstant(value);
      case "closedReason" -> this.closedReason = value == null ? null : String.valueOf(value);
      case "consumedHours" -> this.consumedHours = toDecimal(value, BigDecimal.ZERO);
      case "leftHours" -> this.leftHours = value == null ? null : toDecimal(value, null);
      case "estimateHours" -> this.estimateHours = value == null ? null : toDecimal(value, null);
      case "status" -> this.status = value == null ? status : String.valueOf(value);
      default -> throw new IllegalArgumentException("任务未声明的 fieldSet 字段：" + field);
    }
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  private static String blankToNull(String value) {
    return value.trim().isEmpty() ? null : value;
  }

  /**
   * 时间戳统一按秒落库：MySQL TIMESTAMP(0) 对小数秒是「四舍五入」而非截断，
   * 同秒内 start→finish 会让库内 startedAt 进位到下一秒，从而误触 startedAt ≤ finishedAt 守卫（T-12 IT 实测）。
   */
  private static Instant toInstant(Object value) {
    if (value == null) {
      return null;
    }
    Instant instant = value instanceof Instant direct ? direct : Instant.parse(String.valueOf(value));
    return instant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
  }

  private static BigDecimal toDecimal(Object value, BigDecimal fallback) {
    if (value == null) {
      return fallback;
    }
    return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
  }

  public long id() {
    return id;
  }

  public long executionId() {
    return executionId;
  }

  public long projectId() {
    return projectId;
  }

  public long storyId() {
    return storyId;
  }

  public long parentId() {
    return parentId;
  }

  public long categoryId() {
    return categoryId;
  }

  public String title() {
    return title;
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

  public BigDecimal consumedHours() {
    return consumedHours;
  }

  public BigDecimal leftHours() {
    return leftHours;
  }

  public LocalDate estStartedDate() {
    return estStartedDate;
  }

  public LocalDate deadline() {
    return deadline;
  }

  public String assignee() {
    return assignee;
  }

  public Instant assignedAt() {
    return assignedAt;
  }

  public Instant startedAt() {
    return startedAt;
  }

  public Instant activatedAt() {
    return activatedAt;
  }

  public String finishedBy() {
    return finishedBy;
  }

  public Instant finishedAt() {
    return finishedAt;
  }

  public String canceledBy() {
    return canceledBy;
  }

  public Instant canceledAt() {
    return canceledAt;
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

  public String keywords() {
    return keywords;
  }

  public String description() {
    return description;
  }

  public boolean isParent() {
    return isParent;
  }

  public List<String> notifyAccounts() {
    return notifyAccounts;
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

  public int lockVersion() {
    return lockVersion;
  }
}
