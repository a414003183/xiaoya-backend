package net.zentao.project.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 项目聚合根（project 卡 §3.1：一表三义 program/project/execution；纯 Java，A1）。
 * 白名单不入聚合列，由 acl_entry 单独承载（§2 单源），仓储装配时读入 {@link #whitelist()}。
 */
public class Project {

  private final long id;
  private String type;
  private long parentId;
  private String path;
  private int grade;
  private String name;
  private String code;
  private String model;
  private String status;
  private int priority;
  private LocalDate beginDate;
  private LocalDate endDate;
  private LocalDate firstEndDate;
  private LocalDate realBeganDate;
  private LocalDate realEndDate;
  private int days;
  private BigDecimal budget;
  private String budgetUnit;
  private String description;
  private String pm;
  private String po;
  private String qd;
  private String rd;
  private int progress;
  private BigDecimal estimateHours;
  private BigDecimal consumedHours;
  private BigDecimal leftHours;
  private boolean isMilestone;
  private String acl;
  private List<String> whitelist;
  private int sort;
  private Map<String, Object> customFields;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private String closedBy;
  private Instant closedAt;
  private int lockVersion;

  public Project(long id, String type, long parentId, String path, int grade, String name, String code, String model,
      String status, int priority, LocalDate beginDate, LocalDate endDate, LocalDate firstEndDate,
      LocalDate realBeganDate, LocalDate realEndDate, int days, BigDecimal budget, String budgetUnit,
      String description, String pm, String po, String qd, String rd, int progress, BigDecimal estimateHours,
      BigDecimal consumedHours, BigDecimal leftHours, boolean isMilestone, String acl, List<String> whitelist,
      int sort, Map<String, Object> customFields, String createdBy, Instant createdAt, String updatedBy,
      Instant updatedAt, String closedBy, Instant closedAt, int lockVersion) {
    this.id = id;
    this.type = type;
    this.parentId = parentId;
    this.path = path;
    this.grade = grade;
    this.name = name;
    this.code = code;
    this.model = model;
    this.status = status;
    this.priority = priority;
    this.beginDate = beginDate;
    this.endDate = endDate;
    this.firstEndDate = firstEndDate;
    this.realBeganDate = realBeganDate;
    this.realEndDate = realEndDate;
    this.days = days;
    this.budget = budget;
    this.budgetUnit = budgetUnit;
    this.description = description;
    this.pm = pm;
    this.po = po;
    this.qd = qd;
    this.rd = rd;
    this.progress = progress;
    this.estimateHours = estimateHours;
    this.consumedHours = consumedHours;
    this.leftHours = leftHours;
    this.isMilestone = isMilestone;
    this.acl = acl;
    this.whitelist = whitelist == null ? List.of() : List.copyOf(whitelist);
    this.sort = sort;
    this.customFields = customFields == null ? Map.of() : Map.copyOf(customFields);
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.closedBy = closedBy;
    this.closedAt = closedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单字段合并（project 卡 §5 三型 PATCH 可写字段并集；null 不改）。 */
  public void update(String name, String code, String model, LocalDate beginDate, LocalDate endDate, Integer days,
      BigDecimal budget, String pm, String po, String qd, String rd, String acl, List<String> whitelist,
      String description, Integer sort, Boolean isMilestone) {
    if (name != null) {
      this.name = name;
    }
    if (code != null) {
      this.code = code;
    }
    if (model != null) {
      this.model = model;
    }
    if (beginDate != null) {
      this.beginDate = beginDate;
    }
    if (endDate != null) {
      this.endDate = endDate;
    }
    if (days != null) {
      this.days = days;
    }
    if (budget != null) {
      this.budget = budget;
    }
    if (pm != null) {
      this.pm = pm;
    }
    if (po != null) {
      this.po = po;
    }
    if (qd != null) {
      this.qd = qd;
    }
    if (rd != null) {
      this.rd = rd;
    }
    if (acl != null) {
      this.acl = acl;
    }
    if (whitelist != null) {
      this.whitelist = List.copyOf(whitelist);
    }
    if (description != null) {
      this.description = description;
    }
    if (sort != null) {
      this.sort = sort;
    }
    if (isMilestone != null) {
      this.isMilestone = isMilestone;
    }
  }

  /** 状态机落状态（workflow/project.yml → WorkflowEngine.applyStatus）。 */
  public void applyStatus(String status) {
    this.status = status;
  }

  /**
   * 首次计划完成日：start 时若为空则记住当时的 endDate（project 卡 §3.1「延期比对」）。
   * 后续改 endDate 不再覆盖——这正是它区别于 endDate 的意义。
   */
  public void rememberFirstEndDate() {
    if (firstEndDate == null && endDate != null) {
      firstEndDate = endDate;
    }
  }

  /** fieldSet 副作用入口（workflow/project.yml）：只接受本聚合声明的字段。 */
  public void setField(String field, Object value) {
    switch (field) {
      case "closedBy" -> this.closedBy = value == null ? null : String.valueOf(value);
      case "closedAt" -> this.closedAt = value == null ? null : toInstant(value);
      case "realBeganDate" -> this.realBeganDate = value == null ? null : toDate(value);
      case "realEndDate" -> this.realEndDate = value == null ? null : toDate(value);
      case "status" -> this.status = value == null ? status : String.valueOf(value);
      default -> throw new IllegalArgumentException("项目未声明的 fieldSet 字段：" + field);
    }
  }

  private static Instant toInstant(Object value) {
    return value instanceof Instant instant ? instant : Instant.parse(String.valueOf(value));
  }

  private static LocalDate toDate(Object value) {
    if (value instanceof LocalDate date) {
      return date;
    }
    if (value instanceof Instant instant) {
      return instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }
    return LocalDate.parse(String.valueOf(value));
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
  }

  public boolean isProgram() {
    return "program".equals(type);
  }

  public boolean isExecution() {
    return !isProgram() && !"project".equals(type);
  }

  public long id() {
    return id;
  }

  public String type() {
    return type;
  }

  public long parentId() {
    return parentId;
  }

  public String path() {
    return path;
  }

  public int grade() {
    return grade;
  }

  public String name() {
    return name;
  }

  public String code() {
    return code;
  }

  public String model() {
    return model;
  }

  public String status() {
    return status;
  }

  public int priority() {
    return priority;
  }

  public LocalDate beginDate() {
    return beginDate;
  }

  public LocalDate endDate() {
    return endDate;
  }

  public LocalDate firstEndDate() {
    return firstEndDate;
  }

  public LocalDate realBeganDate() {
    return realBeganDate;
  }

  public LocalDate realEndDate() {
    return realEndDate;
  }

  public int days() {
    return days;
  }

  public BigDecimal budget() {
    return budget;
  }

  public String budgetUnit() {
    return budgetUnit;
  }

  public String description() {
    return description;
  }

  public String pm() {
    return pm;
  }

  public String po() {
    return po;
  }

  public String qd() {
    return qd;
  }

  public String rd() {
    return rd;
  }

  public int progress() {
    return progress;
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

  public boolean isMilestone() {
    return isMilestone;
  }

  public String acl() {
    return acl;
  }

  public List<String> whitelist() {
    return whitelist;
  }

  public int sort() {
    return sort;
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

  public int lockVersion() {
    return lockVersion;
  }
}
