package net.zentao.quality.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Bug 聚合根（quality 卡 §3.1；纯 Java，A1）。 */
public class Bug {

  private final long id;
  private long productId;
  private long branchId;
  private long categoryId;
  private long projectId;
  private long executionId;
  private Long planId;
  private Long storyId;
  private Long taskId;
  private Long testCaseId;
  private Long testRunId;
  private String title;
  private String keywords;
  private int severity;
  private int priority;
  private String type;
  private String os;
  private String browser;
  private String steps;
  private String openedBuilds;
  private String status;
  private boolean confirmed;
  private int activatedCount;
  private LocalDate deadline;
  private String assignee;
  private Instant assignedAt;
  private String resolution;
  private String resolvedBy;
  private Instant resolvedAt;
  private String resolvedBuild;
  private Long duplicateOfId;
  private List<Long> relatedBugIds;
  private List<String> notifyAccounts;
  private String closedBy;
  private Instant closedAt;
  private Map<String, Object> customFields;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private int lockVersion;

  public Bug(long id, long productId, long branchId, long categoryId, long projectId, long executionId,
      Long planId, Long storyId, Long taskId, Long testCaseId, Long testRunId, String title, String keywords,
      int severity, int priority, String type, String os, String browser, String steps, String openedBuilds,
      String status, boolean confirmed, int activatedCount, LocalDate deadline, String assignee,
      Instant assignedAt, String resolution, String resolvedBy, Instant resolvedAt, String resolvedBuild,
      Long duplicateOfId, List<Long> relatedBugIds, List<String> notifyAccounts, String closedBy,
      Instant closedAt, Map<String, Object> customFields, String createdBy, Instant createdAt, String updatedBy,
      Instant updatedAt, int lockVersion) {
    this.id = id;
    this.productId = productId;
    this.branchId = branchId;
    this.categoryId = categoryId;
    this.projectId = projectId;
    this.executionId = executionId;
    this.planId = planId;
    this.storyId = storyId;
    this.taskId = taskId;
    this.testCaseId = testCaseId;
    this.testRunId = testRunId;
    this.title = title;
    this.keywords = keywords;
    this.severity = severity;
    this.priority = priority;
    this.type = type;
    this.os = os;
    this.browser = browser;
    this.steps = steps;
    this.openedBuilds = openedBuilds;
    this.status = status;
    this.confirmed = confirmed;
    this.activatedCount = activatedCount;
    this.deadline = deadline;
    this.assignee = assignee;
    this.assignedAt = assignedAt;
    this.resolution = resolution;
    this.resolvedBy = resolvedBy;
    this.resolvedAt = resolvedAt;
    this.resolvedBuild = resolvedBuild;
    this.duplicateOfId = duplicateOfId;
    this.relatedBugIds = relatedBugIds == null ? List.of() : List.copyOf(relatedBugIds);
    this.notifyAccounts = notifyAccounts == null ? List.of() : List.copyOf(notifyAccounts);
    this.closedBy = closedBy;
    this.closedAt = closedAt;
    this.customFields = customFields == null ? Map.of() : Map.copyOf(customFields);
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（quality 卡 §5）；null 不改，id 型 0 置空。 */
  public void update(String title, String keywords, Integer severity, Integer priority, String type, String os,
      String browser, String steps, String openedBuilds, Long categoryId, Long executionId, Long planId,
      Long storyId, Long taskId, Long testCaseId, LocalDate deadline, List<Long> relatedBugIds,
      List<String> notifyAccounts) {
    if (title != null) {
      this.title = title;
    }
    if (keywords != null) {
      this.keywords = keywords;
    }
    if (severity != null) {
      this.severity = severity;
    }
    if (priority != null) {
      this.priority = priority;
    }
    if (type != null) {
      this.type = type;
    }
    if (os != null) {
      this.os = os;
    }
    if (browser != null) {
      this.browser = browser;
    }
    if (steps != null) {
      this.steps = steps;
    }
    if (openedBuilds != null) {
      this.openedBuilds = openedBuilds;
    }
    if (categoryId != null) {
      this.categoryId = categoryId;
    }
    if (executionId != null) {
      this.executionId = executionId;
    }
    if (planId != null) {
      this.planId = planId == 0 ? null : planId;
    }
    if (storyId != null) {
      this.storyId = storyId == 0 ? null : storyId;
    }
    if (taskId != null) {
      this.taskId = taskId == 0 ? null : taskId;
    }
    if (testCaseId != null) {
      this.testCaseId = testCaseId == 0 ? null : testCaseId;
    }
    if (deadline != null) {
      this.deadline = deadline;
    }
    if (relatedBugIds != null) {
      this.relatedBugIds = List.copyOf(relatedBugIds);
    }
    if (notifyAccounts != null) {
      this.notifyAccounts = List.copyOf(notifyAccounts);
    }
  }

  /** confirm 请求体：confirmed 置位；顺带改派落 assignedAt（quality 卡 §4.1/§8）。 */
  public void confirmBy(String assignee) {
    this.confirmed = true;
    if (assignee != null) {
      this.assignee = assignee;
      this.assignedAt = Instant.now();
    }
  }

  /** resolve 请求体字段落对象（守卫与动态流 detail 读取；resolvedBy/resolvedAt 由 fieldSet 落）。 */
  public void resolveBy(String resolution, String resolvedBuild, Long duplicateOfId, String assignee,
      Long storyId) {
    this.resolution = resolution;
    this.resolvedBuild = resolvedBuild;
    this.duplicateOfId = duplicateOfId;
    if (assignee != null) {
      this.assignee = assignee;
    }
    if (storyId != null) {
      this.storyId = storyId;
    }
  }

  /** activate：activatedCount+1；assignee 省略回派原解决人；resolution/resolvedBy/resolvedAt 清空（§4.1）。 */
  public void activate(String assignee) {
    String fallback = resolvedBy;
    this.activatedCount += 1;
    this.assignee = assignee != null ? assignee : fallback;
    this.assignedAt = Instant.now();
    this.resolution = null;
    this.resolvedBy = null;
    this.resolvedAt = null;
  }

  /** activate 请求体的 openedBuilds（fire 前落对象，供守卫读取）。 */
  public void markOpenedBuilds(String openedBuilds) {
    this.openedBuilds = openedBuilds;
  }

  /** assign 请求体。 */
  public void assignTo(String assignee) {
    this.assignee = assignee;
  }

  public void applyStatus(String status) {
    this.status = status;
  }

  /** fieldSet 副作用入口（bug.yml：resolvedBy/resolvedAt/closedBy/closedAt/assignedAt）。 */
  public void setField(String field, Object value) {
    switch (field) {
      case "resolvedBy" -> this.resolvedBy = value == null ? null : String.valueOf(value);
      case "resolvedAt" -> this.resolvedAt = toInstant(value);
      case "closedBy" -> this.closedBy = value == null ? null : String.valueOf(value);
      case "closedAt" -> this.closedAt = toInstant(value);
      case "assignedAt" -> this.assignedAt = toInstant(value);
      default -> throw new IllegalArgumentException("Bug 未声明的 fieldSet 字段：" + field);
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

  public long projectId() {
    return projectId;
  }

  public long executionId() {
    return executionId;
  }

  public Long planId() {
    return planId;
  }

  public Long storyId() {
    return storyId;
  }

  public Long taskId() {
    return taskId;
  }

  public Long testCaseId() {
    return testCaseId;
  }

  public Long testRunId() {
    return testRunId;
  }

  public String title() {
    return title;
  }

  public String keywords() {
    return keywords;
  }

  public int severity() {
    return severity;
  }

  public int priority() {
    return priority;
  }

  public String type() {
    return type;
  }

  public String os() {
    return os;
  }

  public String browser() {
    return browser;
  }

  public String steps() {
    return steps;
  }

  public String openedBuilds() {
    return openedBuilds;
  }

  public String status() {
    return status;
  }

  public boolean confirmed() {
    return confirmed;
  }

  public int activatedCount() {
    return activatedCount;
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

  public String resolution() {
    return resolution;
  }

  public String resolvedBy() {
    return resolvedBy;
  }

  public Instant resolvedAt() {
    return resolvedAt;
  }

  public String resolvedBuild() {
    return resolvedBuild;
  }

  public Long duplicateOfId() {
    return duplicateOfId;
  }

  public List<Long> relatedBugIds() {
    return relatedBugIds;
  }

  public List<String> notifyAccounts() {
    return notifyAccounts;
  }

  public String closedBy() {
    return closedBy;
  }

  public Instant closedAt() {
    return closedAt;
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
