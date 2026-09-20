package net.zentao.quality.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 测试报告聚合根（quality 卡 §3.6；纯 Java，A1）。 */
public class Report {

  private final long id;
  private final long executionId;
  private final long projectId;
  private final long productId;
  private String title;
  private List<Long> testRunIds;
  private LocalDate beginDate;
  private LocalDate endDate;
  private String owner;
  private String content;
  private String createdBy;
  private Instant createdAt;
  private String updatedBy;
  private Instant updatedAt;
  private int lockVersion;

  public Report(long id, long executionId, long projectId, long productId, String title, List<Long> testRunIds,
      LocalDate beginDate, LocalDate endDate, String owner, String content, String createdBy, Instant createdAt,
      String updatedBy, Instant updatedAt, int lockVersion) {
    this.id = id;
    this.executionId = executionId;
    this.projectId = projectId;
    this.productId = productId;
    this.title = title;
    this.testRunIds = testRunIds == null ? List.of() : List.copyOf(testRunIds);
    this.beginDate = beginDate;
    this.endDate = endDate;
    this.owner = owner;
    this.content = content;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
    this.lockVersion = lockVersion;
  }

  /** PATCH 白名单（quality 卡 §5）；executionId/projectId/productId 不可改；null 不改。 */
  public void update(String title, List<Long> testRunIds, LocalDate beginDate, LocalDate endDate, String owner,
      String content) {
    if (title != null) {
      this.title = title;
    }
    if (testRunIds != null) {
      this.testRunIds = List.copyOf(testRunIds);
    }
    if (beginDate != null) {
      this.beginDate = beginDate;
    }
    if (endDate != null) {
      this.endDate = endDate;
    }
    if (owner != null) {
      this.owner = owner;
    }
    if (content != null) {
      this.content = content;
    }
  }

  public void markUpdatedBy(String actor) {
    this.updatedBy = actor;
    this.updatedAt = Instant.now();
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

  public long productId() {
    return productId;
  }

  public String title() {
    return title;
  }

  public List<Long> testRunIds() {
    return testRunIds;
  }

  public LocalDate beginDate() {
    return beginDate;
  }

  public LocalDate endDate() {
    return endDate;
  }

  public String owner() {
    return owner;
  }

  public String content() {
    return content;
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
