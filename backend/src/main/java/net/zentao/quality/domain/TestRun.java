package net.zentao.quality.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 测试单聚合根（quality 卡 §3.4；纯 Java，A1）。 */
public class TestRun {

  private final long id;
  private long productId;
  private long projectId;
  private long executionId;
  private long buildId;
  private String name;
  private String owner;
  private int priority;
  private String type;
  private LocalDate beginDate;
  private LocalDate endDate;
  private Instant realBeganAt;
  private Instant realFinishedAt;
  private String description;
  private List<String> members;
  private List<String> notifyAccounts;
  private String status;
  private Long reportId;
  private Map<String, Object> customFields;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private int lockVersion;

  public TestRun(long id, long productId, long projectId, long executionId, long buildId, String name,
      String owner, int priority, String type, LocalDate beginDate, LocalDate endDate, Instant realBeganAt,
      Instant realFinishedAt, String description, List<String> members, List<String> notifyAccounts,
      String status, Long reportId, Map<String, Object> customFields, String createdBy, Instant createdAt,
      String updatedBy, Instant updatedAt, int lockVersion) {
    this.id = id;
    this.productId = productId;
    this.projectId = projectId;
    this.executionId = executionId;
    this.buildId = buildId;
    this.name = name;
    this.owner = owner;
    this.priority = priority;
    this.type = type;
    this.beginDate = beginDate;
    this.endDate = endDate;
    this.realBeganAt = realBeganAt;
    this.realFinishedAt = realFinishedAt;
    this.description = description;
    this.members = members == null ? List.of() : List.copyOf(members);
    this.notifyAccounts = notifyAccounts == null ? List.of() : List.copyOf(notifyAccounts);
    this.status = status;
    this.reportId = reportId;
    this.customFields = customFields == null ? Map.of() : Map.copyOf(customFields);
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（quality 卡 §5）；null 不改；日期成对校验在处理器。 */
  public void update(String name, String owner, Integer priority, String type, LocalDate beginDate,
      LocalDate endDate, Long buildId, String description, List<String> members, List<String> notifyAccounts) {
    if (name != null) {
      this.name = name;
    }
    if (owner != null) {
      this.owner = owner;
    }
    if (priority != null) {
      this.priority = priority;
    }
    if (type != null) {
      this.type = type;
    }
    if (beginDate != null) {
      this.beginDate = beginDate;
    }
    if (endDate != null) {
      this.endDate = endDate;
    }
    if (buildId != null) {
      this.buildId = buildId;
    }
    if (description != null) {
      this.description = description;
    }
    if (members != null) {
      this.members = List.copyOf(members);
    }
    if (notifyAccounts != null) {
      this.notifyAccounts = List.copyOf(notifyAccounts);
    }
  }

  /** close 守卫（quality 卡 §4.3）：realFinishedAt ≥ beginDate 且 ≤ endDate 次日。 */
  public void requireClosable(Instant realFinishedAt) {
    if (realFinishedAt == null) {
      return; // 必填校验在处理器（42201）
    }
    LocalDate day = LocalDate.ofInstant(realFinishedAt, java.time.ZoneOffset.UTC);
    if (day.isBefore(beginDate) || day.isAfter(endDate.plusDays(1))) {
      throw new IllegalArgumentException("realFinishedAt 须不早于开始日期且不晚于结束日期次日。");
    }
  }

  /** close 请求体落对象（fire 前写入，供守卫读取）。 */
  public void closeWith(Instant realFinishedAt) {
    this.realFinishedAt = realFinishedAt;
  }

  /** Report 创建回填（§3.6 只读列，仅 quality 域内写）。 */
  public void attachReport(long reportId) {
    this.reportId = reportId;
  }

  public void applyStatus(String status) {
    this.status = status;
  }

  /** fieldSet 副作用入口（test-run.yml：realBeganAt）。 */
  public void setField(String field, Object value) {
    switch (field) {
      case "realBeganAt" -> this.realBeganAt = toInstant(value);
      default -> throw new IllegalArgumentException("测试单未声明的 fieldSet 字段：" + field);
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

  public long projectId() {
    return projectId;
  }

  public long executionId() {
    return executionId;
  }

  public long buildId() {
    return buildId;
  }

  public String name() {
    return name;
  }

  public String owner() {
    return owner;
  }

  public int priority() {
    return priority;
  }

  public String type() {
    return type;
  }

  public LocalDate beginDate() {
    return beginDate;
  }

  public LocalDate endDate() {
    return endDate;
  }

  public Instant realBeganAt() {
    return realBeganAt;
  }

  public Instant realFinishedAt() {
    return realFinishedAt;
  }

  public String description() {
    return description;
  }

  public List<String> members() {
    return members;
  }

  public List<String> notifyAccounts() {
    return notifyAccounts;
  }

  public String status() {
    return status;
  }

  public Long reportId() {
    return reportId;
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
